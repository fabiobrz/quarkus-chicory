package io.quarkiverse.chicory.runtime.wasm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.Function;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.InterpreterMachine;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

/**
 * A representation of a Wasm module that is managed by Quarkus Chicory.
 */
public class WasmQuarkusContext {
    private final String name;
    private final WasmModule module;
    private final boolean isStatic;
    private final ExecutionMode executionMode;

    // Client code can only create via the Builder
    WasmQuarkusContext(final String name, final WasmModule module, final ExecutionMode executionMode) {
        this.name = name;
        this.module = module;
        this.executionMode = executionMode;
        this.isStatic = false;
    }

    // Client code can only create via the Builder
    WasmQuarkusContext(final String name, final WasmModule module) {
        this.name = name;
        this.module = module;
        this.executionMode = ExecutionMode.Interpreter;
        this.isStatic = true;
    }

    public String getName() {
        return name;
    }

    public WasmModule getModule() {
        return module;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public static WasmQuarkusContext.Builder builder(final String name, final WasmModule wasmModule) {
        return new Builder(name, wasmModule);
    }

    public Function<Instance, Machine> getMachineFactory() {
        if (this.isStatic()) {
            // a statically configured Wasm module
            if (LaunchMode.current() == LaunchMode.NORMAL) {
                // This is built in at build time into a generated Java API, which SHOULD ultimately be used in
                // production mode
                Log.info("  PROD mode enabled, build time generated classes will be executed");
                return instance -> {
                    // similar to the generated raw Java class "create()"
                    final String machineClazzName = this.getName() + "Machine";
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
                };
            } else {
                // In quarkus:dev mode, i.e. where live reload works, we let the configuration dictate the
                // execution mode
                if (Objects.requireNonNull(this.getExecutionMode()) == ExecutionMode.RuntimeCompiler) {
                    Log.info("quarkus:dev mode enabled, runtime compilation will be executed");
                    return MachineFactoryCompiler::compile;
                } else {
                    Log.warn("Interpreter execution mode selected for static Wasm module");
                    return InterpreterMachine::new;
                }
            }
        } else {
            // a dynamically configured Wasm module
            if (Objects.requireNonNull(this.getExecutionMode()) == ExecutionMode.RuntimeCompiler) {
                Log.info("Runtime execution mode selected for dynamic Wasm module");
                return MachineFactoryCompiler::compile;
            }
            Log.info("Interpreter execution mode selected for dynamic Wasm module");
            return InterpreterMachine::new;
        }
    }

    public static class Builder {
        protected final String name;
        protected final WasmModule wasmModule;
        private ExecutionMode executionMode;

        protected Builder(final String name, final WasmModule wasmModule) {
            this.name = name;
            this.wasmModule = wasmModule;
            this.executionMode = ExecutionMode.Interpreter;
        }

        public Builder withMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public WasmQuarkusContext build() {
            return new WasmQuarkusContext(name, wasmModule, executionMode);
        }
    }
}
