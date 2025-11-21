package io.quarkiverse.chicory.runtime;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates a configured instance of {@link WasmModuleContextRegistry} and provides a
 * runtime reference to it.
 */
@Recorder
public class WasmModuleContextRecorder {

    /**
     * Creates an instance of {@link WasmModuleContextRegistry} based on the application configuration and
     * returns a runtime proxy.
     *
     * @param moduleConfig The application configuration
     * @return Returns a {@link RuntimeValue} referencing the configured {@link WasmModuleContextRegistry}.
     */
    public RuntimeValue<WasmModuleContextRegistry> createWasmModuleContextRegistry(ChicoryConfig moduleConfig) {
        WasmModuleContextRegistry service = new WasmModuleContextRegistry(moduleConfig);
        return new RuntimeValue<>(service);
    }
}
