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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;

import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

@Path("/chicory")
@ApplicationScoped
public class ChicoryGoCelResource {

    WasmModule wasmModule;

    @PostConstruct
    public void init() throws IOException {
        // get the Wasm module payload from the classpath, as an application resource
        final String wasmFileName = "go-cel-wasi.wasm";
        try (InputStream is = ChicoryGoCelResource.class.getClassLoader().getResourceAsStream(wasmFileName)) {
            if (is == null) {
                throw new IllegalStateException("Resource " + wasmFileName + " not found!");
            }
            wasmModule = Parser.parse(is);
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("validate")
    public Response validate(
            @RestForm String manifestJson,
            @RestForm String celPolicy) {

        // Create a ByteArrayOutputStream to capture stdout
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        // Build WASI options with arguments
        // Args: [program-name, policy, input-json]
        WasiOptions options = WasiOptions.builder()
                .withArguments(List.of("go-cel-wasi.wasm", celPolicy, manifestJson))
                .withStdout(stdout)
                .withStderr(stderr)
                .build();

        WasiPreview1 wasi = WasiPreview1.builder()
                .withOptions(options)
                .build();

        Store store = new Store().addFunction(wasi.toHostFunctions());

        // Load module and instantiate
        // This will automatically call _start(), which runs main() and exits
        try {
            store.instantiate("expr", wasmModule);
            // If we get here without exception, something is wrong
            throw new RuntimeException("Expected WasiExitException but got none");
        } catch (com.dylibso.chicory.wasi.WasiExitException e) {
            // Expected - program exits after printing result
            if (e.exitCode() == 2) {
                // Usage error
                throw new RuntimeException("Usage error: " + stderr);
            } else if (e.exitCode() != 0) {
                throw new RuntimeException("Unexpected exit code: " + e.exitCode() + ", stderr: " + stderr);
            }
            // Exit code 0 is success
        }

        // Read the result from stdout
        String output = stdout.toString().trim();
        if (output.isEmpty()) {
            throw new RuntimeException("No output from WASM module. Stderr: " + stderr);
        }

        return Response.ok("Go-CEL validation result: " + Integer.parseInt(output)).build();
    }
}
