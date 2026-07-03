package org.sopt.ssingserver.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AccessTokenProvider accessTokenProvider;
    private final AuthTokenExtractor authTokenExtractor;
    private final SecurityFilterSkipMatcher securityFilterSkipMatcher;
    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // permitAll 이전 커스텀 필터 우회 경계
        return securityFilterSkipMatcher.shouldSkip(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Authorization 헤더 추출 경계
            String accessToken = resolveBearerToken(request);
            if (accessToken != null) {
                setAuthentication(accessToken);
            }
        } catch (AccessTokenException exception) {
            // Access Token 인증 실패 응답
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new AccessTokenAuthenticationException(exception.getErrorCode(), exception)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authTokenExtractor.extractNullableBearerToken(authorization);
    }

    private void setAuthentication(String accessToken) {
        AccessTokenClaims claims = accessTokenProvider.parseAccessToken(accessToken);
        AuthenticatedMember principal = new AuthenticatedMember(claims.memberId(), claims.role());

        // Spring Security 권한 형식 변환
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(ROLE_PREFIX + claims.role().name())
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );

        // SecurityContext 인증 객체 등록
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
