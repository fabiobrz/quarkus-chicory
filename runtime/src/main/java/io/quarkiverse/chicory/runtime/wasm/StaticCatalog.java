package io.quarkiverse.chicory.runtime.wasm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.ChicoryConfig;

public class StaticCatalog extends Catalog {

    private static final String FACTORY_METHOD_NAME_CREATE = "create";

    StaticCatalog(ChicoryConfig config) {
        // Initialize the internal static catalog map
        for (Map.Entry<String, ChicoryConfig.ModuleConfig> moduleConfigEntry : config.modules().entrySet()) {
            StaticContext context = createStatic(moduleConfigEntry.getKey(), moduleConfigEntry.getValue());
            storage.put(moduleConfigEntry.getKey(), context);
        }
    }

    private static WasmModule getFromStream(final String wasmResource) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(wasmResource)) {
            return Parser.parse(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot access Wasm module resource: " + wasmResource, e);
        }
    }

    private static StaticContext createStatic(final String key, final ChicoryConfig.ModuleConfig config) {
        final String factoryClassName = config.name();
        final WasmModule wasmModule = config.wasmFile().isPresent() ? Parser.parse(config.wasmFile().get())
                : (config.wasmResource().isPresent() ? getFromStream(config.wasmResource().get()) : null);
        if (wasmModule == null) {
            throw new IllegalStateException(
                    "Cannot create Wasm module context payload because neither a resource name nor a file path is defined.");
        }
        return new StaticContext(key, wasmModule, factoryClassName, FACTORY_METHOD_NAME_CREATE);
    }
}
