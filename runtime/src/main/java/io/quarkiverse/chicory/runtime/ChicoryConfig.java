package io.quarkiverse.chicory.runtime;

import java.io.File;
import java.util.function.Function;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

// TODO: this is just for the PoC, the configuration should reflect proper use cases...
@ConfigMapping(prefix = "quarkus.chicory")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ChicoryConfig {

    /**
     * The WASM module path.
     */
    @WithDefault("./src/main/resources/photon_example.wasm")
    File modulePath();

    /**
     * The FullyQualifiedDomain name of the class that should be used to
     * set {@link com.dylibso.chicory.runtime.Instance.Builder#withMachineFactory(Function)}
     */
    @WithDefault("org.acme.Photon")
    String machineFactoryClassName();

    /**
     * The name of the method that should be used to
     * set {@link com.dylibso.chicory.runtime.Instance.Builder#withMachineFactory(Function)}
     */
    @WithDefault("create")
    String machineFactoryMethodName();
}
