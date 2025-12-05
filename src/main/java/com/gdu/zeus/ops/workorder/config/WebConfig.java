package com.gdu.zeus.ops.workorder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Knife4j/Swagger UI 文档资源
        registry.addResourceHandler("/doc.html", "/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        // WebJars 资源（Swagger UI 依赖）
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // Swagger/OpenAPI 配置和 JSON 资源
        registry.addResourceHandler("/v3/api-docs/**", "/swagger-resources/**")
                .addResourceLocations("classpath:/META-INF/resources/");

        // 前端静态资源
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
    
