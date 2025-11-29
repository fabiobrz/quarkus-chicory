package io.quarkiverse.chicory.runtime.wasm;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.WasmModule;

public abstract class Context {
    private final String name;
    private final WasmModule wasmModule;

    Context(final String name, final WasmModule wasmModule) {
        this.name = name;
        // optional data
        this.wasmModule = wasmModule;
    }

    public String getName() {
        return name;
    }

    public WasmModule getWasmModule() {
        return wasmModule;
    }

    public abstract Instance chicoryInstance();

    public abstract static class Builder {
        protected final String name;
        protected final WasmModule wasmModule;

        protected Builder(final String name, final WasmModule wasmModule) {
            this.name = name;
            this.wasmModule = wasmModule;
        }

        public abstract Context build();
    }
}
