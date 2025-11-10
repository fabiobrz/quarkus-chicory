package io.quarkiverse.chicory.deployment;

import java.lang.reflect.InvocationTargetException;

import com.dylibso.chicory.runtime.Instance;

import io.quarkiverse.chicory.runtime.ChicoryConfig;
import io.quarkiverse.chicory.runtime.ChicoryInstanceRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ChicoryProcessor {

    private static final String FEATURE = "chicory";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem produceConfiguredClient(ChicoryInstanceRecorder recorder,
            ChicoryConfig config)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException {

        return SyntheticBeanBuildItem
                .configure(Instance.class)
                .scope(jakarta.enterprise.context.ApplicationScoped.class)
                // Pass the configuration to the runtime recorder method
                .runtimeValue(recorder.createChicoryInstance(config))
                .setRuntimeInit()
                .unremovable()
                .done();
    }
}
