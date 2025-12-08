package io.quarkiverse.chicory.deployment.items;

import io.quarkus.builder.item.EmptyBuildItem;

/**
 * An {@link EmptyBuildItem} subclass that marks the completion of a build step that load all configured Wasm modules.
 */
public class WasmRegistrationCompleted extends EmptyBuildItem {
}
