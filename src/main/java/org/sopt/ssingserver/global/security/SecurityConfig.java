package org.sopt.ssingserver.global.security;

import java.time.Clock;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProperties;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.auth.token.JjwtAccessTokenProvider;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AccessTokenProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AccessTokenProvider accessTokenProvider,
            SecurityAuthenticationEntryPoint securityAuthenticationEntryPoint,
            SecurityAccessDeniedHandler securityAccessDeniedHandler
    ) throws Exception {
        // Security 체인 내부 전용 필터
        AccessTokenAuthenticationFilter accessTokenAuthenticationFilter = new AccessTokenAuthenticationFilter(
                accessTokenProvider,
                securityAuthenticationEntryPoint
        );

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityAuthenticationEntryPoint)
                        .accessDeniedHandler(securityAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // Access Token 없는 인증 API
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/kakao", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        // TODO: 관리자 권한 세분화 시 DB 현재 role/status/소유자 기준의 후속 인가 추가
                        .requestMatchers("/api/v1/admin/**").hasRole(MemberRole.ADMIN.name())
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    AccessTokenProvider accessTokenProvider(
            AccessTokenProperties accessTokenProperties,
            Clock jwtClock
    ) {
        return new JjwtAccessTokenProvider(accessTokenProperties, jwtClock);
    }

    @Bean
    Clock jwtClock() {
        return Clock.systemUTC();
    }
}
