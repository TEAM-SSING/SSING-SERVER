package org.sopt.ssingserver.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.token.JwtClaims;
import org.sopt.ssingserver.domain.auth.token.JwtTokenException;
import org.sopt.ssingserver.domain.auth.token.JwtTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 인증 헤더 추출
            String accessToken = resolveBearerToken(request);
            if (accessToken != null) {
                setAuthentication(accessToken);
            }
        } catch (JwtTokenException exception) {
            // Security 필터 단계 인증 실패 처리
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new JwtAuthenticationFailureException(exception.getErrorCode(), exception)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            // 헤더가 없으면 URL 인가 단계에서 인증 필요 여부를 판단한다.
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return token;
    }

    private void setAuthentication(String accessToken) {
        JwtClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
        AuthenticatedMember principal = new AuthenticatedMember(claims.memberId(), claims.role());

        // Spring Security 권한 형식으로 변환
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
