package io.quarkiverse.chicory.runtime.wasm;

import java.util.Objects;
import java.util.function.Function;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;

import io.quarkus.logging.Log;

class DynamicInstanceProvider implements Function<Wasm, Instance> {

    private Instance instance;

    @Override
    public Instance apply(Wasm wasm) {
        // lazily generate instance
        if (this.instance == null) {
            Instance.Builder builder = Instance.builder(wasm.getModule());
            if (Objects.requireNonNull(wasm.getMode()) == ExecutionMode.RuntimeCompiler) {
                // Warn the user and switch to runtime compilation when there isn't enough information about the
                // generated APIs.
                Log.warn("Runtime execution mode selected for dynamic Wasm module");
                builder.withMachineFactory(MachineFactoryCompiler::compile)
                        .withMemoryFactory(ByteArrayMemory::new);
            }
            // use the interpreter
            Log.warn("Interpreter execution mode selected for dynamic Wasm module");
            this.instance = builder.build();
        }
        return this.instance;
    }
}
