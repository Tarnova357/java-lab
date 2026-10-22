package com.example.java_lab.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class WebController {

    @GetMapping("/api/user")
    public Map<String, Object> getUserInfo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return Map.of("username", "anonymous", "roles", List.of());
        }

        String username = authentication.getName();
        String email = "";
        Set<String> roles = new HashSet<>();

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            username = oidcUser.getPreferredUsername() != null
                    ? oidcUser.getPreferredUsername()
                    : authentication.getName();
            email = oidcUser.getEmail() != null ? oidcUser.getEmail() : "";

            extractRoles(oidcUser.getClaims(), roles);
            if (oidcUser.getUserInfo() != null) {
                extractRoles(oidcUser.getUserInfo().getClaims(), roles);
            }
        } else if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            username = oauth2User.getAttribute("preferred_username") != null
                    ? oauth2User.getAttribute("preferred_username")
                    : authentication.getName();
            email = oauth2User.getAttribute("email") != null
                    ? oauth2User.getAttribute("email")
                    : "";
            extractRoles(oauth2User.getAttributes(), roles);
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            username = jwt.getClaimAsString("preferred_username") != null
                    ? jwt.getClaimAsString("preferred_username")
                    : authentication.getName();
            email = jwt.getClaimAsString("email") != null
                    ? jwt.getClaimAsString("email")
                    : "";
            extractRoles(jwt.getClaims(), roles);
        }

        for (GrantedAuthority auth : authentication.getAuthorities()) {
            if (!"ROLE_ANONYMOUS".equals(auth.getAuthority())) {
                roles.add(auth.getAuthority());
            }
        }

        return Map.of(
                "username", username,
                "email", email,
                "roles", new ArrayList<>(roles)
        );
    }

    @SuppressWarnings("unchecked")
    private void extractRoles(Map<String, Object> claims, Set<String> roles) {
        if (claims == null) return;

        if (claims.containsKey("realm_access")) {
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> rList = (List<String>) realmAccess.get("roles");
                for (String r : rList) {
                    roles.add("ROLE_" + r);
                }
            }
        }
    }

    @GetMapping("/perform-logout")
    public void performLogout(jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.sendRedirect("http://localhost:8081/realms/java-lab-realm/protocol/openid-connect/logout?post_logout_redirect_uri=http://localhost:8080/&client_id=spring-client");
    }
}