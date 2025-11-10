package io.quarkiverse.chicory.runtime;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.wasm.Parser;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

// TODO: this is just for the PoC, the logic in here needs to be dynamic based on variable use cases...
@Recorder
public class ChicoryInstanceRecorder {

    /**
     * Creates and configures the instance of the external library class.
     * This method runs at application startup.
     */
    public RuntimeValue<Instance> createChicoryInstance(ChicoryConfig config)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException {
        final File modulePath = config.modulePath();
        if (!modulePath.exists()) {
            throw new IllegalStateException("WASM module not found: " + modulePath);
        }

        var module = Parser.parse(modulePath);
        Instance.Builder builder = Instance.builder(module);

        // In dev mode, use runtime compilation so that live reload works
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            Function<Instance, Machine> machineFactoryFunction = loadMachineFactoryFunction(
                    config.machineFactoryClassName(), config.machineFactoryMethodName());
            builder = builder.withMachineFactory(machineFactoryFunction);
        } else {
            builder = builder.withMachineFactory(MachineFactoryCompiler::compile)
                    .withMemoryFactory(ByteArrayMemory::new);
        }

        return new RuntimeValue<>(builder.build());
    }

    private static Function<Instance, Machine> loadMachineFactoryFunction(final String className, final String methodName)
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException {

        Class<?> dynamicClass = Thread.currentThread().getContextClassLoader().loadClass(className);
        MethodType functionType = MethodType.methodType(Machine.class, Instance.class);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle methodHandle = lookup.findStatic(dynamicClass, methodName, functionType);
        MethodHandle adaptedHandle = methodHandle.asType(functionType);

        return new InvokedFunction(adaptedHandle);

    }

    private static class InvokedFunction implements Function<Instance, Machine> {
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
