package org.sopt.ssingserver.global.security;

import java.time.Clock;
import org.sopt.ssingserver.domain.auth.token.JwtProperties;
import org.sopt.ssingserver.domain.auth.token.JwtTokenProvider;
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
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) throws Exception {
        // 서블릿 필터 자동 등록과 겹치지 않도록 Security 체인 안에서만 생성한다.
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                jwtAuthenticationEntryPoint
        );

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // 로그인/재발급은 Access Token 없이 호출된다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/kakao", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        // TODO: hasRole은 1차 필터다. 후속 인가 계층에서 DB 현재 role/status/소유자로 최종 판단한다.
                        .requestMatchers("/api/v1/admin/**").hasRole(MemberRole.ADMIN.name())
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtTokenProvider jwtTokenProvider(
            JwtProperties jwtProperties,
            Clock jwtClock
    ) {
        return new JwtTokenProvider(jwtProperties, jwtClock);
    }

    @Bean
    Clock jwtClock() {
        return Clock.systemUTC();
    }
}
