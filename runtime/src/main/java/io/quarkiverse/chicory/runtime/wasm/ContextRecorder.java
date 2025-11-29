package io.quarkiverse.chicory.runtime.wasm;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates a configured instance of {@link Catalog} and provides a
 * runtime reference to it.
 */
@Recorder
public class ContextRecorder {

    /**
     * Creates an instance of {@link Catalog} based on the application configuration and
     * returns a runtime proxy.
     *
     * @param moduleConfig The application configuration
     * @return Returns a {@link RuntimeValue} referencing the configured {@link Catalog}.
     */
    public RuntimeValue<StaticCatalog> createStaticWasmModuleContextCatalog(
            ChicoryConfig moduleConfig) {
        StaticCatalog service = new StaticCatalog(moduleConfig);
        return new RuntimeValue<>(service);
    }

    /**
     * Creates an instance of {@link Catalog} based on the application configuration and
     * returns a runtime proxy.
     *
     * @return Returns a {@link RuntimeValue} referencing the configured {@link Catalog}.
     */
    public RuntimeValue<DynamicCatalog> createDynamicWasmModuleContextCatalog() {
        return new RuntimeValue<>(new DynamicCatalog());
    }
}
