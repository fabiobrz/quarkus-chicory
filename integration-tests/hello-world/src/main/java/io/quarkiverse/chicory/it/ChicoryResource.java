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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.chicory.runtime.WasmModuleContextRegistry;

@Path("/chicory")
@ApplicationScoped
public class ChicoryResource {
    // This registry "sounds" very much similar to the Store in Chicory
    // verify if we can't reuse (Occam razor)
    @Inject
    WasmModuleContextRegistry wasmModuleContextRegistry;

    @GET
    public String hello() {
        // TODO: guessing the name is not idiomatic in application.conf we define:
        // quarkus.chicory.modules[0]
        // in the photon example we call it "example" but giving the generated class name is confusing
        var instance = wasmModuleContextRegistry.get("io.quarkiverse.chicory.it.OperationModule").instance();
        var result = instance.export("operation").apply(41, 1);

        return "Hello chicory " + result[0];
    }

}
