package io.quarkiverse.chicory.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkiverse.chicory.runtime.WasmModuleContextRecorder;
import io.quarkiverse.chicory.runtime.WasmModuleContextRegistry;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.logging.Log;
import io.quarkus.runtime.RuntimeValue;

/**
 * The Quarkus Chicory deployment processor implements the following features:
 * <ul>
 * <li>Provide an application scoped bean that exposes a registry of configured Wasm modules</li>
 * <li>Replace the Chicory Maven plugin functionality to generate bytecode and Wasm meta files</li>
 * </ul>
 * <p>
 * A build step leverages the Chicory {@link Generator} to generate the bytecode and meta Wasm files for
 * each configured Wasm module, then two subsequent build steps process the output to provide Quarkus the related
 * classes and resources that will be built as part of the application, thus replacing the Chicory Maven plugin
 * functionality.
 * <br>
 * Finally, a build step generates a synthetic application scoped registry that stores Wasm module context
 * data, both configured at build time, and dynamically added at runtime.
 * </p>
 */
class ChicoryProcessor {

    private static final String FEATURE = "chicory";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Register the recorded object, i.e. {@link WasmModuleContextRegistry} as an {@link ApplicationScoped} CDI bean.
     *
     * @param recorder The {@link io.quarkus.runtime.annotations.Recorder} instance that provides a runtime
     *        reference to a configured {@link WasmModuleContextRegistry}.
     * @param config The application configuration that defines the configured Wasm modules.
     * @return A configured {@link WasmModuleContextRegistry} as an {@link ApplicationScoped} bean.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public SyntheticBeanBuildItem configureWasmModuleContextRegistry(WasmModuleContextRecorder recorder,
            ChicoryConfig config) {

        RuntimeValue<WasmModuleContextRegistry> serviceRuntimeValue = recorder.createWasmModuleContextRegistry(config);
        return SyntheticBeanBuildItem.configure(WasmModuleContextRegistry.class)
                .scope(ApplicationScoped.class)
                .runtimeValue(serviceRuntimeValue)
                .setRuntimeInit()
                .done();
    }

    /**
     * Use the Chicory build time compiler {@link Generator} to generate bytecode from configured {@code Wasm} modules.
     *
     * @param chicoryConfig The application configuration, storing all the configured modules.
     * @return A collection of {@link GeneratedWasmClassesBuildItem} items, each of them storing the name of the
     *         generated Wasm module, a list of paths referencing the generated {@code .class} files,
     *         a reference to the generated {@code .meta} Wasm file, and a reference to the generated {@code .java}
     *         source file.
     * @throws IOException If the generation fails.
     */
    @BuildStep
    public List<GeneratedWasmClassesBuildItem> generate(ChicoryConfig chicoryConfig) throws IOException {

        final List<GeneratedWasmClassesBuildItem> result = new ArrayList<>();

        for (ChicoryConfig.ModuleConfig moduleConfig : chicoryConfig.modules()) {
            final String name = moduleConfig.name();
            final Path wasmFile = moduleConfig.wasmFile();
            final Path targetClassFolder = chicoryConfig.generator().targetClassFolder();
            final Path targetWasmFolder = chicoryConfig.generator().targetWasmFolder();
            final Path targetSourceFolder = chicoryConfig.generator().targetSourceFolder();
            final Optional<List<Integer>> interpretedFunctionsConfig = moduleConfig.compiler().interpretedFunctions();

            Log.info("Generating bytecode for " + name + " from " + wasmFile);
            final Config config = Config.builder()
                    .withWasmFile(wasmFile)
                    .withName(name)
                    .withTargetClassFolder(targetClassFolder)
                    .withTargetWasmFolder(targetWasmFolder)
                    .withTargetSourceFolder(targetSourceFolder)
                    .withInterpreterFallback(moduleConfig.compiler().interpreterFallback())
                    .withInterpretedFunctions(
                            interpretedFunctionsConfig.isPresent() ? new HashSet<>(interpretedFunctionsConfig.get()) : Set.of())
                    .build();
            final Generator generator = new Generator(config);
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
            final String name = buildItem.getName();
            final Path metaWasm = buildItem.getMetaWasm();
            Log.info("Collecting the generated .meta file: " + metaWasm + " for " + name);
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
            final String name = buildItem.getName();
            final Path javaSources = buildItem.getJavaSources();
            Log.info("Collecting the generated .java file: " + javaSources + " for " + name);
            generatedJavaSources.add(new GeneratedResourceBuildItem(javaSources.getFileName().toString(),
                    Files.readAllBytes(javaSources)));
        }
        return generatedJavaSources;
    }
}
