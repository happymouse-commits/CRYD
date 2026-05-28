package com.happymouse.cryd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Web配置 - 允许前端跨域访问 + SPA路由支持
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Vue Router SPA: forward all frontend routes to index.html
        registry.addViewController("/student/**").setViewName("forward:/index.html");
        registry.addViewController("/teacher/**").setViewName("forward:/index.html");
        registry.addViewController("/admin/**").setViewName("forward:/index.html");
        // Also handle direct route access without trailing slash
        registry.addViewController("/student").setViewName("forward:/index.html");
        registry.addViewController("/teacher").setViewName("forward:/index.html");
        registry.addViewController("/admin").setViewName("forward:/index.html");
        // Root path redirects to login page
        registry.addRedirectViewController("/", "/login.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(0);
    }
}