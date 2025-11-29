package io.quarkiverse.chicory.runtime.wasm;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.util.StringUtil;

/**
 * A managed <i>static</i> Wasm module context, holding the information needed by
 * {@link Catalog} and client applications.
 */
public class StaticContext extends Context {

    private final String factoryClassName;
    private final String factoryMethodName;

    private Instance instance;

    StaticContext(final String name, final WasmModule wasmModule, final String factoryClassName,
            final String factoryMethodName) {
        super(name, wasmModule);
        this.factoryClassName = factoryClassName;
        this.factoryMethodName = factoryMethodName;
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
    @Override
    public Instance chicoryInstance() {
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
                    builder.withMachineFactory(Utils.loadMachineFactoryFunction(
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
        sb.append("StaticWasmModuleContext{id=").append(getName())
                .append(", wasmModule=").append(getWasmModule().toString());
        if (!StringUtil.isNullOrEmpty(factoryClassName)) {
            sb.append(", factoryClassName=").append(factoryClassName);
        }
        if (!StringUtil.isNullOrEmpty(factoryMethodName)) {
            sb.append(", factoryMethodName=").append(factoryMethodName);
        }
        return sb + "}";
    }
}
