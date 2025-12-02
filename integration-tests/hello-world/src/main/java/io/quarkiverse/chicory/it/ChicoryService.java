package io.quarkiverse.chicory.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.dylibso.chicory.runtime.Instance;

import io.quarkiverse.chicory.runtime.wasm.Wasms;

@ApplicationScoped
public class ChicoryService {

    @Inject
    Wasms wasms;

    Instance getInstance(final String name) {
        return wasms.get(name).chicoryInstance();
    }
}
