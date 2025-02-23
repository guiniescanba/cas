package org.apereo.cas.support.saml.web.idp.profile.sso;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.DefaultAuthenticationBuilder;
import org.apereo.cas.authentication.credential.BasicIdentifiableCredential;
import org.apereo.cas.authentication.credential.UsernamePasswordCredential;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.RegisteredServiceAccessStrategyUtils;
import org.apereo.cas.services.RegisteredServiceAttributeReleasePolicyContext;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.SamlException;
import org.apereo.cas.support.saml.SamlProtocolConstants;
import org.apereo.cas.support.saml.SamlUtils;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.SamlRegisteredServiceServiceProviderMetadataFacade;
import org.apereo.cas.support.saml.services.idp.metadata.cache.SamlRegisteredServiceCachingMetadataResolver;
import org.apereo.cas.support.saml.util.AbstractSaml20ObjectBuilder;
import org.apereo.cas.support.saml.web.idp.profile.builders.AuthenticatedAssertionContext;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileBuilderContext;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileObjectBuilder;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.LoggingUtils;
import org.apereo.cas.web.BaseCasActuatorEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jooq.lambda.Unchecked;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.context.ScratchContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.impl.AuthnRequestBuilder;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * This is {@link SSOSamlIdPPostProfileHandlerEndpoint}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Slf4j
@RestControllerEndpoint(id = "samlPostProfileResponse", enableByDefault = false)
public class SSOSamlIdPPostProfileHandlerEndpoint extends BaseCasActuatorEndpoint {

    private final ServicesManager servicesManager;

    private final AuthenticationSystemSupport authenticationSystemSupport;

    private final ServiceFactory<WebApplicationService> serviceFactory;

    private final PrincipalFactory principalFactory;

    private final SamlProfileObjectBuilder<? extends SAMLObject> responseBuilder;

    private final SamlRegisteredServiceCachingMetadataResolver defaultSamlRegisteredServiceCachingMetadataResolver;

    private final AbstractSaml20ObjectBuilder saml20ObjectBuilder;

    private final PrincipalResolver principalResolver;

    public SSOSamlIdPPostProfileHandlerEndpoint(final CasConfigurationProperties casProperties,
                                                final ServicesManager servicesManager,
                                                final AuthenticationSystemSupport authenticationSystemSupport,
                                                final ServiceFactory<WebApplicationService> serviceFactory,
                                                final PrincipalFactory principalFactory,
                                                final SamlProfileObjectBuilder<? extends SAMLObject> responseBuilder,
                                                final SamlRegisteredServiceCachingMetadataResolver cachingMetadataResolver,
                                                final AbstractSaml20ObjectBuilder saml20ObjectBuilder,
                                                final PrincipalResolver principalResolver) {
        super(casProperties);
        this.servicesManager = servicesManager;
        this.authenticationSystemSupport = authenticationSystemSupport;
        this.serviceFactory = serviceFactory;
        this.principalFactory = principalFactory;
        this.responseBuilder = responseBuilder;
        this.defaultSamlRegisteredServiceCachingMetadataResolver = cachingMetadataResolver;
        this.saml20ObjectBuilder = saml20ObjectBuilder;
        this.principalResolver = principalResolver;
    }

    /**
     * Produce response entity.
     *
     * @param request     the request
     * @param response    the response
     * @param samlRequest the saml request
     * @return the response entity
     */
    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Operation(summary = "Produce SAML2 response entity", parameters = {
        @Parameter(name = "username", required = true),
        @Parameter(name = "password", required = true),
        @Parameter(name = SamlProtocolConstants.PARAMETER_ENTITY_ID, required = true),
        @Parameter(name = "encrypt")
    })
    public ResponseEntity<Object> produceGet(final HttpServletRequest request, final HttpServletResponse response,
                                             @ModelAttribute
                                             final SamlRequest samlRequest) {
        return produce(request, response, samlRequest);
    }

    /**
     * Produce response entity.
     *
     * @param request     the request
     * @param response    the response
     * @param samlRequest the saml request
     * @return the response entity
     */
    @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Operation(summary = "Produce SAML2 response entity", parameters = {
        @Parameter(name = "username", required = true),
        @Parameter(name = "password", required = false),
        @Parameter(name = SamlProtocolConstants.PARAMETER_ENTITY_ID, required = true),
        @Parameter(name = "encrypt")
    })
    public ResponseEntity<Object> producePost(final HttpServletRequest request,
                                              final HttpServletResponse response,
                                              @ModelAttribute
                                              final SamlRequest samlRequest) {
        return produce(request, response, samlRequest);
    }

    private ResponseEntity<Object> produce(final HttpServletRequest request,
                                           final HttpServletResponse response,
                                           final SamlRequest samlRequest) {
        try {
            val selectedService = serviceFactory.createService(samlRequest.getEntityId());
            val registeredService = servicesManager.findServiceBy(selectedService, SamlRegisteredService.class);
            RegisteredServiceAccessStrategyUtils.ensureServiceAccessIsAllowed(selectedService, registeredService);

            val loadedService = (SamlRegisteredService) BeanUtils.cloneBean(registeredService);
            loadedService.setEncryptAssertions(samlRequest.isEncrypt());
            loadedService.setEncryptAttributes(samlRequest.isEncrypt());

            val authnRequest = new AuthnRequestBuilder().buildObject();
            authnRequest.setIssuer(saml20ObjectBuilder.newIssuer(samlRequest.getEntityId()));

            return SamlRegisteredServiceServiceProviderMetadataFacade.get(
                    defaultSamlRegisteredServiceCachingMetadataResolver, loadedService, samlRequest.getEntityId())
                .map(Unchecked.function(adaptor -> {
                    val messageContext = new MessageContext();
                    val scratch = messageContext.getSubcontext(ScratchContext.class, true);
                    val map = (Map) Objects.requireNonNull(scratch).getMap();
                    map.put(SamlProtocolConstants.PARAMETER_ENCODE_RESPONSE, Boolean.FALSE);
                    val assertion = getAssertion(samlRequest);
                    val buildContext = SamlProfileBuilderContext.builder()
                        .samlRequest(authnRequest)
                        .httpRequest(request)
                        .httpResponse(response)
                        .authenticatedAssertion(Optional.of(assertion))
                        .registeredService(loadedService)
                        .adaptor(adaptor)
                        .binding(SAMLConstants.SAML2_POST_BINDING_URI)
                        .messageContext(messageContext)
                        .build();
                    val object = responseBuilder.build(buildContext);
                    val encoded = SamlUtils.transformSamlObject(saml20ObjectBuilder.getOpenSamlConfigBean(), object, true).toString();
                    return new ResponseEntity<Object>(encoded, HttpStatus.OK);
                }))
                .orElseThrow(() -> new SamlException("Unable to locate " + samlRequest.getEntityId()));
        } catch (final Exception e) {
            LoggingUtils.error(LOGGER, e);
            return new ResponseEntity<>(StringEscapeUtils.escapeHtml4(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    private AuthenticatedAssertionContext getAssertion(final SamlRequest samlRequest) {
        val selectedService = serviceFactory.createService(samlRequest.getEntityId());
        val registeredService = servicesManager.findServiceBy(selectedService, SamlRegisteredService.class);

        val authentication = authenticateRequest(samlRequest, selectedService);
        val context = RegisteredServiceAttributeReleasePolicyContext.builder()
            .registeredService(registeredService)
            .service(selectedService)
            .principal(authentication.getPrincipal())
            .build();
        val attributesToRelease = registeredService.getAttributeReleasePolicy().getAttributes(context);
        val builder = DefaultAuthenticationBuilder.of(authentication.getPrincipal(), principalFactory, attributesToRelease,
            selectedService, registeredService, authentication);

        val finalAuthentication = builder.build();
        val authnPrincipal = finalAuthentication.getPrincipal();
        return AuthenticatedAssertionContext.builder()
            .name(authnPrincipal.getId())
            .attributes(CollectionUtils.merge(authnPrincipal.getAttributes(), finalAuthentication.getAttributes()))
            .build();
    }

    private Authentication authenticateRequest(final SamlRequest samlRequest, final WebApplicationService selectedService) {
        if (StringUtils.isNotBlank(samlRequest.getPassword())) {
            val credential = new UsernamePasswordCredential(samlRequest.getUsername(), samlRequest.getPassword());
            val result = authenticationSystemSupport.finalizeAuthenticationTransaction(selectedService, credential);
            return result.getAuthentication();
        }
        val principal = principalResolver.resolve(new BasicIdentifiableCredential(samlRequest.getUsername()),
            Optional.of(principalFactory.createPrincipal(samlRequest.getUsername())),
            Optional.empty(), Optional.of(selectedService));
        return DefaultAuthenticationBuilder.newInstance().setPrincipal(principal).build();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @With
    public static class SamlRequest implements Serializable {
        @Serial
        private static final long serialVersionUID = 9132411807103771828L;

        private String username;

        private String password;

        private String entityId;

        private boolean encrypt;
    }
}
