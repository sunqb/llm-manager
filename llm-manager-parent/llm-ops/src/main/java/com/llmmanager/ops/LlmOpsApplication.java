package com.llmmanager.ops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.llmmanager.ops",
        "com.llmmanager.service",
        "com.llmmanager.agent",
        "com.llmmanager.common"
})
@MapperScan({
        "com.llmmanager.service.core.mapper",
        "com.llmmanager.agent.storage.core.mapper"
})
public class LlmOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmOpsApplication.class, args);
    }
}
