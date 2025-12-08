package io.quarkiverse.chicory.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.inject.spi.CDI;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.wasm.ExecutionMode;
import io.quarkiverse.chicory.runtime.wasm.Wasm;
import io.quarkiverse.chicory.runtime.wasm.Wasms;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

/**
 * A class that implements the logic to transform bytecode using Chicory APIs at runtime.
 */
public class ChicoryRuntimeTransformer {

    /**
     * Transforms a Chicory {@link Instance#builder(WasmModule)#build()} call that would assign a field annotated by
     * {@link ChicoryInstance}, based on a given Wasm module application configuration and execution environment.
     *
     * @param builder The {@link Instance.Builder} instance whose {@code build()} call should be transformed.
     * @param wasmId The key that identifies a configured Wasm module
     * @return The {@link Instance} object that should be assigned to the annotated field.
     */
    public static Instance transformBuilder(Instance.Builder builder, final String wasmId) {
        // TODO - check whether machineFactory is set already and quit in such case?

        Log.info("  Retrieving config for Wasm module: '" + wasmId + "' to apply field assignment bytecode transformation.");
        // Use programmatic lookup to get the *current* instance of the mutable registry bean
        Wasms wasms = CDI.current().select(Wasms.class).get();
        Optional<Wasm> wasm = Optional.ofNullable(wasms.get(wasmId));
        if (wasm.isPresent()) {
            final Wasm actualWasm = wasm.get();
            if (actualWasm.isStatic()) {
                // a statically configured Wasm module
                if (LaunchMode.current() == LaunchMode.NORMAL) {
                    // This is built in at build time into a generated Java API, which SHOULD ultimately be used in
                    // production mode
                    Log.info("  PROD mode enabled, build time generated classes will be executed");
                    builder.withMachineFactory(new Function<Instance, Machine>() {
                        @Override
                        public Machine apply(Instance instance) {
                            // similar to the generated raw Java class "create()"
                            final String machineClazzName = actualWasm.getName() + "Machine";
                            try {
                                Class<?> machineClazz = Thread.currentThread().getContextClassLoader()
                                        .loadClass(machineClazzName);
                                Class<?>[] parameterTypes = new Class<?>[] { Instance.class };
                                Constructor<?> constructor = machineClazz.getConstructor(parameterTypes);
                                Object[] arguments = new Object[] { instance };
                                return (Machine) constructor.newInstance(arguments);
                            } catch (ClassNotFoundException e) {
                                throw new IllegalStateException("Cannot load class: " + machineClazzName, e);
                            } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                                    | IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } else {
                    // In quarkus:dev mode, i.e. where live reload works, we let the configuration dictate the
                    // execution mode
                    if (Objects.requireNonNull(actualWasm.getExecutionMode()) == ExecutionMode.RuntimeCompiler) {
                        Log.info("quarkus:dev mode enabled, runtime compilation will be executed");
                        builder.withMachineFactory(MachineFactoryCompiler::compile)
                                .withMemoryFactory(ByteArrayMemory::new);
                    } else {
                        Log.warn("Interpreter execution mode selected for static Wasm module");
                    }
                }
            } else {
                // a dynamically configured Wasm module
                if (Objects.requireNonNull(actualWasm.getExecutionMode()) == ExecutionMode.RuntimeCompiler) {
                    Log.info("Runtime execution mode selected for dynamic Wasm module");
                    builder.withMachineFactory(MachineFactoryCompiler::compile)
                            .withMemoryFactory(ByteArrayMemory::new);
                }
                Log.info("Interpreter execution mode selected for dynamic Wasm module");
            }
        } else {
            Log.info("  No configuration found for wasmId '" + wasmId + "'. Using defaults.");
        }
        return builder.build();
    }
}
