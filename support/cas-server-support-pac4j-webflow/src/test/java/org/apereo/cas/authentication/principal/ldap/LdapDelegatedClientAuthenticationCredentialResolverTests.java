package org.apereo.cas.authentication.principal.ldap;

import org.apereo.cas.adaptors.ldap.LdapIntegrationTestsOperations;
import org.apereo.cas.authentication.principal.ClientCredential;
import org.apereo.cas.authentication.principal.DelegatedClientAuthenticationCredentialResolver;
import org.apereo.cas.config.DelegatedAuthenticationProfileSelectionConfiguration;
import org.apereo.cas.util.RandomUtils;
import org.apereo.cas.util.junit.EnabledIfListeningOnPort;
import org.apereo.cas.web.BaseDelegatedAuthenticationTests;

import com.unboundid.ldap.sdk.LDAPConnection;
import lombok.Cleanup;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apereo.inspektr.common.web.ClientInfo;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.webflow.context.ExternalContextHolder;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.execution.RequestContextHolder;
import org.springframework.webflow.test.MockRequestContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link LdapDelegatedClientAuthenticationCredentialResolverTests}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@Tag("Ldap")
@EnabledIfListeningOnPort(port = 10389)
@SpringBootTest(classes = {
    DelegatedAuthenticationProfileSelectionConfiguration.class,
    BaseDelegatedAuthenticationTests.SharedTestConfiguration.class
}, properties = {
    "cas.authn.pac4j.profile-selection.ldap.ldap-url=ldap://localhost:10389",
    "cas.authn.pac4j.profile-selection.ldap.base-dn=ou=people,dc=example,dc=org",
    "cas.authn.pac4j.profile-selection.ldap.search-filter=uid={0}",
    "cas.authn.pac4j.profile-selection.ldap.bind-dn=cn=Directory Manager",
    "cas.authn.pac4j.profile-selection.ldap.bind-credential=password",
    "cas.authn.pac4j.profile-selection.ldap.profile-id-attribute=cn",
    "cas.authn.pac4j.profile-selection.ldap.attributes=sn,givenName,uid,mail,cn"
})
class LdapDelegatedClientAuthenticationCredentialResolverTests {
    private static final String USER = RandomUtils.randomAlphabetic(10);

    private static final int LDAP_PORT = 10389;

    @Autowired
    @Qualifier("ldapDelegatedClientAuthenticationCredentialResolver")
    private DelegatedClientAuthenticationCredentialResolver ldapDelegatedClientAuthenticationCredentialResolver;

    @BeforeAll
    public static void bootstrap() throws Exception {
        ClientInfoHolder.setClientInfo(new ClientInfo(new MockHttpServletRequest()));
        @Cleanup
        val localhost = new LDAPConnection("localhost", LDAP_PORT, "cn=Directory Manager", "password");
        val ldif = IOUtils.toString(new ClassPathResource("ldap-pac4j.ldif").getInputStream(), StandardCharsets.UTF_8)
            .replace("$user", USER);
        LdapIntegrationTestsOperations.populateEntries(localhost,
            new ByteArrayInputStream(ldif.getBytes(StandardCharsets.UTF_8)),
            "ou=people,dc=example,dc=org");
    }

    @Test
    void verifyOperation() throws Exception {
        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        request.setAttribute(Credentials.class.getName(), "caspac4j");
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));
        RequestContextHolder.setRequestContext(context);
        ExternalContextHolder.setExternalContext(context.getExternalContext());
        val credentials = new TokenCredentials(USER);
        val clientCredential = new ClientCredential(credentials, "FacebookClient");
        assertTrue(ldapDelegatedClientAuthenticationCredentialResolver.supports(clientCredential));
        val results = ldapDelegatedClientAuthenticationCredentialResolver.resolve(context, clientCredential);
        assertEquals(1, results.size());
        val profile = results.get(0);
        assertEquals("caspac4j", profile.getLinkedId());
        assertEquals(USER, profile.getId());
        assertTrue(profile.getAttributes().containsKey("mail"));
        assertTrue(profile.getAttributes().containsKey("uid"));
    }
}
