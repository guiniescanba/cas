package org.apereo.cas.web;

import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.config.TokenAuthenticationConfiguration;
import org.apereo.cas.config.TokenAuthenticationWebflowConfiguration;
import org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy;
import org.apereo.cas.services.RegisteredServiceTestUtils;
import org.apereo.cas.token.authentication.TokenCredential;
import org.apereo.cas.web.report.AbstractCasEndpointTests;
import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link TokenAuthenticationEndpointTests}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@TestPropertySource(properties = "management.endpoint.tokenAuth.enabled=true")
@Tag("ActuatorEndpoint")
@Import({
    TokenAuthenticationConfiguration.class,
    TokenAuthenticationWebflowConfiguration.class
})
class TokenAuthenticationEndpointTests extends AbstractCasEndpointTests {
    @Autowired
    @Qualifier("tokenAuthenticationEndpoint")
    private TokenAuthenticationEndpoint endpoint;

    @Autowired
    @Qualifier("tokenAuthenticationHandler")
    private AuthenticationHandler tokenAuthenticationHandler;
    
    @Test
    void verifyOperation() throws Exception {
        val registeredService = RegisteredServiceTestUtils.getRegisteredService(RegisteredServiceTestUtils.CONST_TEST_URL);
        registeredService.setAccessStrategy(new DefaultRegisteredServiceAccessStrategy());
        servicesManager.save(registeredService);
        val results = endpoint.produceToken("casuser", RegisteredServiceTestUtils.CONST_TEST_URL);
        assertTrue(results.containsKey("registeredService"));
        assertTrue(results.containsKey("token"));
        val token = results.get("token").toString();
        val service = RegisteredServiceTestUtils.getService(RegisteredServiceTestUtils.CONST_TEST_URL);
        val authnResults = tokenAuthenticationHandler.authenticate(new TokenCredential(token, service), service);
        assertEquals("casuser", authnResults.getPrincipal().getId());
    }
}
