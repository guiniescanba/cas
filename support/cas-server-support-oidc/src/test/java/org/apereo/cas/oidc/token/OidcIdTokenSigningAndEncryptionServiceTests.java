package org.apereo.cas.oidc.token;

import org.apereo.cas.oidc.AbstractOidcTests;
import org.apereo.cas.oidc.discovery.OidcServerDiscoverySettings;
import org.apereo.cas.oidc.issuer.OidcDefaultIssuerService;
import org.apereo.cas.support.oauth.OAuth20Constants;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link OidcIdTokenSigningAndEncryptionServiceTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@Tag("OIDC")
class OidcIdTokenSigningAndEncryptionServiceTests {
    @Nested
    @SuppressWarnings("ClassCanBeStatic")
    @TestPropertySource(properties = "cas.authn.oidc.jwks.file-system.jwks-file=classpath:multiple-keys.jwks")
    class KeystoreWithMultipleKeysTests extends AbstractOidcTests {
        @Test
        void verifyOperation() {
            val claims = getClaims();

            val es512 = getOidcRegisteredService("ES512");
            es512.setIdTokenSigningAlg("ES512");
            es512.setJwksKeyId("EC");
            es512.setEncryptIdToken(false);

            val rs256 = getOidcRegisteredService("RS256");
            rs256.setIdTokenSigningAlg("RS256");
            rs256.setJwksKeyId("RSA");
            rs256.setEncryptIdToken(false);

            val result1 = oidcTokenSigningAndEncryptionService.encode(es512, claims);
            assertNotNull(result1);

            val result2 = oidcTokenSigningAndEncryptionService.encode(rs256, claims);
            assertNotNull(result2);

            val result3 = oidcTokenSigningAndEncryptionService.encode(getOidcRegisteredService(), claims);
            assertNotNull(result3);
        }
    }

    @Nested
    @SuppressWarnings("ClassCanBeStatic")
    @TestPropertySource(properties = {
        "cas.authn.oidc.discovery.id-token-signing-alg-values-supported=RS256,RS384,RS512",
        "cas.authn.oidc.discovery.id-token-encryption-encoding-values-supported=A128CBC-HS256,A192CBC-HS384,A256CBC-HS512,A128GCM,A192GCM,A256GCM"
    })
    class DefaultTests extends AbstractOidcTests {
        @Test
        void verifyOperation() {
            val claims = getClaims();
            val result = oidcTokenSigningAndEncryptionService.encode(getOidcRegisteredService(), claims);
            assertNotNull(result);
        }

        @Test
        void verifyWrongType() {
            assertFalse(oidcTokenSigningAndEncryptionService.shouldEncryptToken(getOAuthRegisteredService("1", "http://localhost/cas")));
            assertFalse(oidcTokenSigningAndEncryptionService.shouldSignToken(getOAuthRegisteredService("1", "http://localhost/cas")));
        }

        @Test
        void verifySkipSigning() {
            val oidcRegisteredService = getOidcRegisteredService(false, false);
            val result = oidcTokenSigningAndEncryptionService.shouldSignToken(oidcRegisteredService);
            assertFalse(result);
        }

        @Test
        void verifyValidationOperation() {
            val claims = getClaims();
            val oidcRegisteredService = getOidcRegisteredService(true, false);
            val result = oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims);
            val jwt = oidcTokenSigningAndEncryptionService.decode(result, Optional.of(oidcRegisteredService));
            assertNotNull(jwt);
        }

        @Test
        void verifyDecodingFailureBadToken() {
            val oidcRegisteredService = getOidcRegisteredService(true, false);
            assertThrows(IllegalArgumentException.class,
                () -> oidcTokenSigningAndEncryptionService.decode("bad-token", Optional.of(oidcRegisteredService)));
        }

        @Test
        void verifyDecodingFailureNoIssuer() {
            val oidcRegisteredService = getOidcRegisteredService(true, false);
            val claims = getClaims();
            claims.setIssuer(StringUtils.EMPTY);
            val result = oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims);
            assertThrows(IllegalArgumentException.class,
                () -> oidcTokenSigningAndEncryptionService.decode(result, Optional.of(oidcRegisteredService)));
        }

        @Test
        void verifyDecodingFailureBadIssuer() {
            val oidcRegisteredService = getOidcRegisteredService(true, false);
            val claims = getClaims();
            claims.setIssuer("bad-issuer");
            val result2 = oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims);
            assertThrows(IllegalArgumentException.class,
                () -> oidcTokenSigningAndEncryptionService.decode(result2, Optional.of(oidcRegisteredService)));
        }

        @Test
        void verifyDecodingFailureBadClient() {
            val oidcRegisteredService = getOidcRegisteredService(true, false);
            val claims = getClaims();
            claims.setStringClaim(OAuth20Constants.CLIENT_ID, StringUtils.EMPTY);
            val result3 = oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims);
            assertThrows(IllegalArgumentException.class,
                () -> oidcTokenSigningAndEncryptionService.decode(result3, Optional.of(oidcRegisteredService)));
        }

        @Test
        void verifyNoneNotSupported() {
            val claims = getClaims();
            val oidcRegisteredService = getOidcRegisteredService();
            oidcRegisteredService.setIdTokenSigningAlg(AlgorithmIdentifiers.NONE);
            assertThrows(IllegalArgumentException.class, () -> oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims));
            oidcRegisteredService.setIdTokenSigningAlg(AlgorithmIdentifiers.RSA_USING_SHA256);
            oidcRegisteredService.setIdTokenEncryptionAlg(AlgorithmIdentifiers.NONE);
            assertThrows(IllegalArgumentException.class, () -> oidcTokenSigningAndEncryptionService.encode(oidcRegisteredService, claims));
        }

        @Test
        void verifyNoneSupported() {
            val discovery = new OidcServerDiscoverySettings(casProperties.getAuthn().getOidc().getCore().getIssuer());
            discovery.setIdTokenSigningAlgValuesSupported(Set.of(AlgorithmIdentifiers.NONE));
            discovery.setIdTokenEncryptionAlgValuesSupported(Set.of(AlgorithmIdentifiers.NONE));
            val service = new OidcIdTokenSigningAndEncryptionService(oidcDefaultJsonWebKeystoreCache,
                oidcServiceJsonWebKeystoreCache,
                new OidcDefaultIssuerService(casProperties.getAuthn().getOidc()),
                discovery);

            val claims = getClaims();
            val oidcRegisteredService = getOidcRegisteredService();
            oidcRegisteredService.setIdTokenSigningAlg(AlgorithmIdentifiers.NONE);
            oidcRegisteredService.setIdTokenEncryptionAlg(AlgorithmIdentifiers.NONE);

            val result = service.encode(oidcRegisteredService, claims);
            assertNotNull(result);
        }
    }
}
