package io.quarkiverse.chicory.runtime.wasm;

import java.util.Optional;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.chicory.runtime.WasmQuarkusConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates a configured instances of {@link WasmQuarkusContextRegistry} and
 * {@link WasmQuarkusContext} at runtime.
 */
@Recorder
public class WasmQuarkusContextRecorder {
    /**
     * Gets the {@link CDI} current instance of the {@link WasmQuarkusContextRegistry} application scoped bean and
     * populates it with configured Wasm modules.
     */
    public void initializeRegistry(WasmQuarkusConfig config) {
        // At runtime, this code looks up the *actual* CDI bean instance
        // and calls the initialization method we defined above.
        WasmQuarkusContextRegistry wasmQuarkusContextRegistry = CDI.current()
                .select(WasmQuarkusContextRegistry.class)
                .get();
        wasmQuarkusContextRegistry.initialize(config);
    }

    /**
     * Gets the {@link CDI} current instance of the {@link WasmQuarkusContextRegistry} and returns a runtime proxy to
     * a registered instance of {@link WasmQuarkusContext}.
     *
     * @param name The name of the configured Wasm module
     * @return A {@link RuntimeValue} referencing the configured {@link WasmQuarkusContext}.
     */
    public RuntimeValue<?> createContext(final String name) {
        WasmQuarkusContextRegistry wasmQuarkusContextRegistry = CDI.current()
                .select(WasmQuarkusContextRegistry.class)
                .get();
        Optional<WasmQuarkusContext> context = Optional.ofNullable(wasmQuarkusContextRegistry.get(name));
        if (context.isPresent()) {
            return new RuntimeValue<>(context.get());
        } else {
            throw new IllegalArgumentException("  No configuration found for Wasm module '" + name + "'. Using defaults.");
        }
    }
}
