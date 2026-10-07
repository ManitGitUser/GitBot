package com.example.gitbot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

class SpaCsrfTokenRequestHandlerTest {

    private SpaCsrfTokenRequestHandler handler;
    private CsrfToken rawCsrfToken;

    @BeforeEach
    void setUp() {
        handler = new SpaCsrfTokenRequestHandler();
        rawCsrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "7902d142-c96f-4896-bde6-839db4d2a4a6");
    }

    @Test
    @DisplayName("Resolves masked XOR token supplied in X-XSRF-TOKEN header to raw token value")
    void resolveCsrfTokenValue_maskedTokenInHeader_returnsRawToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 1. Let handler handle the token so that an XOR-masked token is produced (as done by GET /api/auth/csrf)
        handler.handle(request, response, () -> rawCsrfToken);
        CsrfToken deferredToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        assertThat(deferredToken).isNotNull();
        String maskedToken = deferredToken.getToken();

        // Verify the masked token is different from the raw token
        assertThat(maskedToken).isNotEqualTo(rawCsrfToken.getToken());

        // 2. Put masked token in header
        request.addHeader("X-XSRF-TOKEN", maskedToken);

        // 3. Resolve against rawCsrfToken (as loaded from cookie by CookieCsrfTokenRepository)
        String resolved = handler.resolveCsrfTokenValue(request, rawCsrfToken);

        assertThat(resolved).isEqualTo(rawCsrfToken.getToken());
    }

    @Test
    @DisplayName("Resolves raw token supplied in X-XSRF-TOKEN header directly")
    void resolveCsrfTokenValue_rawTokenInHeader_returnsRawToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-XSRF-TOKEN", rawCsrfToken.getToken());

        String resolved = handler.resolveCsrfTokenValue(request, rawCsrfToken);

        assertThat(resolved).isEqualTo(rawCsrfToken.getToken());
    }

    @Test
    @DisplayName("Resolves masked token from request parameter when header is absent")
    void resolveCsrfTokenValue_noHeader_resolvesFromParameterUsingDelegate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, () -> rawCsrfToken);
        CsrfToken deferredToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String maskedToken = deferredToken.getToken();

        request.setParameter("_csrf", maskedToken);

        String resolved = handler.resolveCsrfTokenValue(request, rawCsrfToken);

        assertThat(resolved).isEqualTo(rawCsrfToken.getToken());
    }

    @Test
    @DisplayName("Returns null when neither header nor parameter is provided")
    void resolveCsrfTokenValue_noHeaderNoParameter_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String resolved = handler.resolveCsrfTokenValue(request, rawCsrfToken);

        assertThat(resolved).isNull();
    }
}
