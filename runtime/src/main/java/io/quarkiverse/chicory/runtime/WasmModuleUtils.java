package io.quarkiverse.chicory.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;

/**
 * Utilities used by the classes in the {@link io.quarkiverse.chicory.runtime} package.
 */
public class WasmModuleUtils {

    /**
     * Returns a function that represents the actual Wasm module factory method.
     *
     * @param className A fully qualified domain name that identifies the class providing the factory method
     *        represented by the returned function
     * @param methodName The name of the method represented by the returned function
     * @return A {@link Function} instance that represents the actual Wasm module factory method.
     */
    public static Function<Instance, Machine> loadMachineFactoryFunction(final String className, final String methodName) {
        try {
            Class<?> dynamicClass = Thread.currentThread().getContextClassLoader().loadClass(className);
            MethodType functionType = MethodType.methodType(Machine.class, Instance.class);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle methodHandle = lookup.findStatic(dynamicClass, methodName, functionType);
            MethodHandle adaptedHandle = methodHandle.asType(functionType);
            return new WasmModuleUtils.InvokedFunction(adaptedHandle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot load the machine factory function reference: " + e);
        }
    }

    /**
     * A concrete implementation of the function that will be applied to compile the actual Wasm module.
     */
    public static class InvokedFunction implements Function<Instance, Machine> {
        private final MethodHandle handle;

        InvokedFunction(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public Machine apply(Instance instance) {
            try {
                return (Machine) handle.invokeExact(instance);
            } catch (Throwable t) {
                throw new RuntimeException("Error invoking dynamic method", t);
            }
        }
    }
}
