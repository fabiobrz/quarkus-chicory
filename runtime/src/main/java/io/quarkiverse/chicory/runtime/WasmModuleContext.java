package io.quarkiverse.chicory.runtime;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.util.StringUtil;

/**
 * A managed Wasm module context, holding the information needed by {@link WasmModuleContextRegistry} and client
 * applications.
 */
public class WasmModuleContext {
    private final String name;
    private final WasmModule wasmModule;

    private final String factoryClassName;
    private final String factoryMethodName;

    private Instance instance;

    WasmModuleContext(final String name, final WasmModule wasmModule, final String factoryClassName,
            final String factoryMethodName) {
        this.name = name;
        // optional data
        this.wasmModule = wasmModule;
        this.factoryClassName = factoryClassName;
        this.factoryMethodName = factoryMethodName;
    }

    public String getName() {
        return name;
    }

    public WasmModule getWasmModule() {
        return wasmModule;
    }

    /**
     * Returns a {@link Instance} obtained for the {@link #getWasmModule()}.
     * <ul>
     * <li></il>In <i>quarkus:dev mode</i>, the Wasm module is compiled via runtime compilation.</li>
     * <li>In <i>production mode</i>, if both {@link #factoryClassName} and {@link #factoryMethodName} are initialized,
     * the API provided by the Java classes generated at build time is used, otherwise runtime compilation
     * is used.</li>
     * </ul>
     *
     * @return A {@link Instance} object that is the run-time representation of the Wasm module code.
     */
    public Instance instance() {
        // lazily generate instance
        if (this.instance == null) {
            Instance.Builder builder = Instance.builder(this.getWasmModule());
            if (LaunchMode.current() == LaunchMode.NORMAL) {
                // This could be a static file based Wasm module, built in at build time into a generated Java API,
                // which SHOULD ultimately be used in production mode
                if (StringUtil.isNullOrEmpty(this.factoryClassName) || StringUtil.isNullOrEmpty(this.factoryMethodName)) {
                    // Warn the user and switch to runtime compilation when there isn't enough information about the
                    // generated APIs.
                    Log.warn("Factory class or method name is null or empty in production mode, therefore " +
                            "runtime compilation will be executed");
                    builder.withMachineFactory(MachineFactoryCompiler::compile)
                            .withMemoryFactory(ByteArrayMemory::new);
                } else {
                    builder.withMachineFactory(WasmModuleUtils.loadMachineFactoryFunction(
                            this.factoryClassName, this.factoryMethodName));
                }
            } else {
                // In quarkus:dev mode, _always_ use runtime compilation, so that live reload works
                Log.info("quarkus:dev mode enabled, runtime compilation will be executed");
                builder.withMachineFactory(MachineFactoryCompiler::compile)
                        .withMemoryFactory(ByteArrayMemory::new);
            }
            this.instance = builder.build();
        }
        return this.instance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WasmModuleContext{id=").append(name)
                .append(", wasmModule=").append(wasmModule.toString());
        if (!StringUtil.isNullOrEmpty(factoryClassName)) {
            sb.append(", factoryClassName=").append(factoryClassName);
        }
        if (!StringUtil.isNullOrEmpty(factoryMethodName)) {
            sb.append(", factoryMethodName=").append(factoryMethodName);
        }
        return sb + "}";
    }

    public static WasmModuleContext.Builder builder(final String id, final WasmModule wasmModule) {
        return new WasmModuleContext.Builder(id, wasmModule);
    }

    public static final class Builder {
        private final String name;
        private final WasmModule wasmModule;
        private String factoryClassName;
        private String factoryMethodName;

        private Builder(final String name, final WasmModule wasmModule) {
            this.name = name;
            this.wasmModule = wasmModule;
        }

        public WasmModuleContext.Builder withFactoryClassName(final String factoryClassName) {
            this.factoryClassName = factoryClassName;
            return this;
        }

        public WasmModuleContext.Builder withFactoryMethodName(final String factoryMethodName) {
            this.factoryMethodName = factoryMethodName;
            return this;
        }

        public WasmModuleContext build() {
            return new WasmModuleContext(name, wasmModule, factoryClassName, factoryMethodName);
        }
    }
}
