package org.apereo.cas.services;

import org.apereo.cas.util.serialization.JacksonObjectMapperFactory;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apereo.services.persondir.util.CaseCanonicalizationMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.io.File;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Misagh Moayyed
 * @since 4.1.0
 */
@Tag("RegisteredService")
class DefaultRegisteredServiceUsernameProviderTests {
    private static final File JSON_FILE = new File(FileUtils.getTempDirectoryPath(), "defaultRegisteredServiceUsernameProvider.json");

    private static final ObjectMapper MAPPER = JacksonObjectMapperFactory.builder()
        .defaultTypingEnabled(true).build().toObjectMapper();

    @Test
    void verifyNoCanonAndEncrypt() {
        val applicationContext = new StaticApplicationContext();
        val beanFactory = applicationContext.getBeanFactory();
        val cipher = RegisteredServiceCipherExecutor.noOp();
        beanFactory.initializeBean(cipher, RegisteredServiceCipherExecutor.DEFAULT_BEAN_NAME);
        beanFactory.autowireBean(cipher);
        beanFactory.registerSingleton(RegisteredServiceCipherExecutor.DEFAULT_BEAN_NAME, cipher);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val provider = new DefaultRegisteredServiceUsernameProvider();
        provider.setCanonicalizationMode(null);
        provider.setEncryptUsername(true);
        val principal = RegisteredServiceTestUtils.getPrincipal("ID");
        val service = RegisteredServiceTestUtils.getRegisteredService("usernameAttributeProviderService");

        val usernameContext = RegisteredServiceUsernameProviderContext.builder()
            .registeredService(service)
            .service(RegisteredServiceTestUtils.getService())
            .principal(principal)
            .build();

        val id = provider.resolveUsername(usernameContext);
        provider.initialize();
        assertEquals(id, principal.getId().toUpperCase(Locale.ENGLISH));
    }

    @Test
    void verifyRegServiceUsernameUpper() {
        val provider = new DefaultRegisteredServiceUsernameProvider();
        provider.setCanonicalizationMode(CaseCanonicalizationMode.UPPER.name());
        val principal = RegisteredServiceTestUtils.getPrincipal("id");

        val usernameContext = RegisteredServiceUsernameProviderContext.builder()
            .registeredService(RegisteredServiceTestUtils.getRegisteredService("usernameAttributeProviderService"))
            .service(RegisteredServiceTestUtils.getService())
            .principal(principal)
            .build();
        val id = provider.resolveUsername(usernameContext);
        assertEquals(id, principal.getId().toUpperCase(Locale.ENGLISH));
    }

    @Test
    void verifyPatternRemoval() {
        val provider = new DefaultRegisteredServiceUsernameProvider();
        provider.setCanonicalizationMode(CaseCanonicalizationMode.UPPER.name());
        provider.setRemovePattern("@.+");
        val principal = RegisteredServiceTestUtils.getPrincipal("casuser@example.org");

        val usernameContext = RegisteredServiceUsernameProviderContext.builder()
            .registeredService(RegisteredServiceTestUtils.getRegisteredService("usernameAttributeProviderService"))
            .service(RegisteredServiceTestUtils.getService())
            .principal(principal)
            .build();
        val id = provider.resolveUsername(usernameContext);
        assertEquals(id, "CASUSER");
    }

    @Test
    void verifyScopedUsername() {
        val provider = new DefaultRegisteredServiceUsernameProvider();
        provider.setCanonicalizationMode(CaseCanonicalizationMode.UPPER.name());
        provider.setScope("example.org");
        val principal = RegisteredServiceTestUtils.getPrincipal("id");

        val usernameContext = RegisteredServiceUsernameProviderContext.builder()
            .registeredService(RegisteredServiceTestUtils.getRegisteredService("usernameAttributeProviderService"))
            .service(RegisteredServiceTestUtils.getService())
            .principal(principal)
            .build();
        val id = provider.resolveUsername(usernameContext);
        assertEquals(id, principal.getId().toUpperCase(Locale.ENGLISH).concat("@EXAMPLE.ORG"));
    }

    @Test
    void verifyRegServiceUsername() {
        val provider = new DefaultRegisteredServiceUsernameProvider();
        val principal = RegisteredServiceTestUtils.getPrincipal("id");

        val usernameContext = RegisteredServiceUsernameProviderContext.builder()
            .registeredService(RegisteredServiceTestUtils.getRegisteredService("id"))
            .service(RegisteredServiceTestUtils.getService())
            .principal(principal)
            .build();
        val id = provider.resolveUsername(usernameContext);
        assertEquals(id, principal.getId());
    }

    @Test
    void verifyEquality() {
        val provider = new DefaultRegisteredServiceUsernameProvider();
        val provider2 = new DefaultRegisteredServiceUsernameProvider();
        assertEquals(provider, provider2);
    }

    @Test
    void verifySerializeADefaultRegisteredServiceUsernameProviderToJson() throws Exception {
        val providerWritten = new DefaultRegisteredServiceUsernameProvider();
        MAPPER.writeValue(JSON_FILE, providerWritten);
        val providerRead = MAPPER.readValue(JSON_FILE, DefaultRegisteredServiceUsernameProvider.class);
        assertEquals(providerWritten, providerRead);
    }
}
