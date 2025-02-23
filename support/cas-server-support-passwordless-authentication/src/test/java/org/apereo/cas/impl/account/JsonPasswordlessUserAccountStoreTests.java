package org.apereo.cas.impl.account;

import org.apereo.cas.api.PasswordlessUserAccountStore;
import org.apereo.cas.impl.BasePasswordlessUserAccountStoreTests;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link JsonPasswordlessUserAccountStoreTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@TestPropertySource(properties = "cas.authn.passwordless.accounts.json.location=classpath:PasswordlessAccount.json")
@Tag("FileSystem")
class JsonPasswordlessUserAccountStoreTests extends BasePasswordlessUserAccountStoreTests {

    @Autowired
    @Qualifier(PasswordlessUserAccountStore.BEAN_NAME)
    private PasswordlessUserAccountStore passwordlessUserAccountStore;

    @Test
    void verifyAction() {
        passwordlessUserAccountStore.reload();
        val user = passwordlessUserAccountStore.findUser("casuser");
        assertTrue(user.isPresent());
    }
}
