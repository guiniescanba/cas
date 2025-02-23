package org.apereo.cas.util.cipher;

import org.apereo.cas.util.MockWebServer;

import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

import static org.apache.commons.lang3.ArrayUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link JsonWebKeySetStringCipherExecutorTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@Tag("Cipher")
class JsonWebKeySetStringCipherExecutorTests {
    private static File getKeystoreFile() throws Exception {
        val jwksKeystore = new ClassPathResource("sample.jwks");
        val data = IOUtils.toString(jwksKeystore.getInputStream(), StandardCharsets.UTF_8);
        val keystoreFile = new File(FileUtils.getTempDirectoryPath(), "sample.jwks");
        FileUtils.write(keystoreFile, data, StandardCharsets.UTF_8);
        return keystoreFile;
    }

    @Test
    void verifyAction() throws Exception {
        val jwksKeystore = new ClassPathResource("sample.jwks");
        val data = IOUtils.toString(jwksKeystore.getInputStream(), StandardCharsets.UTF_8);

        val keystoreFile = getKeystoreFile();
        try (val webServer = new MockWebServer(8435,
            new ByteArrayResource(data.getBytes(StandardCharsets.UTF_8), "REST Output"), MediaType.APPLICATION_JSON_VALUE);
             val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile, "http://localhost:8435")) {
            webServer.start();
            val token = cipher.encode("Misagh");
            assertEquals("Misagh", cipher.decode(token));
            Files.setLastModifiedTime(keystoreFile.toPath(), FileTime.from(Instant.now()));
            Thread.sleep(5_000);
            cipher.destroy();
        }
    }


    @Test
    void verifyEmptyFileForEncoding() throws Exception {
        val keystoreFile = File.createTempFile("keystore", ".json");
        FileUtils.write(keystoreFile, "{ \"keys\": [] }", StandardCharsets.UTF_8);
        try (val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile)) {
            assertThrows(IllegalArgumentException.class, () -> cipher.encode("value", EMPTY_OBJECT_ARRAY));
        }
    }

    @Test
    void verifyEncodingWithPublicKeyOnly() throws Exception {
        val jwksKeystore = new ClassPathResource("sample.jwks");
        val data = IOUtils.toString(jwksKeystore.getInputStream(), StandardCharsets.UTF_8);
        val json = new JsonWebKeySet(data).toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
        val keystoreFile = File.createTempFile("keystorepub", ".json");
        FileUtils.write(keystoreFile, json, StandardCharsets.UTF_8);
        try (val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile, Optional.of("cas"))) {
            assertThrows(IllegalArgumentException.class, () -> cipher.encode("value", EMPTY_OBJECT_ARRAY));
        }
    }

    @Test
    void verifyEmptyFileForDecoding() throws Exception {
        val keystoreFile = File.createTempFile("keystore", ".json");
        FileUtils.write(keystoreFile, "{ \"keys\": [] }", StandardCharsets.UTF_8);
        try (val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile, Optional.of("kid"))) {
            assertThrows(IllegalArgumentException.class, () -> cipher.decode("value", EMPTY_OBJECT_ARRAY));
        }
    }

    @Test
    void verifyEmptyJwks() throws Exception {
        val keystoreFile = getKeystoreFile();
        val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile, Optional.empty(), null);
        assertNotNull(cipher.decode("value", EMPTY_OBJECT_ARRAY));
    }

    @Test
    void verifyEmptyPayload() throws Exception {
        val data = "{ \"keys\": [] }";
        val keystoreFile = getKeystoreFile();
        try (val webServer = new MockWebServer(8435,
            new ByteArrayResource(data.getBytes(StandardCharsets.UTF_8), "REST Output"), MediaType.APPLICATION_JSON_VALUE);
             val cipher = new JsonWebKeySetStringCipherExecutor(keystoreFile, "http://localhost:8435")) {
            webServer.start();
            assertThrows(IllegalArgumentException.class, () -> cipher.encode("Misagh"));
        }
    }
}
