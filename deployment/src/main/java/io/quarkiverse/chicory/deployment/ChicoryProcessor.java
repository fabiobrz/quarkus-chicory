package io.quarkiverse.chicory.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;
import com.dylibso.chicory.runtime.Instance;

import io.quarkiverse.chicory.deployment.build.GeneratedWasmClassesBuildItem;
import io.quarkiverse.chicory.deployment.build.WasmRegistrationCompleted;
import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkiverse.chicory.runtime.wasm.Wasm;
import io.quarkiverse.chicory.runtime.wasm.WasmRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.*;
import io.quarkus.logging.Log;

/**
 * The Quarkus Chicory deployment processor provides the following features:
 * <ul>
 * <li>Produce an injectable bean that exposes all the loaded Wasm modules</li>
 * <li>Produce a collection of injectable named beans, each representing a configured Wasm module</li>
 * <li>Produce the logic to adjust the configuration of Chicory {@link com.dylibso.chicory.runtime.Instance.Builder} at
 * runtime, based on the application configuration and execution environment.</li>
 * <li>Replace the Chicory Maven plugin functionality to generate bytecode, Wasm meta files and raw Java sources</li>
 * <li>Watch statically configured Wasm module files to trigger a rebuild in <i>dev mode</i></li>
 * </ul>
 * <p>
 * The first build step is responsible for loading all the configured Wasm modules into an application scoped bean
 * which also tracks those added dynamically at runtime.
 * <br>
 * Then, a separate build step creates a collection of application scoped named beans, each representing a statically
 * configured Wasm module, which can be injected into client applications.
 * <br>
 * Another build step produces the components that will manipulate Chicory APIs at runtime via bytecode transformation.
 * <br>
 * An additional build step leverages the Chicory {@link Generator} to generate the bytecode, the meta Wasm files and
 * the raw Java sources for each configured Wasm module, then two subsequent build steps process the output to provide
 * Quarkus the related classes and resources that will be built as part of the application, thus replacing the Chicory
 * Maven plugin functionality.
 * <br>
 * Finally, a build step is responsible for adding all the statically configured Wasm files to the collectin of watched
 * resources.
 * </p>
 */
class ChicoryProcessor {

    private static final String FEATURE = "chicory";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    private static final DotName INSTANCE_TYPE = DotName.createSimple("com.dylibso.chicory.runtime.Instance");
    private static final DotName CONFIG_ANNOTATION = DotName
            .createSimple("io.quarkiverse.chicory.runtime.ChicoryInstance");

    /**
     * Loads configured Wasm modules to initialize the application scoped
     * {@link io.quarkiverse.chicory.runtime.wasm.Wasms} instances.
     *
     * @param recorder The {@link WasmRecorder} instance that provides the logic to produce the runtime instances of the
     *        required beans
     * @param config The application configuration, storing all the configured modules.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Produce(WasmRegistrationCompleted.class)
    void loadWasms(WasmRecorder recorder, ChicoryConfig config) {
        // Populate the registry with configured Wasm modules
        recorder.initializeWasms(config);
    }

    /**
     * Produce component logic to address bytecode transformation and manipulate the
     * {@link com.dylibso.chicory.runtime.Instance.Builder} configuration, based on configuration and runtime
     * environment.
     *
     * @param combinedIndex
     * @param transformers
     */
    @BuildStep
    @Consume(WasmRegistrationCompleted.class)
    public void transformChicoryInstanceBuilder(CombinedIndexBuildItem combinedIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {
        // Get the index to look up for annotated com.dylibso.chicory.runtime.Instance instances
        IndexView index = combinedIndex.getIndex();
        Collection<AnnotationInstance> instances = index.getAnnotations(CONFIG_ANNOTATION);
        for (AnnotationInstance annotation : instances) {
            AnnotationTarget target = annotation.target();

            // Enforce our transformation #1 hard requirement: must be a field
            if (target.kind() != AnnotationTarget.Kind.FIELD) {
                Log.warn(
                        "Error: @" + CONFIG_ANNOTATION.local() + " can only be applied to fields. Found on: " + target);
                continue;
            }
            FieldInfo field = target.asField();

            // Enforce our transformation #2 requirement: must be an com.dylibso.chicory.runtime.Instance field
            if (target.kind() != AnnotationTarget.Kind.FIELD || !field.type().name().equals(INSTANCE_TYPE)) {
                Log.warn("Error: Field " + field.name() + " is annotated with @" + CONFIG_ANNOTATION.local()
                        + " but is not of type " + Instance.class.getName());
                continue;
            }
            final String className = field.declaringClass().name().toString();

            // Extract the module configuration key
            final String wasmId = annotation.value().asString();

            // Pass only the Wasm module key to the visitor function
            BiFunction<String, ClassVisitor, ClassVisitor> visitorFunction = (cn, visitor) -> new ChicoryFieldVisitor(visitor,
                    wasmId);
            Log.info("Transforming Chicory Instance \"" + field.name() + "\" in \"" + className + "\" based on [" +
                    wasmId + "] Wasm module configuration.");
            transformers.produce(
                    new BytecodeTransformerBuildItem.Builder()
                            .setClassToTransform(className)
                            .setVisitorFunction(visitorFunction)
                            .build());
        }
    }

    // Inner classes for ASM Bytecode Manipulation
    static class ChicoryFieldVisitor extends ClassVisitor {
        private final String wasmId;

        public ChicoryFieldVisitor(ClassVisitor cv, String wasmId) {
            super(Opcodes.ASM9, cv);
            this.wasmId = wasmId;
        }

        // ... (visitMethod passes wasmId to MethodCallAdapter) ...
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new MethodCallAdapter(mv, wasmId);
            }
            return null;
        }
    }

    static class MethodCallAdapter extends MethodVisitor {
        private final String wasmId;

        public MethodCallAdapter(MethodVisitor mv, String wasmId) {
            super(Opcodes.ASM9, mv);
            this.wasmId = wasmId;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // look for the Instance.builder().build() call
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && owner.equals("com/dylibso/chicory/runtime/Instance$Builder")
                    && name.equals("build")) {

                // Inject *only* the wasmId onto the stack
                mv.visitLdcInsn(wasmId);

                // Update the descriptor to accept one String parameter
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "io/quarkiverse/chicory/runtime/ChicoryRuntimeTransformer",
                        "transformBuilder",
                        "(Lcom/dylibso/chicory/runtime/Instance$Builder;Ljava/lang/String;)Lcom/dylibso/chicory/runtime/Instance;",
                        false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    /**
     * Creates a collection of application scoped named beans, each representing a statically
     * configured Wasm module, plus an application scoped bean that stores all loaded Wasm modules
     * data, both configured at build time, and dynamically added at runtime.
     *
     * @param syntheticBeans The {@link BuildProducer} instance that creates the synthetic beans
     * @param recorder The {@link WasmRecorder} instance that provides the logic to produce the runtime instances of the
     *        required beans
     * @param config The application configuration, storing all the configured modules.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(WasmRegistrationCompleted.class)
    void registerWasmBeans(BuildProducer<SyntheticBeanBuildItem> syntheticBeans, WasmRecorder recorder,
            ChicoryConfig config) {

        // Produce synthetic Wasm module named beans
        for (Map.Entry<String, ChicoryConfig.ModuleConfig> configEntry : config.modules().entrySet()) {
            syntheticBeans.produce(
                    SyntheticBeanBuildItem.configure(Wasm.class)
                            .scope(ApplicationScoped.class)
                            .runtimeValue(recorder.createWasm(configEntry.getKey()))
                            .setRuntimeInit()
                            .named(configEntry.getKey())
                            .done());
        }
    }

    /**
     * Use the Chicory build time compiler {@link Generator} to generate bytecode from configured {@code Wasm} modules.
     *
     * @param config The application configuration, storing all the configured modules.
     * @return A collection of {@link GeneratedWasmClassesBuildItem} items, each of them storing the name of the
     *         generated Wasm module, a list of paths referencing the generated {@code .class} files,
     *         a reference to the generated {@code .meta} Wasm file, and a reference to the generated {@code .java}
     *         source file.
     * @throws IOException If the generation fails.
     */
    @BuildStep
    public List<GeneratedWasmClassesBuildItem> generate(ChicoryConfig config) throws IOException {

        final List<GeneratedWasmClassesBuildItem> result = new ArrayList<>();

        for (Map.Entry<String, ChicoryConfig.ModuleConfig> entry : config.modules().entrySet()) {
            final ChicoryConfig.ModuleConfig moduleConfig = entry.getValue();
            final String name = moduleConfig.name();
            final Path wasmFile;
            if (moduleConfig.wasmFile().isPresent()) {
                wasmFile = moduleConfig.wasmFile().get();
            } else if (moduleConfig.wasmResource().isPresent()) {
                String wasmResource = moduleConfig.wasmResource().get();
                wasmFile = Files.createTempFile("chicory", "wasm");
                try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(wasmResource)) {
                    if (is != null) {
                        Files.copy(is, wasmFile, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        throw new IllegalStateException("Cannot access Wasm module resource: " + wasmResource);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(
                            String.format("Cannot create Wasm module resource (%s) temporary file", wasmResource), e);
                }
            } else {
                throw new IllegalStateException(
                        "Cannot create Wasm module payload because neither a resource name nor a file path is defined.");
            }
            final Path targetClassFolder = config.generator().targetClassFolder();
            final Path targetWasmFolder = config.generator().targetWasmFolder();
            final Path targetSourceFolder = config.generator().targetSourceFolder();
            final Optional<List<Integer>> interpretedFunctionsConfig = moduleConfig.compiler().interpretedFunctions();

            Log.info("Generating bytecode for " + entry.getKey() + " from " + wasmFile);
            final Config generatorConfig = Config.builder()
                    .withWasmFile(wasmFile)
                    .withName(name)
                    .withTargetClassFolder(targetClassFolder)
                    .withTargetWasmFolder(targetWasmFolder)
                    .withTargetSourceFolder(targetSourceFolder)
                    .withInterpreterFallback(moduleConfig.compiler().interpreterFallback())
                    .withInterpretedFunctions(
                            interpretedFunctionsConfig.isPresent() ? new HashSet<>(interpretedFunctionsConfig.get()) : Set.of())
                    .build();
            final Generator generator = new Generator(generatorConfig);
            final Set<Integer> finalInterpretedFunctions = generator.generateResources();
            generator.generateMetaWasm(finalInterpretedFunctions);
            generator.generateSources();

            // Track the generated *.class and .meta Wasm files
            final List<Path> generatedClasses = new ArrayList<>();
            Path generatedMetaWasm = null;
            Path generatedJava = null;
            // N .class files
            try (Stream<Path> pathStream = Files.walk(targetClassFolder)) {
                ArrayList<Path> files = pathStream
                        .filter(p -> p.toString().endsWith(".class"))
                        .collect(Collectors.toCollection(ArrayList::new));
                for (Path file : files) {
                    Log.debug("Tracking the generated .class file: " + file);
                    generatedClasses.add(file);
                }
            }
            // 1 .meta Wasm file
            try (Stream<Path> pathStream = Files.walk(targetWasmFolder)) {
                generatedMetaWasm = pathStream
                        .filter(p -> p.toString().endsWith(".meta"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(".meta Wasm file not found"));
                Log.debug("Tracking the generated .meta file: " + generatedMetaWasm);
            }
            // 1 .java source file
            try (Stream<Path> pathStream = Files.walk(targetSourceFolder)) {
                generatedJava = pathStream
                        .filter(p -> p.toString().endsWith(".java"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(".java Wasm file not found"));
                Log.debug("Tracking the generated .java file: " + generatedJava);
            }
            result.add(new GeneratedWasmClassesBuildItem(name, generatedClasses, generatedMetaWasm, generatedJava));
        }
        return result;
    }

    /**
     * A build step that consumes the build items generated by {@link #generate(ChicoryConfig)} to collect a list of
     * {@link GeneratedClassBuildItem} referencing the generated {@code .class} files.
     *
     * @param generatedWasmClassesBuildItems The list of {@link GeneratedWasmClassesBuildItem} items that will be used
     *        to collect all the generated {@code .class} files
     *
     * @return A collection of {@link GeneratedClassBuildItem} items, referencing the generated {@code .class} files.
     */
    @BuildStep
    public List<GeneratedClassBuildItem> collectClasses(List<GeneratedWasmClassesBuildItem> generatedWasmClassesBuildItems)
            throws IOException {

        final List<GeneratedClassBuildItem> generatedClasses = new ArrayList<>();

        for (GeneratedWasmClassesBuildItem buildItem : generatedWasmClassesBuildItems) {
            final String name = buildItem.getName();
            Log.info("Collecting generated .class files for " + name);

            for (Path file : buildItem.getClasses()) {
                final String className = name + file.getFileName().toString().replace(".java", "");
                Log.debug("Adding .class file: " + className);
                generatedClasses.add(new GeneratedClassBuildItem(true, className, Files.readAllBytes(file)));
            }
        }
        return generatedClasses;
    }

    /**
     * A build step that consumes the build items generated by {@link #generate(ChicoryConfig)} to collect a list of
     * {@link GeneratedResourceBuildItem} referencing the generated {@code .meta} files.
     *
     * @param generatedWasmClassesBuildItems The list of {@link GeneratedWasmClassesBuildItem} items that will be used
     *        to collect all the generated {@code .meta} files
     *
     * @return A collection of {@link GeneratedResourceBuildItem} items, referencing the generated {@code .meta} files.
     */
    @BuildStep
    public List<GeneratedResourceBuildItem> collectMetaWasm(List<GeneratedWasmClassesBuildItem> generatedWasmClassesBuildItems)
            throws IOException {

        final List<GeneratedResourceBuildItem> generatedMetaWasm = new ArrayList<>();

        for (GeneratedWasmClassesBuildItem buildItem : generatedWasmClassesBuildItems) {
            final Path metaWasm = buildItem.getMetaWasm();
            Log.info("Collecting the generated .meta file: " + metaWasm + " for " + buildItem.getName());
            generatedMetaWasm.add(new GeneratedResourceBuildItem(metaWasm.getFileName().toString(),
                    Files.readAllBytes(metaWasm)));
        }
        return generatedMetaWasm;
    }

    /**
     * A build step that consumes the build items generated by {@link #generate(ChicoryConfig)} to collect a list of
     * {@link GeneratedResourceBuildItem} referencing the generated {@code .java} files.
     *
     * @param generatedWasmClassesBuildItems The list of {@link GeneratedWasmClassesBuildItem} items that will be used
     *        to collect all the generated {@code .java} files
     *
     * @return A collection of {@link GeneratedResourceBuildItem} items, referencing the generated {@code .java} files.
     */
    @BuildStep
    public List<GeneratedResourceBuildItem> collectSources(List<GeneratedWasmClassesBuildItem> generatedWasmClassesBuildItems)
            throws IOException {

        final List<GeneratedResourceBuildItem> generatedJavaSources = new ArrayList<>();

        for (GeneratedWasmClassesBuildItem buildItem : generatedWasmClassesBuildItems) {
            final Path javaSources = buildItem.getJavaSources();
            Log.info("Collecting the generated .java file: " + javaSources + " for " + buildItem.getName());
            generatedJavaSources.add(new GeneratedResourceBuildItem(javaSources.getFileName().toString(),
                    Files.readAllBytes(javaSources)));
        }
        return generatedJavaSources;
    }

    /**
     * Only in dev mode, the configured Wasm modules that define a filesystem path are added to the watched resources.
     *
     * @param chicoryConfig The application configuration, storing all the configured modules.
     * @return A list of {@link HotDeploymentWatchedFileBuildItem}, representing the collection f
     *         Wasm module files that will be watched in dev mode.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    List<HotDeploymentWatchedFileBuildItem> addWatchedResources(ChicoryConfig chicoryConfig) {

        List<HotDeploymentWatchedFileBuildItem> result = new ArrayList<>();

        for (Map.Entry<String, ChicoryConfig.ModuleConfig> entry : chicoryConfig.modules().entrySet()) {
            final ChicoryConfig.ModuleConfig moduleConfig = entry.getValue();
            final Path wasmFile;
            if (moduleConfig.wasmFile().isPresent()) {
                wasmFile = moduleConfig.wasmFile().get();
                Log.info("Adding " + wasmFile + " to the collection of watched resources (dev mode)");
                new HotDeploymentWatchedFileBuildItem(wasmFile.toAbsolutePath().toString());
            }
        }
        return result;
    }
}
