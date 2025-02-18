package org.apereo.cas;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link CasSpringBootAdminServerBannerTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Tag("WebApp")
class CasSpringBootAdminServerBannerTests {
    @Test
    void verifyOperation() throws Exception {
        val banner = new CasSpringBootAdminServerBanner();
        assertNotNull(banner.getTitle());

        val environment = new MockEnvironment();
        try (val os = new ByteArrayOutputStream(); val out = new PrintStream(os)) {
            assertDoesNotThrow(() -> banner.printBanner(environment, this.getClass(), out));
            assertNotNull(os.toByteArray());
        }
    }
}
