package com.llmmanager.openapi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.llmmanager.openapi",
        "com.llmmanager.service",
        "com.llmmanager.agent",
        "com.llmmanager.common"
})
@MapperScan({
        "com.llmmanager.service.core.mapper",
        "com.llmmanager.agent.storage.mapper"
})
public class LlmOpenApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmOpenApiApplication.class, args);
    }
}
