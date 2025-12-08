package io.quarkiverse.chicory.runtime.wasm;

import java.util.Optional;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates a configured instances of {@link Wasms} at runtime.
 */
@Recorder
public class WasmRecorder {
    /**
     * Gets the {@link CDI} current instance of the {@link Wasms} application scoped bean and populates it with
     * configured Wasm modules.
     */
    public void initializeWasms(ChicoryConfig config) {
        // At runtime, this code looks up the *actual* CDI bean instance
        // and calls the initialization method we defined above.
        Wasms wasms = CDI.current()
                .select(Wasms.class)
                .get();
        wasms.initialize(config);
    }

    /**
     * Returns a runtime proxy to an instance of {@link com.dylibso.chicory.compiler.internal.MachineFactory} based on
     * the application configuration and returns a runtime proxy.
     *
     * @param name The name of the configured Wasm module
     * @return A {@link RuntimeValue} referencing the configured {@link com.dylibso.chicory.compiler.internal.MachineFactory}.
     */
    public RuntimeValue<?> createMachineFactory(final String name) {
        Wasms wasms = CDI.current()
                .select(Wasms.class)
                .get();
        Optional<Wasm> wasm = Optional.ofNullable(wasms.get(name));
        if (wasm.isPresent()) {
            final Wasm actualWasm = wasm.get();
            return new RuntimeValue<>(actualWasm.getMachineFactory());
        } else {
            throw new IllegalArgumentException("  No configuration found for Wasm module '" + name + "'. Using defaults.");
        }
    }
}
