package org.apereo.cas.oidc.services;

import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.support.oauth.OAuth20Constants;
import org.apereo.cas.support.oauth.services.OAuth20ServicesManagerRegisteredServiceLocator;
import org.apereo.cas.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.Ordered;

/**
 * This is {@link OidcServicesManagerRegisteredServiceLocator}.
 *
 * @author Misagh Moayyed
 * @since 6.3.0
 */
@Slf4j
public class OidcServicesManagerRegisteredServiceLocator extends OAuth20ServicesManagerRegisteredServiceLocator {
    /**
     * Execution order of this locator.
     */
    static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    public OidcServicesManagerRegisteredServiceLocator(final CasConfigurationProperties casProperties) {
        super(casProperties);

        setOrder(DEFAULT_ORDER);
        setRegisteredServiceFilter(
            (registeredService, service) -> {
                var match = supports(registeredService, service);
                if (match) {
                    val oidcService = (OidcRegisteredService) registeredService;
                    LOGGER.trace("Attempting to locate service [{}] via [{}]", service, oidcService);
                    val clientIdAttribute = service.getAttributes().get(OAuth20Constants.CLIENT_ID);
                    match = CollectionUtils.firstElement(clientIdAttribute)
                        .map(Object::toString)
                        .stream()
                        .anyMatch(clientId -> oidcService.getClientId().equals(clientId));
                }
                return match;
            });
    }

    @Override
    public boolean supports(final RegisteredService registeredService, final Service service) {
        return registeredService instanceof OidcRegisteredService && supportsInternal(registeredService, service);
    }

    @Override
    protected Class<? extends RegisteredService> getRegisteredServiceIndexedType() {
        return OidcRegisteredService.class;
    }
}

