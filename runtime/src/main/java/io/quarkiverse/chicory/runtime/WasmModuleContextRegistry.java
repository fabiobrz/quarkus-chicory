package io.quarkiverse.chicory.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.dylibso.chicory.wasm.Parser;

import io.quarkus.logging.Log;

/**
 * A registry that holds managed Wasm modules at runtime.
 */
@ApplicationScoped
public class WasmModuleContextRegistry {

    private final Map<String, WasmModuleContext> registry;

    /**
     * The configuration is injected and data is turned into concrete Wasm module representations.
     *
     * @param config The managed Wasm modules runtime configuration
     */
    @Inject
    public WasmModuleContextRegistry(WasmModulesConfig config) {
        // Initialize the internal registry map during application startup
        this.registry = config.modules().stream()
                .map(this::createWasmModuleContext)
                .collect(Collectors.toMap(WasmModuleContext::getId, module -> module));
        logRegisteredWasmModuleContexts();
    }

    private WasmModuleContext createWasmModuleContext(WasmModulesConfig.ModuleConfig config) {
        final String factoryClassName = config.factoryClassName().isPresent() ? config.factoryClassName().get() : null;
        final String factoryMethodName = config.factoryMethodName().isPresent() ? config.factoryMethodName().get() : null;
        return WasmModuleContext.builder(config.id(), Parser.parse(config.staticFilePath()))
                .withFactoryClassName(factoryClassName)
                .withFactoryMethodName(factoryMethodName).build();
    }

    public WasmModuleContext getModuleContextById(String id) {
        return registry.get(id);
    }

    public Map<String, WasmModuleContext> getAllModuleContexts() {
        return Collections.unmodifiableMap(registry);
    }

    public void addWasmModuleContext(WasmModuleContext wasmModule) {
        if (registry.containsKey(wasmModule.getId())) {
            Log.info("Wasm module context " + wasmModule.getId() + " is already registered, and will be replaced");
        } else {
            Log.info("A new Wasm module context " + wasmModule.getId() + " will be registered");
        }

        registry.put(wasmModule.getId(), wasmModule);
        logRegisteredWasmModuleContexts();
    }

    public void logRegisteredWasmModuleContexts() {
        Log.info("Loaded Wasm module contexts:\n\n" + getAllModuleContexts().entrySet().stream()
                .map(e -> " - " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n")));
    }
}
