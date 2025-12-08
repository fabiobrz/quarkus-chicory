/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.chicory.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.wasm.ExecutionMode;
import io.quarkiverse.chicory.runtime.wasm.Wasm;
import io.quarkiverse.chicory.runtime.wasm.Wasms;
import io.quarkus.logging.Log;

@Path("/chicory/dynamic")
@ApplicationScoped
public class ChicoryDynamicResource {

    @Inject
    Wasms wasms;

    Instance instance;

    private static final String DYNAMIC_WASM_MODULE_NAME_OPERATION = "operation-dynamic";

    @GET
    public Response hello() {
        /*
         * The Wasm module could be obtained by the name it was registered with,
         * here dynamically at runtime, hence we'd need to check for it to be actually present.
         * We set the "instance" value in the "upload" method already, so this is all commented out and
         * kept just for reference on how the injected "Wasms" bean could be used,
         *
         * Wasm wasm = wasms.get(DYNAMIC_WASM_MODULE_NAME_OPERATION);
         * if (wasm == null) {
         * return Response.status(Response.Status.NOT_FOUND)
         * .entity("Wasm module " + DYNAMIC_WASM_MODULE_NAME_OPERATION +
         * "not found. Either you provided a wrong name, or it wasn't uploaded yet.")
         * .build();
         * }
         * instance = Instance.builder(wasms.get("operation-dynnamic")).build();
         *
         */
        var result = instance.export("operation").apply(41, 1);
        return Response.ok("Hello chicory (dynamic): " + result[0]).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("module") FileUpload wasmModule,
            @RestForm("execution-mode") ExecutionMode executionMode) throws IOException {
        try (final InputStream wasmModuleInputStream = Files.newInputStream(wasmModule.uploadedFile())) {
            if (wasmModuleInputStream.available() <= 0) {
                throw new IllegalArgumentException("ERROR: Wasm module NOT uploaded 0");
            }
            WasmModule parsedModule = Parser.parse(wasmModuleInputStream.readAllBytes());
            Wasm added = wasms.add(Wasm.builder(DYNAMIC_WASM_MODULE_NAME_OPERATION, parsedModule)
                    .withMode(executionMode)
                    .build());
            Log.info("Wasm module uploaded");
            Instance.Builder builder = Instance.builder(parsedModule);
            if (added.getMachineFactory() != null) {
                builder.withMachineFactory(added.getMachineFactory());
            }
            if (ExecutionMode.RuntimeCompiler.equals(executionMode)) {
                builder.withMemoryFactory(ByteArrayMemory::new);
            }
            instance = builder.build();
            return Response.accepted(added).build();
        }
    }
}
