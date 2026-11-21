package br.com.devlopes.smartbudget.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartBudgetOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Budget Spring AI API")
                        .version("1.0.0")
                        .description("""
                                API de Orçamento e Finanças Pessoais Inteligente construída com Java 21, \
                                Spring Boot 3 e Spring AI (ChatClient, Tool Calling, Speech-to-Text e Text-to-Speech).
                                """)
                        .contact(new Contact()
                                .name("GabrielVSL")
                                .url("https://github.com/GabrielVSL/finance-ai-service"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
