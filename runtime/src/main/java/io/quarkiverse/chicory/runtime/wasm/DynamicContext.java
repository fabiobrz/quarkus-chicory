package io.quarkiverse.chicory.runtime.wasm;

import java.util.Objects;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkus.logging.Log;

/**
 * A managed <i>dynamic</i> Wasm module context, holding the information needed by
 * {@link Catalog} and client applications.
 */
public class DynamicContext extends Context {

    public enum Mode {
        RuntimeCompiler,
        Interpreter
    }

    private final Mode mode;

    private Instance instance;

    DynamicContext(final String name, final WasmModule wasmModule, final Mode mode) {
        super(name, wasmModule);
        this.mode = mode;
    }

    /**
     * Returns a {@link Instance} obtained for the {@link #getWasmModule()}.
     *
     * @return A {@link Instance} object that is the run-time representation of the Wasm module code.
     */
    @Override
    public Instance chicoryInstance() {
        // lazily generate instance
        if (this.instance == null) {
            Instance.Builder builder = Instance.builder(this.getWasmModule());
            if (Objects.requireNonNull(this.mode) == Mode.RuntimeCompiler) {
                // Warn the user and switch to runtime compilation when there isn't enough information about the
                // generated APIs.
                Log.warn("Runtime compilation mode selected for dynamic Wasm module context");
                builder.withMachineFactory(MachineFactoryCompiler::compile)
                        .withMemoryFactory(ByteArrayMemory::new);
            }
            // use the interpreter
            Log.warn("Interpreter mode selected for dynamic Wasm module context");
            this.instance = builder.build();
        }
        return this.instance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DynamicWasmModuleContext{id=").append(getName())
                .append(", wasmModule=").append(getWasmModule().toString());
        sb.append(", mode=").append(mode.toString());
        return sb + "}";
    }

    public static DynamicContext.Builder builder(final String id, final WasmModule wasmModule) {
        return new DynamicContext.Builder(id, wasmModule);
    }

    public static final class Builder extends Context.Builder {
        private Mode mode;

        Builder(String name, WasmModule wasmModule) {
            super(name, wasmModule);
        }

        public DynamicContext.Builder withMode(final Mode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        public DynamicContext build() {
            return new DynamicContext(name, wasmModule, mode);
        }
    }
}
