package io.quarkiverse.chicory.runtime;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dylibso.chicory.compiler.InterpreterFallback;

import io.quarkiverse.chicory.runtime.wasm.ExecutionMode;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Holds the configuration of a Quarkus Chicory application.
 */
@ConfigMapping(prefix = "quarkus.chicory")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ChicoryConfig {

    /**
     * A reference to the list of all configured Wasm modules.
     *
     * @return A list of configured Wasm modules.
     */
    Map<String, ModuleConfig> modules();

    /**
     * Inner interface representing a single Wasm module configuration.
     */
    interface ModuleConfig {
        /**
         * The Wasm module file to be used. If {@link #wasmResource()} is defined too, this has precedence over it.
         *
         * @return A {@link File} instance representing a configured Wasm module which is backed by a static file.
         */
        @WithName("wasm-file")
        Optional<Path> wasmFile();

        /**
         * The Wasm module to be used. If {@link #wasmFile()} is defined too, it has precedence over this.
         *
         * @return The name of a classpath resource representing a configured Wasm module which is backed by a static file.
         */
        @WithName("wasm-resource")
        Optional<String> wasmResource();

        /**
         * The base name to be used for the generated API class.
         *
         * @return The FQDN of the generated API class.
         */
        @WithName("name")
        String name();

        /**
         * A reference to the Chicory build time compiler configuration per each Wasm module
         *
         * @return The {@link CompilerConfig} instance representing the configuration that will be provided to the
         *         Chicory build time compiler
         */
        CompilerConfig compiler();

        /**
         * Inner interface representing the build time compiler configuration for a single Wasm module.
         */
        interface CompilerConfig {

            /**
             * The execution mode for a configured Wasm module
             *
             * @return {@link ExecutionMode} value that identifies the way Chicory will execute the Wasm module code
             */
            @WithName("execution-mode")
            @WithDefault("Interpreter")
            ExecutionMode executionMode();

            /**
             * The action to take if the compiler needs to use the interpreter because a function is too big
             */
            @WithName("interpreter-fallback")
            @WithDefault("FAIL")
            InterpreterFallback interpreterFallback();

            /**
             * The indexes of functions that should be interpreted, separated by commas
             */
            @WithName("interpreted-functions")
            Optional<List<Integer>> interpretedFunctions();
        }
    }

    /**
     * A reference to the configuration for the Chicory generator execution.
     *
     * @return The {@link GeneratorConfig} instance representing the configuration that will be provided to the
     *         Chicory code generation process.
     */
    GeneratorConfig generator();

    /**
     * Inner interface representing the Chicory generator configuration.
     */
    interface GeneratorConfig {
        /**
         * The target folder to generate classes
         */
        @WithName("target-class-folder")
        @WithDefault("target/generated-resources/chicory-compiler")
        Path targetClassFolder();

        /**
         * The target source folder to generate the Machine implementation
         */
        @WithDefault("target/generated-sources/chicory-compiler")
        Path targetSourceFolder();

        /**
         * The target wasm folder to generate the stripped meta wasm module
         */
        @WithName("target-wasm-folder")
        @WithDefault("target/generated-resources/chicory-compiler")
        Path targetWasmFolder();
    }
}
