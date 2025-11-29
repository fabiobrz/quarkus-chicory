package io.quarkiverse.chicory.runtime.wasm;

public interface ModifiableCatalog {

    Context add(final Context wasmModule);

    Context update(final Context context);
}
