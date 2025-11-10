package io.quarkiverse.chicory.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Abstracts the configured Wasm modules.
 */
@ConfigMapping(prefix = "quarkus.chicory.wasm-modules")
public interface WasmModulesConfig {

    /**
     * Holds the list of all configured Wasm modules.
     *
     * @return A list of configured Wasm modules.
     */
    List<ModuleConfig> modules();

    /**
     * Inner interface representing a single Wasm module configuration.
     */
    interface ModuleConfig {
        /**
         * A required identifier for the configured Wasm module.
         *
         * @return A String that uniquely identifies a configured Wasm module.
         */
        @WithName("id")
        String id();

        /**
         * A path for a configured Wasm module represented by a static file.
         *
         * @return A {@link Path} instance representing a configured Wasm module which is backed by a static file.
         */
        @WithName("static-file-path")
        Path staticFilePath();

        /**
         * An optional fully qualified class name, that will be used as a factory to compile the configured Wasm module.
         *
         * @return The fully qualified name of a class that will be used as a factory to compile the configured Wasm
         *         module API.
         */
        @WithName("factory-class-name")
        Optional<String> factoryClassName();

        /**
         * An optional entry point, i.e. a factory method that will be used to compile the configured Wasm module.
         *
         * @return The name of a factory method that will be used to compile the configured Wasm module API.
         */
        @WithName("factory-method-name")
        Optional<String> factoryMethodName();
    }
}
