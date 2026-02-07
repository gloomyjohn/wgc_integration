package com.jjy.wgcbackend.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WGC API Documentation")
                        .version("1.0")
                        .description("WGC项目API接口文档")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com")));
    }
}
