package org.apereo.cas.support.saml.web.idp.profile.builders.enc.encoder.sso;

import org.apereo.cas.mock.MockTicketGrantingTicket;
import org.apereo.cas.support.saml.BaseSamlIdPConfigurationTests;
import org.apereo.cas.support.saml.services.idp.metadata.SamlRegisteredServiceServiceProviderMetadataFacade;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link SamlResponseArtifactEncoderTests}.
 *
 * @author Misagh Moayyed
 * @since 6.3.0
 */
@Tag("SAML2")
@TestPropertySource(properties = "cas.tgc.crypto.enabled=false")
class SamlResponseArtifactEncoderTests extends BaseSamlIdPConfigurationTests {
    @Test
    void verifyOperation() throws Exception {
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();

        val registeredService = getSamlRegisteredServiceForTestShib();
        val authnRequest = getAuthnRequestFor(registeredService);
        val adaptor = SamlRegisteredServiceServiceProviderMetadataFacade.get(samlRegisteredServiceCachingMetadataResolver, registeredService, authnRequest).get();
        val encoder = new SamlResponseArtifactEncoder(velocityEngine, adaptor, request, response, samlArtifactMap);
        assertEquals(SAMLConstants.SAML2_ARTIFACT_BINDING_URI, encoder.getBinding());

        val tgt = new MockTicketGrantingTicket("casuser");
        ticketRegistry.addTicket(tgt);
        ticketGrantingTicketCookieGenerator.addCookie(response, tgt.getId());
        request.setCookies(response.getCookies());

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        
        val samlResponse = samlProfileSamlResponseBuilder.newSamlObject(Response.class);
        val issuer = samlProfileSamlResponseBuilder.newSamlObject(Issuer.class);
        issuer.setValue("cas");
        samlResponse.setIssuer(issuer);
        assertNotNull(encoder.encode(authnRequest, samlResponse, "relay-state", new MessageContext()));
    }

}
