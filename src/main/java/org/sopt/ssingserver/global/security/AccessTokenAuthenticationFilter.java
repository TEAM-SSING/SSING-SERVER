package org.sopt.ssingserver.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AccessTokenProvider accessTokenProvider;
    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;

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
        if (!StringUtils.hasText(authorization)) {
            // 보호 URL 인가 단계
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return token;
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
