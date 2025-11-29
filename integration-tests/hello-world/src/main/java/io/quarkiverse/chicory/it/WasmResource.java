package io.quarkiverse.chicory.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.chicory.runtime.wasm.*;

@Path("/wasm")
public class WasmResource {

    @Inject
    StaticCatalog staticCatalog;

    @Inject
    DynamicCatalog dynamicCatalog;

    @GET
    @Path("/static/all")
    public Response getAllStaticContexts() {
        return Response.ok().entity(staticCatalog.all()).build();
    }

    @GET
    @Path("/dynamic/all")
    public Response getAllDynamicContexts() {
        return Response.ok().entity(dynamicCatalog.all()).build();
    }

    @GET
    @Path("/all")
    public Response getAllContexts() {
        return Response.ok().entity(
                dynamicCatalog.all().entrySet().addAll(staticCatalog.all().entrySet())).build();
    }
}
