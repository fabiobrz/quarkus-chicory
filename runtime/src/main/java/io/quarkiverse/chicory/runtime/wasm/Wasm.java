package io.quarkiverse.chicory.runtime.wasm;

import java.util.function.Function;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.WasmModule;

/**
 * A representation of a Wasm module that is managed by Quarkus Chicory.
 */
public class Wasm {
    private final String name;
    private final WasmModule module;
    private final ExecutionMode executionMode;
    private final Function<Wasm, Instance> instanceProvider;

    // Client code can only create via the Builder
    Wasm(final String name, final WasmModule module, final ExecutionMode executionMode) {
        this(name, module, executionMode, new DynamicInstanceProvider());
    }

    // Client code can only create via the Builder
    Wasm(final String name, final WasmModule module, final ExecutionMode executionMode,
            final Function<Wasm, Instance> instanceProvider) {
        this.name = name;
        this.module = module;
        this.executionMode = executionMode;
        this.instanceProvider = instanceProvider;
    }

    public String getName() {
        return name;
    }

    public WasmModule getModule() {
        return module;
    }

    public ExecutionMode getMode() {
        return executionMode;
    }

    public Instance chicoryInstance() {
        return instanceProvider.apply(this);
    }

    public static Wasm.Builder builder(final String name, final WasmModule wasmModule) {
        return new Builder(name, wasmModule);
    }

    public static class Builder {
        protected final String name;
        protected final WasmModule wasmModule;
        private ExecutionMode executionMode;
        private Function<Wasm, Instance> instanceProvider;

        protected Builder(final String name, final WasmModule wasmModule) {
            this.name = name;
            this.wasmModule = wasmModule;
            this.executionMode = ExecutionMode.Interpreter;
        }

        public Builder withMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder withInstanceProvider(Function<Wasm, Instance> instanceProvider) {
            this.instanceProvider = instanceProvider;
            return this;
        }

        public Wasm build() {
            if (instanceProvider != null) {
                return new Wasm(name, wasmModule, executionMode, instanceProvider);
            }
            return new Wasm(name, wasmModule, executionMode);
        }
    }
}
