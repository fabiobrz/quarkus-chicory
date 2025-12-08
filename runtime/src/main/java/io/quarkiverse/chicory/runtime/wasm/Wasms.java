package io.quarkiverse.chicory.runtime.wasm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;

/**
 * An application scoped bean to manage an application Wasm modules, either statically configured or dynamically loaded
 * at runtime.
 */
@ApplicationScoped
@Unremovable
public class Wasms {

    protected final Map<String, Wasm> storage = new HashMap<>();

    void initialize(ChicoryConfig config) {
        for (Map.Entry<String, ChicoryConfig.ModuleConfig> moduleConfigEntry : config.modules().entrySet()) {
            final String key = moduleConfigEntry.getKey();
            final ChicoryConfig.ModuleConfig moduleConfig = moduleConfigEntry.getValue();
            Log.info("A configured Wasm module " + key + " will be added");
            storage.put(key, Wasms.create(key, moduleConfig));
        }
        log();
    }

    public Wasm add(final Wasm wasm) {
        if (storage.containsKey(wasm.getName())) {
            Log.info("Wasm module " + wasm.getName() + " is already registered, and will be replaced");
        } else {
            Log.info("A new Wasm module " + wasm.getName() + " will be added");
        }
        storage.put(wasm.getName(), wasm);
        log();
        return wasm;
    }

    public Wasm get(final String name) {
        return storage.get(name);
    }

    public Map<String, Wasm> all() {
        return Collections.unmodifiableMap(storage);
    }

    public void log() {
        Log.info("Loaded Wasm modules:\n\n" + all().entrySet().stream()
                .map(e -> " - " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n")));
    }

    static Wasm create(final String key, final ChicoryConfig.ModuleConfig config) {
        final WasmModule wasmModule = config.wasmFile().isPresent() ? Parser.parse(config.wasmFile().get())
                : (config.wasmResource().isPresent() ? getFromStream(config.wasmResource().get()) : null);
        if (wasmModule == null) {
            throw new IllegalStateException(
                    "Cannot create Wasm module payload because neither a resource name nor a file path is defined.");
        }
        return new Wasm(key, wasmModule);
    }

    private static WasmModule getFromStream(final String wasmResource) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(wasmResource)) {
            return Parser.parse(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot access Wasm module resource: " + wasmResource, e);
        }
    }
}
