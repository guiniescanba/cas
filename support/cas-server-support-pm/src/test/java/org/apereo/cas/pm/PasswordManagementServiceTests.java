package org.apereo.cas.pm;

import org.apereo.cas.services.RegisteredServiceTestUtils;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link PasswordManagementServiceTests}.
 *
 * @author Misagh Moayyed
 * @since 6.3.0
 */
@Tag("PasswordOps")
class PasswordManagementServiceTests {
    @Test
    void verifyOperation() {
        val service = new PasswordManagementService() {
        };
        assertFalse(service.change(new PasswordChangeRequest()));
        assertFalse(service.unlockAccount(RegisteredServiceTestUtils.getHttpBasedServiceCredentials()));
        assertNull(service.findEmail(PasswordManagementQuery.builder().email("user@example.org").build()));
        assertNull(service.findPhone(PasswordManagementQuery.builder().username("user").build()));
        assertNull(service.findUsername(PasswordManagementQuery.builder().email("user@example.org").build()));
        assertNull(service.createToken(PasswordManagementQuery.builder().username("user").build()));
        assertNull(service.parseToken("user"));
        assertNotNull(service.getSecurityQuestions(PasswordManagementQuery.builder().username("user").build()));
        assertDoesNotThrow(() -> service.updateSecurityQuestions(PasswordManagementQuery.builder().username("user").build()));
    }

}
