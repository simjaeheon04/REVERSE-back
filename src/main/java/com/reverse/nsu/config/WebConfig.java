package com.reverse.nsu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "https://nsu-reverse.site",
                        "https://www.nsu-reverse.site",
                        "https://nsu-reverse.website",
                        "https://www.nsu-reverse.website"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 1. 기본적으로 인증이 필요한 경로들
                .addPathPatterns("/api/posts/**", "/api/user/**", "/api/admin/**")

                // 2. 인증 없이 누구나 접근 가능한(Public) 경로들 제외
                .excludePathPatterns(
                        "/api/auth/**",            // 로그인, 회원가입
                        "/api/posts/notices",      // 공지사항 목록 조회
                        "/api/posts/list/**",      // 일반 게시글 목록 조회
                        "/api/posts/detail/**",    // 게시글 상세 조회
                        "/api/posts/notices/*"     // [추가] 공지사항 단건 조회 (GET)
                );
    }
}
