package org.apereo.cas.ticket.accesstoken;

import org.apereo.cas.AbstractOAuth20Tests;
import org.apereo.cas.services.RegisteredServiceTestUtils;
import org.apereo.cas.support.oauth.OAuth20GrantTypes;
import org.apereo.cas.support.oauth.OAuth20ResponseTypes;
import org.apereo.cas.support.oauth.services.DefaultRegisteredServiceOAuthAccessTokenExpirationPolicy;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link OAuth20DefaultAccessTokenFactoryTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Tag("OAuthToken")
class OAuth20DefaultAccessTokenFactoryTests extends AbstractOAuth20Tests {

    @Test
    void verifyOperationCreate() {
        val registeredService = getRegisteredService("https://app.oauth.org", "clientid-at", "secret-at");
        registeredService.setAccessTokenExpirationPolicy(
            new DefaultRegisteredServiceOAuthAccessTokenExpirationPolicy("PT10S", "PT10S"));
        servicesManager.save(registeredService);
        val token = defaultAccessTokenFactory.create(RegisteredServiceTestUtils.getService("https://app.oauth.org"),
            RegisteredServiceTestUtils.getAuthentication(),
            Set.of("Scope1", "Scope2"), "clientid-at",
            OAuth20ResponseTypes.CODE, OAuth20GrantTypes.AUTHORIZATION_CODE);
        assertNotNull(token);
        assertNotNull(defaultAccessTokenFactory.get(OAuth20AccessToken.class));
    }
}
