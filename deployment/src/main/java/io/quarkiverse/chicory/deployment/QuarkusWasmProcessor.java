package io.quarkiverse.chicory.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;

import io.quarkiverse.chicory.deployment.items.GeneratedWasmCodeBuildItem;
import io.quarkiverse.chicory.deployment.items.WasmContextRegistrationCompleted;
import io.quarkiverse.chicory.runtime.WasmQuarkusConfig;
import io.quarkiverse.chicory.runtime.wasm.WasmQuarkusContext;
import io.quarkiverse.chicory.runtime.wasm.WasmQuarkusContextRecorder;
import io.quarkiverse.chicory.runtime.wasm.WasmQuarkusContextRegistry;
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
 * An additional build step leverages the Chicory {@link Generator} to generate the bytecode, the meta Wasm files and
 * the raw Java sources for each configured Wasm module, then two subsequent build steps process the output to provide
 * Quarkus the related classes and resources that will be built as part of the application, thus replacing the Chicory
 * Maven plugin functionality.
 * <br>
 * Finally, a build step is responsible for adding all the statically configured Wasm files to the collectin of watched
 * resources.
 * </p>
 */
class QuarkusWasmProcessor {

    private static final String FEATURE = "chicory";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Loads configured Wasm modules to initialize the application scoped {@link WasmQuarkusContextRegistry} instance.
     *
     * @param recorder The {@link WasmQuarkusContextRecorder} instance that provides the logic to populate the runtime
     *        instance of {@link WasmQuarkusContextRegistry}
     * @param config The application configuration, storing all the configured modules.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Produce(WasmContextRegistrationCompleted.class)
    void loadContextRegistry(WasmQuarkusContextRecorder recorder, WasmQuarkusConfig config) {
        // Populate the registry with configured Wasm modules
        recorder.initializeRegistry(config);
    }

    /**
     * Creates a collection of {@link WasmQuarkusContext} application scoped named
     * beans, for each statically configured Wasm module.
     *
     * @param syntheticBeans The {@link BuildProducer} instance that creates the synthetic beans
     * @param recorder The {@link WasmQuarkusContextRecorder} instance that provides the logic to produce the runtime
     *        instances of the required beans
     * @param config The application configuration, storing all the configured modules.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(WasmContextRegistrationCompleted.class)
    void registerWasmContextBeans(BuildProducer<SyntheticBeanBuildItem> syntheticBeans, WasmQuarkusContextRecorder recorder,
            WasmQuarkusConfig config) {
        // Produce synthetic WasmQuarkusContext beans for related Wasm modules
        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> configEntry : config.modules().entrySet()) {
            syntheticBeans.produce(
                    SyntheticBeanBuildItem.configure(WasmQuarkusContext.class)
                            .scope(ApplicationScoped.class)
                            .runtimeValue(recorder.createContext(configEntry.getKey()))
                            .setRuntimeInit()
                            .named(configEntry.getKey())
                            .done());
        }
    }

    /**
     * Use the Chicory build time compiler {@link Generator} to generate bytecode from configured {@code Wasm} modules.
     *
     * @param config The application configuration, storing all the configured modules.
     * @return A collection of {@link GeneratedWasmCodeBuildItem} items, each of them storing the name of the
     *         generated Wasm module, a list of paths referencing the generated {@code .class} files,
     *         a reference to the generated {@code .meta} Wasm file, and a reference to the generated {@code .java}
     *         source file.
     * @throws IOException If the generation fails.
     */
    @BuildStep
    @Consume(WasmContextRegistrationCompleted.class)
    public List<GeneratedWasmCodeBuildItem> generate(WasmQuarkusConfig config) throws IOException {

        final List<GeneratedWasmCodeBuildItem> result = new ArrayList<>();

        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> entry : config.modules().entrySet()) {
            final WasmQuarkusConfig.ModuleConfig moduleConfig = entry.getValue();
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
            result.add(new GeneratedWasmCodeBuildItem(name, generatedClasses, generatedMetaWasm, generatedJava));
        }
        return result;
    }

    /**
     * A build step that consumes the build items generated by {@link #generate(WasmQuarkusConfig)} to collect a list of
     * {@link GeneratedClassBuildItem} referencing the generated {@code .class} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .class} files
     *
     * @return A collection of {@link GeneratedClassBuildItem} items, referencing the generated {@code .class} files.
     */
    @BuildStep
    public List<GeneratedClassBuildItem> collectGeneratedClasses(List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems)
            throws IOException {

        final List<GeneratedClassBuildItem> generatedClasses = new ArrayList<>();

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
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
     * A build step that consumes the build items generated by {@link #generate(WasmQuarkusConfig)} to collect a list of
     * {@link GeneratedResourceBuildItem} referencing the generated {@code .meta} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .meta} files
     *
     * @return A collection of {@link GeneratedResourceBuildItem} items, referencing the generated {@code .meta} files.
     */
    @BuildStep
    public List<GeneratedResourceBuildItem> collectGeneratedMetaWasm(
            List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems)
            throws IOException {

        final List<GeneratedResourceBuildItem> generatedMetaWasm = new ArrayList<>();

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
            final Path metaWasm = buildItem.getMetaWasm();
            Log.info("Collecting the generated .meta file: " + metaWasm + " for " + buildItem.getName());
            generatedMetaWasm.add(new GeneratedResourceBuildItem(metaWasm.getFileName().toString(),
                    Files.readAllBytes(metaWasm)));
        }
        return generatedMetaWasm;
    }

    /**
     * A build step that consumes the build items generated by {@link #generate(WasmQuarkusConfig)} to collect a list of
     * {@link GeneratedResourceBuildItem} referencing the generated {@code .java} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .java} files
     *
     * @return A collection of {@link GeneratedResourceBuildItem} items, referencing the generated {@code .java} files.
     */
    @BuildStep
    public List<GeneratedResourceBuildItem> collectGeneratedSources(
            List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems)
            throws IOException {

        final List<GeneratedResourceBuildItem> generatedJavaSources = new ArrayList<>();

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
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
     * @param wasmQuarkusConfig The application configuration, storing all the configured modules.
     * @return A list of {@link HotDeploymentWatchedFileBuildItem}, representing the collection f
     *         Wasm module files that will be watched in dev mode.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    List<HotDeploymentWatchedFileBuildItem> addWatchedResources(WasmQuarkusConfig wasmQuarkusConfig) {

        List<HotDeploymentWatchedFileBuildItem> result = new ArrayList<>();

        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> entry : wasmQuarkusConfig.modules().entrySet()) {
            final WasmQuarkusConfig.ModuleConfig moduleConfig = entry.getValue();
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
