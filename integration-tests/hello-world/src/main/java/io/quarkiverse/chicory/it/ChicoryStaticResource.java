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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import com.dylibso.chicory.runtime.Instance;

import io.quarkiverse.chicory.runtime.ChicoryInstance;
import io.quarkiverse.chicory.runtime.wasm.Wasms;

@Path("/chicory/static")
@ApplicationScoped
public class ChicoryStaticResource {

    // Only used to showcase how Wasm modules can be obtained by the Quarkus Chicory registry
    @Inject
    Wasms wasms;

    @ChicoryInstance("operation-static")
    Instance staticOperationInstance;

    @PostConstruct
    public void init() {
        // The Wasm module is obtained by the name it was registered with,
        // here it was loaded statically at build time statically, based on the application configuration.
        // Therefore, we can rely on the injected named bean to obtain the Chicory Instance in @PostConstruct...
        staticOperationInstance = Instance.builder(wasms.get("operation-static").getModule()).build();
        /*
         * ... although we could have just done something like:
         * staticOperationInstance = Instance.builder(Parser.parse(
         * ChicoryResource.class.getClassLoader().getResourceAsStream("operation.wasm"))).build();
         */
    }

    @GET
    public Response hello() {
        var result = staticOperationInstance.export("operation").apply(41, 1);
        return Response.ok("Hello chicory (static): " + result[0]).build();
    }
}
