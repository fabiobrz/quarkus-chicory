package io.quarkiverse.chicory.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.chicory.runtime.wasm.*;

@Path("/wasm")
public class WasmResource {

    @Inject
    WasmQuarkusContextRegistry wasmQuarkusContextRegistry;

    @GET
    @Path("/all")
    public Response getAll() {
        return Response.ok().entity(wasmQuarkusContextRegistry.all()).build();
    }
}
