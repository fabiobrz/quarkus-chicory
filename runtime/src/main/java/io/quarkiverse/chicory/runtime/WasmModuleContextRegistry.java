package io.quarkiverse.chicory.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.dylibso.chicory.wasm.Parser;

import io.quarkus.logging.Log;

/**
 * A registry that holds managed Wasm modules at runtime.
 */
public class WasmModuleContextRegistry {

    static final String FACTORY_METHOD_NAME_CREATE = "create";
    private final Map<String, WasmModuleContext> registry;

    /**
     * The configuration is injected and data is turned into concrete Wasm module representations.
     *
     * @param config The managed Wasm modules runtime configuration
     */
    public WasmModuleContextRegistry(ChicoryConfig config) {
        // Initialize the internal registry map
        this.registry = config.modules().entrySet().stream()
                .map(e -> this.createContext(e.getKey(), e.getValue()))
                .collect(Collectors.toMap(WasmModuleContext::getName, module -> module));
        logAll();
    }

    private WasmModuleContext createContext(final String key, final ChicoryConfig.ModuleConfig config) {
        final String factoryClassName = config.name();
        return WasmModuleContext.builder(key, Parser.parse(config.wasmFile()))
                .withFactoryClassName(factoryClassName)
                .withFactoryMethodName(FACTORY_METHOD_NAME_CREATE).build();
    }

    public WasmModuleContext get(String name) {
        return registry.get(name);
    }

    public Map<String, WasmModuleContext> all() {
        return Collections.unmodifiableMap(registry);
    }

    public void add(WasmModuleContext wasmModule) {
        if (registry.containsKey(wasmModule.getName())) {
            Log.info("Wasm module context " + wasmModule.getName() + " is already registered, and will be replaced");
        } else {
            Log.info("A new Wasm module context " + wasmModule.getName() + " will be registered");
        }
        registry.put(wasmModule.getName(), wasmModule);
        logAll();
    }

    public void logAll() {
        Log.info("Loaded Wasm module contexts:\n\n" + all().entrySet().stream()
                .map(e -> " - " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n")));
    }
}
