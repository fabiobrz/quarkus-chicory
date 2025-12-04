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
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;

import io.quarkiverse.chicory.runtime.wasm.ExecutionMode;
import io.quarkiverse.chicory.runtime.wasm.Wasm;
import io.quarkiverse.chicory.runtime.wasm.Wasms;

@Path("/chicory")
@ApplicationScoped
public class ChicoryResource {

    @Inject
    Wasms wasms;

    private static final Deque expectedStack = new ArrayDeque<Integer>(2);

    private static final String DYNAMIC_WASM_MODULE_NAME_OPERATION = "operation-dynamic";

    @PostConstruct
    public void init() {
        try {
            // TODO: this returns null, why??? it's surprising
            // ChicoryResource.class.getResourceAsStream("/wasm/operation.wasm")

            wasms.add(Wasm.builder(
                    DYNAMIC_WASM_MODULE_NAME_OPERATION, Parser.parse(
                            ChicoryResource.class.getResourceAsStream("/operation.wasm")
                                    .readAllBytes()))
                    // TODO: this is leaky abstraction, and doesn't play well with the rest
                    .withInstanceProvider(module -> Instance.builder(module.getModule())
                            .withImportValues(ImportValues.builder()
                                    .addFunction(
                                            new HostFunction(
                                                    "env",
                                                    "host_log",
                                                    FunctionType.of(List.of(ValType.I32), List.of()),
                                                    (inst, args) -> {
                                                        var num = (int) args[0];
                                                        assert expectedStack.pop().equals(num);
                                                        System.out.println("Number: " + num);
                                                        return null;
                                                    }))
                                    .build())
                            .build())
                    .withMode(ExecutionMode.RuntimeCompiler) // TODO: we need to enable also the build time compiler here
                    .build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GET
    public Response hello() {
        expectedStack.add(41);
        expectedStack.add(1);

        var result = wasms
                .get(DYNAMIC_WASM_MODULE_NAME_OPERATION)
                .chicoryInstance()
                .exports()
                .function("operation")
                .apply(41, 1);

        return Response.ok("Hello chicory: " + result[0]).build();
    }
}
