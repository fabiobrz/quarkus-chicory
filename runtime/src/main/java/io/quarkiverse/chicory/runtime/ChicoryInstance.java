package io.quarkiverse.chicory.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.dylibso.chicory.runtime.Instance;

/**
 * Annotation that designates a Chicory {@link Instance} field in order to be managed by Quarkus Chicory.
 * Must be used on {@link Instance} fields only.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface ChicoryInstance {
    /**
     * A key used to identify configuration properties related to a specific Wasm module,
     * e.g.: it would be "operation" for quarkus.chicory.modules.operation.* properties
     *
     * @return The Wasm module identifier
     */
    String value();
}
