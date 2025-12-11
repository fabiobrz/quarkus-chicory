package io.quarkiverse.chicory.runtime.wasm;

import java.util.function.Function;
import java.util.function.Supplier;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;

import io.quarkiverse.chicory.runtime.WasmQuarkusConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates configured {@link WasmQuarkusContext} instances at runtime.
 */
@Recorder
public class WasmQuarkusContextRecorder {
    /**
     * Creates a {@link WasmQuarkusContext} instance based on a configured Wasm module, and returns it as a
     * runtime value.
     *
     * @param key The configuration key of a given Wasm module
     * @param config The application configuration, storing all the configured Wasm modules.
     * @return A {@link RuntimeValue} referencing the configured {@link WasmQuarkusContext}.
     */
    public RuntimeValue<?> createContext(final String key, final WasmQuarkusConfig config, final boolean isNativePackageType) {
        WasmQuarkusConfig.ModuleConfig moduleConfig = config.modules().get(key);
        Log.info("A configured Wasm module " + key + " will be created");
        final boolean isDynamic = !(moduleConfig.wasmFile().isPresent() || moduleConfig.wasmResource().isPresent());
        // default to runtime compilation
        ExecutionMode actualExecutionMode = ExecutionMode.RuntimeCompiler;
        // dynamic vs. static payload configuration directly affects Wasm context execution mode, depending on
        // native image vs. JVM package type
        if (isDynamic) {
            // wasm payload is not configured, and is meant to be loaded dynamically, therefore only Interpreter
            // execution mode can be used in native package type execution (everything is compiled AoT)
            if (isNativePackageType) {
                Log.warn("No payload is configured for Wasm module " + key +
                        ", and native image is being built. Execution mode will fall back to " + ExecutionMode.Interpreter);
                actualExecutionMode = ExecutionMode.Interpreter;
            } else {
                // ... otherwise fallback to the runtime compiler (default), as the payload is loaded dynamically (and
                // the user cannot set it)
                Log.info("No payload is configured for Wasm module " + key + ", execution mode is " + actualExecutionMode);
            }
        } else {
            // Wasm payload is configured statically, and execution mode as well both for native vs. JVM package
            // type
            actualExecutionMode = moduleConfig.compiler().executionMode();
            Log.info("Payload is configured for Wasm module " + key + ", execution mode is " + actualExecutionMode);
        }
        Supplier<Function<Instance, Machine>> machineFactoryProvider = (LaunchMode.current() == LaunchMode.NORMAL
                || LaunchMode.current() == LaunchMode.RUN)
                        ? new ProdNativeModeMachineFactoryProvider(isDynamic, moduleConfig.name(), actualExecutionMode)
                        : new DevTestModeMachineFactoryProvider(actualExecutionMode);
        WasmQuarkusContext wasmQuarkusContext = new WasmQuarkusContext(moduleConfig.name(), actualExecutionMode,
                machineFactoryProvider);
        return new RuntimeValue<>(wasmQuarkusContext);
    }
}
