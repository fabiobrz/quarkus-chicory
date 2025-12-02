package io.quarkiverse.chicory.runtime.wasm;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates a configured instances of {@link Wasms} at runtime.
 */
@Recorder
public class WasmRecorder {

    final static List<Wasm> REGISTERED_WASMS = new ArrayList<>();

    /**
     * Creates an instance of {@link Wasms} based on the application configuration and
     * returns a runtime proxy.
     *
     * @param name The name of the configured Wasm module
     * @param config The application configuration
     * @return Returns a {@link RuntimeValue} referencing the configured {@link Wasms}.
     */
    public RuntimeValue<Wasm> createWasm(final String name, final ChicoryConfig config) {
        Wasm wasm = Wasms.createStatic(name, config.modules().get(name));
        REGISTERED_WASMS.add(wasm);
        return new RuntimeValue<>(wasm);
    }

    /**
     * Creates an instance of {@link Wasms} based on the application configuration and
     * returns a runtime proxy.
     *
     * @return Returns a {@link RuntimeValue} referencing the configured {@link Wasms}.
     */
    public RuntimeValue<Wasms> createWasms() {
        return new RuntimeValue<>(new Wasms(REGISTERED_WASMS));
    }
}
