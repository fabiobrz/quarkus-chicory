package io.quarkiverse.chicory.runtime.wasm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;

/**
 * A catalog that holds managed Wasm modules at runtime.
 */
public class Catalog {

    protected final Map<String, Context> storage = new HashMap<>();

    public Context get(final String name) {
        return storage.get(name);
    }

    public Map<String, Context> all() {
        return Collections.unmodifiableMap(storage);
    }

    public void log() {
        Log.info("Loaded Wasm module contexts:\n\n" + all().entrySet().stream()
                .map(e -> " - " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n")));
    }
}
