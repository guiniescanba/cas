package org.apereo.cas.token.authentication;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.RegisteredServiceProperty.RegisteredServiceProperties;
import org.apereo.cas.services.UnauthorizedServiceException;
import org.apereo.cas.util.function.FunctionUtils;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * This is {@link TokenAuthenticationSecurity}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenAuthenticationSecurity {
    private RegisteredServiceSecurityConfiguration securityConfiguration;

    /**
     * Generate token for authentication.
     *
     * @param authentication the authentication
     * @return the string
     */
    public String generateTokenFor(final Authentication authentication) {
        val claims = new HashMap<String, Object>(authentication.getAttributes());
        claims.putAll(authentication.getPrincipal().getAttributes());
        claims.put("sub", authentication.getPrincipal().getId());
        return toGenerator().generate(claims);
    }

    /**
     * For registered service.
     *
     * @param service the service
     * @return the token authentication security
     */
    public static TokenAuthenticationSecurity forRegisteredService(final RegisteredService service) {
        val securityConfiguration = new RegisteredServiceSecurityConfiguration();
        val signingConfig = getSecretSignatureConfiguration(service);
        val encConfig = getSecretEncryptionConfiguration(service);
        securityConfiguration.setEncryptionConfiguration(encConfig);
        securityConfiguration.setSignatureConfiguration(signingConfig);
        return new TokenAuthenticationSecurity(securityConfiguration);
    }

    /**
     * To generator.
     *
     * @return the jwt generator
     */
    public JwtGenerator toGenerator() {
        val generator = new JwtGenerator();
        FunctionUtils.doIfNotNull(securityConfiguration.getSignatureConfiguration(), generator::setSignatureConfiguration);
        FunctionUtils.doIfNotNull(securityConfiguration.getEncryptionConfiguration(), generator::setEncryptionConfiguration);
        return generator;
    }

    /**
     * To authenticator.
     *
     * @return the jwt authenticator
     */
    public JwtAuthenticator toAuthenticator() {
        val authn = new JwtAuthenticator();
        FunctionUtils.doIfNotNull(securityConfiguration.getEncryptionConfiguration(), authn::setEncryptionConfiguration);
        FunctionUtils.doIfNotNull(securityConfiguration.getSignatureConfiguration(), authn::setSignatureConfiguration);
        return authn;
    }

    private static String getRegisteredServiceJwtProperty(final RegisteredService service,
                                                          final RegisteredServiceProperties propName) {
        if (service == null || !service.getAccessStrategy().isServiceAccessAllowed()) {
            LOGGER.debug("Service is not defined/found or its access is disabled in the registry");
            throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE);
        }
        if (propName.isAssignedTo(service)) {
            return propName.getPropertyValue(service).value();
        }
        LOGGER.trace("Service [{}] does not define a property [{}] in the registry", service.getServiceId(), propName);
        return null;
    }

    private static SecretSignatureConfiguration getSecretSignatureConfiguration(final RegisteredService service) {
        val signingSecret = getRegisteredServiceJwtSigningSecret(service);
        if (StringUtils.isNotBlank(signingSecret)) {
            val secretBytes = getSecretBytes(signingSecret, areSecretsBase64Encoded(service));
            val serviceSigningAlg = getRegisteredServiceJwtProperty(service, RegisteredServiceProperties.TOKEN_SECRET_SIGNING_ALG);
            val sets = new HashSet<Algorithm>(0);
            sets.addAll(JWSAlgorithm.Family.EC);
            sets.addAll(JWSAlgorithm.Family.HMAC_SHA);
            sets.addAll(JWSAlgorithm.Family.RSA);
            sets.addAll(JWSAlgorithm.Family.SIGNATURE);
            val signingSecretAlg = StringUtils.defaultString(serviceSigningAlg, JWSAlgorithm.HS256.getName());
            val signingAlg = findAlgorithmFamily(sets, signingSecretAlg, JWSAlgorithm.class);
            return new SecretSignatureConfiguration(secretBytes, signingAlg);
        }
        return null;
    }

    private static SecretEncryptionConfiguration getSecretEncryptionConfiguration(final RegisteredService service) {
        val encryptionSecret = getRegisteredServiceJwtEncryptionSecret(service);
        if (StringUtils.isNotBlank(encryptionSecret)) {
            val encSecretBytes = getSecretBytes(encryptionSecret, areSecretsBase64Encoded(service));
            val sets = new HashSet<Algorithm>(0);
            sets.addAll(JWEAlgorithm.Family.AES_GCM_KW);
            sets.addAll(JWEAlgorithm.Family.AES_KW);
            sets.addAll(JWEAlgorithm.Family.ASYMMETRIC);
            sets.addAll(JWEAlgorithm.Family.ECDH_ES);
            sets.addAll(JWEAlgorithm.Family.PBES2);
            sets.addAll(JWEAlgorithm.Family.RSA);
            sets.addAll(JWEAlgorithm.Family.SYMMETRIC);

            val encryptionAlg = getRegisteredServiceJwtProperty(service,
                RegisteredServiceProperties.TOKEN_SECRET_ENCRYPTION_ALG);

            val encryptionSecretAlg = StringUtils.defaultString(encryptionAlg, JWEAlgorithm.DIR.getName());
            val encAlg = findAlgorithmFamily(sets, encryptionSecretAlg, JWEAlgorithm.class);

            sets.clear();
            sets.addAll(EncryptionMethod.Family.AES_CBC_HMAC_SHA);
            sets.addAll(EncryptionMethod.Family.AES_GCM);

            val encryptionMethod = getRegisteredServiceJwtProperty(service, RegisteredServiceProperties.TOKEN_SECRET_ENCRYPTION_METHOD);
            val encryptionSecretMethod = StringUtils.defaultString(encryptionMethod, EncryptionMethod.A192CBC_HS384.getName());
            val encMethod = findAlgorithmFamily(sets, encryptionSecretMethod, EncryptionMethod.class);
            return new SecretEncryptionConfiguration(encSecretBytes, encAlg, encMethod);
        }
        return null;
    }

    private static String getRegisteredServiceJwtEncryptionSecret(final RegisteredService service) {
        return getRegisteredServiceJwtProperty(service, RegisteredServiceProperties.TOKEN_SECRET_ENCRYPTION);
    }

    private static String getRegisteredServiceJwtSigningSecret(final RegisteredService service) {
        return getRegisteredServiceJwtProperty(service, RegisteredServiceProperties.TOKEN_SECRET_SIGNING);
    }

    private static boolean areSecretsBase64Encoded(final RegisteredService service) {
        val secretIsBase64 = getRegisteredServiceJwtProperty(service, RegisteredServiceProperties.TOKEN_SECRETS_ARE_BASE64_ENCODED);
        return BooleanUtils.toBoolean(secretIsBase64);
    }

    private static <T extends Algorithm> T findAlgorithmFamily(final Set<Algorithm> family,
                                                               final String alg, final Class<T> clazz) {
        val result = family
            .stream()
            .filter(algorithm -> algorithm.getName().equalsIgnoreCase(alg))
            .findFirst();
        if (result.isPresent()) {
            val algorithm = result.get();
            if (!clazz.isAssignableFrom(algorithm.getClass())) {
                throw new ClassCastException("Result [%s is of type %s when we were expecting %s"
                    .formatted(algorithm, algorithm.getClass(), clazz));
            }
            return (T) algorithm;
        }
        throw new IllegalArgumentException("Unable to find algorithm " + alg);
    }

    private static byte[] getSecretBytes(final String secret, final boolean secretIsBase64Encoded) {
        return secretIsBase64Encoded ? new Base64(secret).decode() : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Data
    @Setter
    private static class RegisteredServiceSecurityConfiguration {
        private SignatureConfiguration signatureConfiguration;
        private EncryptionConfiguration encryptionConfiguration;
    }
}
