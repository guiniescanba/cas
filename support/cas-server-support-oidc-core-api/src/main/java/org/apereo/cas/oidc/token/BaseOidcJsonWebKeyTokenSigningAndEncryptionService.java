package org.apereo.cas.oidc.token;

import org.apereo.cas.oidc.issuer.OidcIssuerService;
import org.apereo.cas.oidc.jwks.OidcJsonWebKeyCacheKey;
import org.apereo.cas.oidc.jwks.OidcJsonWebKeyUsage;
import org.apereo.cas.oidc.jwks.rotation.OidcJsonWebKeystoreRotationService;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.support.oauth.services.OAuthRegisteredService;
import org.apereo.cas.ticket.BaseTokenSigningAndEncryptionService;
import org.apereo.cas.util.EncodingUtils;
import org.apereo.cas.util.function.FunctionUtils;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTParser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.Unchecked;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwt.JwtClaims;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * This is {@link BaseOidcJsonWebKeyTokenSigningAndEncryptionService}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseOidcJsonWebKeyTokenSigningAndEncryptionService extends BaseTokenSigningAndEncryptionService {
    /**
     * The default keystore for OIDC tokens.
     */
    protected final LoadingCache<OidcJsonWebKeyCacheKey, Optional<JsonWebKeySet>> defaultJsonWebKeystoreCache;

    /**
     * The service keystore for OIDC tokens.
     */
    protected final LoadingCache<OidcJsonWebKeyCacheKey, Optional<JsonWebKeySet>> serviceJsonWebKeystoreCache;

    /**
     * Issuer service.
     */
    protected final OidcIssuerService issuerService;

    @Override
    public String encode(final OAuthRegisteredService service, final JwtClaims claims) {
        return FunctionUtils.doUnchecked(() -> {
            LOGGER.trace("Attempting to produce token generated for service [{}] with claims [{}]", service, claims.toJson());
            var innerJwt = signTokenIfNecessary(claims, service);
            if (shouldEncryptToken(service)) {
                innerJwt = encryptToken(service, innerJwt);
            }
            return innerJwt;
        });
    }

    @Override
    public JwtClaims decode(final String token, final Optional<OAuthRegisteredService> service) {
        return Unchecked.supplier(() -> {
            if (service.isPresent()) {
                val jwt = JWTParser.parse(token);
                if (jwt instanceof EncryptedJWT) {
                    val encryptionKey = getJsonWebKeyForEncryption(service.get());
                    val decoded = EncodingUtils.decryptJwtValue(encryptionKey.getPrivateKey(), token);
                    return super.decode(decoded, service);
                }
            }
            return super.decode(token, service);
        }, throwable -> {
            throw new IllegalArgumentException(throwable);
        }).get();
    }

    @Override
    public String resolveIssuer(final Optional<OAuthRegisteredService> service) {
        val filter = service
            .filter(OidcRegisteredService.class::isInstance)
            .map(OidcRegisteredService.class::cast)
            .stream()
            .findFirst();
        return issuerService.determineIssuer(filter);
    }

    protected abstract String encryptToken(OAuthRegisteredService svc, String token);

    @Override
    public PublicJsonWebKey getJsonWebKeySigningKey(final Optional<OAuthRegisteredService> serviceResult) {
        val servicePassed = serviceResult
            .filter(OidcRegisteredService.class::isInstance)
            .map(OidcRegisteredService.class::cast)
            .stream()
            .findFirst();
        val iss = issuerService.determineIssuer(servicePassed);
        LOGGER.trace("Using issuer [{}] to locate JWK signing key", iss);
        val jwks = defaultJsonWebKeystoreCache.get(new OidcJsonWebKeyCacheKey(iss, OidcJsonWebKeyUsage.SIGNING));
        return getJsonWebKeySigningKeyFrom(jwks, serviceResult);
    }

    protected PublicJsonWebKey getJsonWebKeySigningKeyFrom(final Optional<JsonWebKeySet> jwks,
                                                           final Optional<OAuthRegisteredService> serviceResult) {
        FunctionUtils.throwIf(jwks.isEmpty(),
            () -> new IllegalArgumentException("JSON web keystore is empty and contains no keys"));
        val jsonWebKeys = jwks.orElseThrow().getJsonWebKeys();
        LOGGER.trace("JSON web keystore contains [{}] key(s)", jsonWebKeys);

        val finalKey = serviceResult
            .filter(OidcRegisteredService.class::isInstance)
            .map(OidcRegisteredService.class::cast)
            .stream()
            .filter(oidcService -> StringUtils.isNotBlank(oidcService.getJwksKeyId()))
            .map(oidcService -> jsonWebKeys
                .stream()
                .filter(PublicJsonWebKey.class::isInstance)
                .filter(key -> StringUtils.equalsIgnoreCase(key.getKeyId(), oidcService.getJwksKeyId()))
                .map(PublicJsonWebKey.class::cast)
                .findFirst())
            .flatMap(Optional::stream)
            .findFirst();
        
        LOGGER.debug("Located key [{}] for service [{}]", finalKey, serviceResult);
        return finalKey.orElseGet(() -> (PublicJsonWebKey) jsonWebKeys.get(0));
    }

    /**
     * Gets json web key for encryption.
     *
     * @param svc the svc
     * @return the json web key for encryption
     */
    protected PublicJsonWebKey getJsonWebKeyForEncryption(final OAuthRegisteredService svc) {
        LOGGER.debug("Service [{}] is set to encrypt tokens", svc);
        val jwks = serviceJsonWebKeystoreCache.get(new OidcJsonWebKeyCacheKey(svc, OidcJsonWebKeyUsage.ENCRYPTION));
        if (Objects.requireNonNull(jwks).isEmpty()) {
            throw new IllegalArgumentException(
                "Service " + svc.getServiceId()
                    + " with client id " + svc.getClientId()
                    + " is configured to encrypt tokens, yet no JSON web key is available to handle encryption");
        }
        val jsonWebKey = jwks.get()
            .getJsonWebKeys()
            .stream()
            .filter(key -> OidcJsonWebKeystoreRotationService.JsonWebKeyLifecycleStates.getJsonWebKeyState(key).isCurrent())
            .min(Comparator.comparing(JsonWebKey::getKeyId))
            .orElseThrow(() -> new IllegalArgumentException("Cannot locate current JSON web key for encryption"));
        LOGGER.debug("Found JSON web key to encrypt the token: [{}]", jsonWebKey);
        Objects.requireNonNull(jsonWebKey.getKey(), "JSON web key used to encrypt the token has no associated public key");
        return (PublicJsonWebKey) jsonWebKey;
    }
}
