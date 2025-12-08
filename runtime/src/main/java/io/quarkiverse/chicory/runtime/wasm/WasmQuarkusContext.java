package io.quarkiverse.chicory.runtime.wasm;

import java.util.function.Function;
import java.util.function.Supplier;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;

/**
 * A representation of a Wasm module that is managed by Quarkus Chicory.
 */
public class WasmQuarkusContext {
    private final String name;
    private final ExecutionMode executionMode;
    private final Supplier<Function<Instance, Machine>> machineFactoryProvider;

    // Client code can only create via the Builder
    WasmQuarkusContext(final String name, final ExecutionMode executionMode,
            Supplier<Function<Instance, Machine>> machineFactoryProvider) {
        this.name = name;
        this.executionMode = executionMode;
        this.machineFactoryProvider = machineFactoryProvider;
    }

    public String getName() {
        return name;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public Function<Instance, Machine> getMachineFactory() {
        return machineFactoryProvider.get();
    }
}
