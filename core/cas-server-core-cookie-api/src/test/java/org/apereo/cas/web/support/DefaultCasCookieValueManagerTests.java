package org.apereo.cas.web.support;

import org.apereo.cas.configuration.model.support.cookie.TicketGrantingCookieProperties;
import org.apereo.cas.util.crypto.CipherExecutor;
import org.apereo.cas.web.cookie.CookieValueManager;
import org.apereo.cas.web.support.mgmr.DefaultCasCookieValueManager;
import org.apereo.cas.web.support.mgmr.DefaultCookieSameSitePolicy;

import lombok.val;
import org.apereo.inspektr.common.web.ClientInfo;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Daniel Frett
 * @since 5.3.0
 */
@Tag("Cookie")
class DefaultCasCookieValueManagerTests {
    private static final String CLIENT_IP = "127.0.0.1";

    private static final String USER_AGENT = "Test-Client/1.0.0";

    private static final String VALUE = "cookieValue";

    private CookieValueManager cookieValueManager;

    @Mock
    private Cookie cookie;

    @BeforeEach
    public void initialize() {
        MockitoAnnotations.openMocks(this);

        val request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.setLocalAddr(CLIENT_IP);
        request.addHeader("User-Agent", USER_AGENT);
        ClientInfoHolder.setClientInfo(new ClientInfo(request));

        cookieValueManager = getCookieValueManager(new TicketGrantingCookieProperties());
    }

    @AfterEach
    public void cleanup() {
        ClientInfoHolder.clear();
    }

    @Test
    void verifySessionPinning() {
        val request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.setLocalAddr(CLIENT_IP);
        request.removeHeader("User-Agent");
        ClientInfoHolder.setClientInfo(new ClientInfo(request));

        val props = new TicketGrantingCookieProperties();
        assertThrows(IllegalStateException.class,
            () -> getCookieValueManager(props).buildCookieValue(VALUE, request));
        props.setPinToSession(false);
        assertNotNull(getCookieValueManager(props).buildCookieValue(VALUE, request));
    }

    @Test
    void verifySessionPinningAuthorizedOnFailure() {
        val request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.setLocalAddr(CLIENT_IP);
        request.addHeader("User-Agent", USER_AGENT);
        ClientInfoHolder.setClientInfo(new ClientInfo(request));

        val props = new TicketGrantingCookieProperties();
        props.setAllowedIpAddressesPattern("^19.*.3.1\\d\\d");
        val mgr = getCookieValueManager(props);
        var value = mgr.buildCookieValue(VALUE, request);
        assertNotNull(value);

        request.setRemoteAddr("198.127.3.155");
        ClientInfoHolder.setClientInfo(new ClientInfo(request));
        value = mgr.obtainCookieValue(value, request);
        assertNotNull(value);
    }

    @Test
    void verifyEncodeAndDecodeCookie() {
        val request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.setLocalAddr(CLIENT_IP);
        request.addHeader("User-Agent", USER_AGENT);
        val encoded = cookieValueManager.buildCookieValue(VALUE, request);
        assertEquals(String.join("@", VALUE, CLIENT_IP, USER_AGENT), encoded);

        when(cookie.getValue()).thenReturn(encoded);
        val decoded = cookieValueManager.obtainCookieValue(cookie, request);
        assertEquals(VALUE, decoded);
    }

    @Test
    void verifyNoPinning() {
        val props = new TicketGrantingCookieProperties();
        props.setPinToSession(false);
        val mgr = getCookieValueManager(props);
        assertEquals("something", mgr.obtainCookieValue("something", new MockHttpServletRequest()));
    }

    @Test
    void verifyBadValue() {
        val props = new TicketGrantingCookieProperties();
        val mgr = getCookieValueManager(props);
        assertThrows(InvalidCookieException.class, () -> mgr.obtainCookieValue("something", new MockHttpServletRequest()));
    }

    private static CookieValueManager getCookieValueManager(final TicketGrantingCookieProperties props) {
        return new DefaultCasCookieValueManager(CipherExecutor.noOp(), DefaultCookieSameSitePolicy.INSTANCE, props);
    }

    @Test
    void verifyBadCookie() {
        val props = new TicketGrantingCookieProperties();
        val mgr = getCookieValueManager(props);
        assertThrows(InvalidCookieException.class, () -> mgr.obtainCookieValue("something@1@", new MockHttpServletRequest()));
    }

    @Test
    void verifyBadIp() {
        val props = new TicketGrantingCookieProperties();
        val mgr = getCookieValueManager(props);
        assertThrows(InvalidCookieException.class, () -> mgr.obtainCookieValue("something@1@agent", new MockHttpServletRequest()));
    }

    @Test
    void verifyBadAgent() {
        val props = new TicketGrantingCookieProperties();
        val mgr = getCookieValueManager(props);
        assertThrows(InvalidCookieException.class,
            () -> mgr.obtainCookieValue("something@" + ClientInfoHolder.getClientInfo().getClientIpAddress() + "@agent", new MockHttpServletRequest()));
    }

    @Test
    void verifyMissingClientInfo() {
        val props = new TicketGrantingCookieProperties();
        val mgr = getCookieValueManager(props);
        ClientInfoHolder.clear();
        assertThrows(InvalidCookieException.class,
            () -> mgr.obtainCookieValue("something@" + CLIENT_IP + '@' + USER_AGENT, new MockHttpServletRequest()));
    }

}
