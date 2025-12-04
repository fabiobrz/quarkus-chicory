package io.quarkiverse.chicory.runtime.wasm;

import java.util.function.Function;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

class StaticInstanceProvider implements Function<Wasm, Instance> {

    private Instance instance;
    private static final String FACTORY_METHOD_NAME_CREATE = "create";

    @Override
    public Instance apply(Wasm wasm) {
        // lazily generate instance
        if (this.instance == null) {
            Instance.Builder builder = Instance.builder(wasm.getModule());
            if (LaunchMode.current() == LaunchMode.NORMAL) {
                // This could be a static file based Wasm module, built in at build time into a generated Java API,
                // which SHOULD ultimately be used in production mode
                builder.withMachineFactory(Utils.loadMachineFactoryFunction(
                        wasm.getName(), FACTORY_METHOD_NAME_CREATE));
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
}
