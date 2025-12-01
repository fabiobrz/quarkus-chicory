package io.quarkiverse.chicory.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.dylibso.chicory.runtime.Instance;

import io.quarkiverse.chicory.runtime.wasm.StaticCatalog;

@ApplicationScoped
public class ChicoryService {

    @Inject
    StaticCatalog staticCatalog;

    Instance getInstance(final String name) {
        return staticCatalog.get(name).chicoryInstance();
    }
}
