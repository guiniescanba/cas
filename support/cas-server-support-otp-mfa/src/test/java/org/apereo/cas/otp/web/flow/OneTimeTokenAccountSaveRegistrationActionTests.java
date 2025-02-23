package org.apereo.cas.otp.web.flow;

import org.apereo.cas.authentication.OneTimeTokenAccount;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.otp.repository.credentials.OneTimeTokenCredentialRepository;
import org.apereo.cas.services.RegisteredServiceTestUtils;
import org.apereo.cas.util.MockServletContext;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.support.WebUtils;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.webflow.context.ExternalContextHolder;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.execution.RequestContextHolder;
import org.springframework.webflow.test.MockRequestContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link OneTimeTokenAccountSaveRegistrationActionTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Tag("WebflowMfaActions")
class OneTimeTokenAccountSaveRegistrationActionTests {
    @Test
    void verifyCreateAccount() {
        val props = new CasConfigurationProperties();
        val account = OneTimeTokenAccount.builder()
            .username("casuser")
            .secretKey(UUID.randomUUID().toString())
            .validationCode(123456)
            .scratchCodes(List.of())
            .name(UUID.randomUUID().toString())
            .build();
        val repository = mock(OneTimeTokenCredentialRepository.class);
        val action = new OneTimeTokenAccountSaveRegistrationAction(repository, props);

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        request.addParameter(OneTimeTokenAccountSaveRegistrationAction.REQUEST_PARAMETER_ACCOUNT_NAME, "ExampleAccount");
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));
        RequestContextHolder.setRequestContext(context);
        ExternalContextHolder.setExternalContext(context.getExternalContext());
        WebUtils.putAuthentication(RegisteredServiceTestUtils.getAuthentication("casuser"), context);
        context.getFlowScope().put(OneTimeTokenAccountCreateRegistrationAction.FLOW_SCOPE_ATTR_ACCOUNT, account);
        assertEquals(CasWebflowConstants.TRANSITION_ID_SUCCESS, action.doExecute(context).getId());
    }

    @Test
    void verifyMissingAccount() {
        val props = new CasConfigurationProperties();
        val repository = mock(OneTimeTokenCredentialRepository.class);
        val action = new OneTimeTokenAccountSaveRegistrationAction(repository, props);

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));
        RequestContextHolder.setRequestContext(context);
        ExternalContextHolder.setExternalContext(context.getExternalContext());
        assertEquals(CasWebflowConstants.TRANSITION_ID_ERROR, action.doExecute(context).getId());
    }
}
