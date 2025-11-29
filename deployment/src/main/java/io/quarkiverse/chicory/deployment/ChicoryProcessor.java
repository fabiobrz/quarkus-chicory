package io.quarkiverse.chicory.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;

import io.quarkiverse.chicory.runtime.*;
import io.quarkiverse.chicory.runtime.wasm.Catalog;
import io.quarkiverse.chicory.runtime.wasm.ContextRecorder;
import io.quarkiverse.chicory.runtime.wasm.DynamicCatalog;
import io.quarkiverse.chicory.runtime.wasm.StaticCatalog;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.logging.Log;
import io.quarkus.runtime.RuntimeValue;

/**
 * The Quarkus Chicory deployment processor implements the following features:
 * <ul>
 * <li>Provide an application scoped bean that exposes a catalog of configured Wasm modules</li>
 * <li>Replace the Chicory Maven plugin functionality to generate bytecode and Wasm meta files</li>
 * </ul>
 * <p>
 * A build step leverages the Chicory {@link Generator} to generate the bytecode and meta Wasm files for
 * each configured Wasm module, then two subsequent build steps process the output to provide Quarkus the related
 * classes and resources that will be built as part of the application, thus replacing the Chicory Maven plugin
 * functionality.
 * <br>
 * Finally, a build step generates a synthetic application scoped catalog that stores Wasm module context
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
     * Register the recorded object, i.e. {@link Catalog} as an {@link ApplicationScoped} CDI bean.
     *
     * @param recorder The {@link io.quarkus.runtime.annotations.Recorder} instance that provides a runtime
     *        reference to a configured {@link Catalog}.
     * @param config The application configuration that defines the configured Wasm modules.
     * @return A configured {@link Catalog} as an {@link ApplicationScoped} bean.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public SyntheticBeanBuildItem registerStaticWasmModuleContextCatalog(ContextRecorder recorder,
            ChicoryConfig config) {

        RuntimeValue<StaticCatalog> serviceRuntimeValue = recorder
                .createStaticWasmModuleContextCatalog(config);
        return SyntheticBeanBuildItem.configure(StaticCatalog.class)
                .scope(ApplicationScoped.class)
                .runtimeValue(serviceRuntimeValue)
                .setRuntimeInit()
                .done();
    }

    /**
     * Register the recorded object, i.e. {@link Catalog} as an {@link ApplicationScoped} CDI bean.
     *
     * @param recorder The {@link io.quarkus.runtime.annotations.Recorder} instance that provides a runtime
     *        reference to a configured {@link Catalog}.
     * @param config The application configuration that defines the configured Wasm modules.
     * @return A configured {@link Catalog} as an {@link ApplicationScoped} bean.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public SyntheticBeanBuildItem registerDynamicWasmModuleContextCatalog(ContextRecorder recorder,
            ChicoryConfig config) {

        RuntimeValue<DynamicCatalog> serviceRuntimeValue = recorder.createDynamicWasmModuleContextCatalog();
        return SyntheticBeanBuildItem.configure(DynamicCatalog.class)
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

        for (Map.Entry<String, ChicoryConfig.ModuleConfig> entry : chicoryConfig.modules().entrySet()) {
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
                        "Cannot create Wasm module context payload because neither a resource name nor a file path is defined.");
            }
            final Path targetClassFolder = chicoryConfig.generator().targetClassFolder();
            final Path targetWasmFolder = chicoryConfig.generator().targetWasmFolder();
            final Path targetSourceFolder = chicoryConfig.generator().targetSourceFolder();
            final Optional<List<Integer>> interpretedFunctionsConfig = moduleConfig.compiler().interpretedFunctions();

            Log.info("Generating bytecode for " + entry.getKey() + " from " + wasmFile);
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
            final String name = moduleConfig.name();
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
