package io.quarkiverse.chicory.runtime.wasm;

import io.quarkus.logging.Log;

public class DynamicCatalog extends Catalog implements ModifiableCatalog {

    public Context add(final Context wasmModule) {
        if (storage.containsKey(wasmModule.getName())) {
            Log.info("Wasm module context " + wasmModule.getName() + " is already registered, and will be replaced");
        } else {
            Log.info("A new Wasm module context " + wasmModule.getName() + " will be registered");
        }
        storage.put(wasmModule.getName(), wasmModule);
        log();
        return wasmModule;
    }

    public Context update(final Context context) {
        if (!storage.containsKey(context.getName())) {
            throw new IllegalArgumentException(
                    "Cannot find the " + context.getName() + "Wasm module that should be updated.");
        }
        Context existing = storage.remove(context.getName());
        storage.put(existing.getName(), context);
        return existing;
    }
}
