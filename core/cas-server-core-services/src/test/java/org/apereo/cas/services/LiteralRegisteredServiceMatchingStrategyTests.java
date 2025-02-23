package org.apereo.cas.services;

import org.apereo.cas.util.serialization.JacksonObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link LiteralRegisteredServiceMatchingStrategyTests}.
 *
 * @author Misagh Moayyed
 * @since 6.3.0
 */
@Tag("RegisteredService")
class LiteralRegisteredServiceMatchingStrategyTests {
    private static final File JSON_FILE = new File(FileUtils.getTempDirectoryPath(), "ExactLiteralRegisteredServiceMatchingStrategyTests.json");

    private static final ObjectMapper MAPPER = JacksonObjectMapperFactory.builder()
        .defaultTypingEnabled(true).build().toObjectMapper();

    @Test
    void verifySerialization() throws Exception {
        val service = RegisteredServiceTestUtils.getRegisteredService(UUID.randomUUID().toString());
        val strategy = new LiteralRegisteredServiceMatchingStrategy().setCaseInsensitive(true);
        service.setMatchingStrategy(strategy);
        MAPPER.writeValue(JSON_FILE, service);
        val read = MAPPER.readValue(JSON_FILE, RegisteredService.class);
        assertEquals(read, service);
    }

    @Test
    void verifyOperationCaseInsensitive() {
        val service = RegisteredServiceTestUtils.getRegisteredService(RegisteredServiceTestUtils.CONST_TEST_URL);
        val strategy = new LiteralRegisteredServiceMatchingStrategy().setCaseInsensitive(true);
        assertTrue(strategy.matches(service, RegisteredServiceTestUtils.CONST_TEST_URL));
        assertTrue(strategy.matches(service, RegisteredServiceTestUtils.CONST_TEST_URL.toUpperCase(Locale.ENGLISH)));
        assertFalse(strategy.matches(service, "https://.*"));
    }

    @Test
    void verifyOperationCaseSensitive() {
        val service = RegisteredServiceTestUtils.getRegisteredService(RegisteredServiceTestUtils.CONST_TEST_URL);
        val strategy = new LiteralRegisteredServiceMatchingStrategy().setCaseInsensitive(false);
        assertFalse(strategy.matches(service, RegisteredServiceTestUtils.CONST_TEST_URL.toUpperCase(Locale.ENGLISH)));
    }


}
