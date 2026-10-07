package com.example.gitbot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        /*
         * When the CSRF header is present, it may contain either:
         * 1. A masked XOR token (obtained via GET /api/auth/csrf by cross-origin SPA)
         * 2. A raw token (obtained via document.cookie by same-origin SPA / local client)
         *
         * Try resolving via the XOR delegate first. If unmasking fails (e.g. token is raw),
         * fall back to the raw header value via super.
         *
         * If no header is present, delegate handles request parameters.
         */
        if (StringUtils.hasText(headerValue)) {
            String resolved = this.delegate.resolveCsrfTokenValue(request, csrfToken);
            return (resolved != null) ? resolved : super.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
