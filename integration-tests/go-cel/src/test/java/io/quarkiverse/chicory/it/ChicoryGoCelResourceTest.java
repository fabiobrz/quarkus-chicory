package io.quarkiverse.chicory.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ChicoryGoCelResourceTest {

    @Test
    public void testValidationEndpoint() throws IOException {
        String manifestJson = readResource("pod.json");
        String celPolicy = readResource("cel.policy");

        given()
                .multiPart("manifestJson", manifestJson)
                .multiPart("celPolicy", celPolicy)
                .when()
                .post("/chicory/validate")
                .then()
                .statusCode(200)
                .body(containsString("Go-CEL validation result: 1"));
    }

    private String readResource(String fileName) throws IOException {
        var url = Thread.currentThread().getContextClassLoader().getResource(fileName);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        return Files.readString(Path.of(url.getPath()));
    }
}
