package com.example.llmmanager.service;

import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.entity.LlmModel;
import com.example.llmmanager.repository.LlmModelRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LlmExecutionService {

    private final LlmModelRepository modelRepository;
    private final ChatModelFactory chatModelFactory;

    public LlmExecutionService(LlmModelRepository modelRepository, ChatModelFactory chatModelFactory) {
        this.modelRepository = modelRepository;
        this.chatModelFactory = chatModelFactory;
    }

    public String chat(Long modelId, String userMessage) {
        LlmModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("Model not found"));
        
        return executeRequest(model, userMessage, null);
    }

    public String chatWithAgent(Agent agent, String userMessage) {
        LlmModel model = agent.getLlmModel();
        // Prefer agent temp override, else model default
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();
        
        return executeRequest(model, userMessage, agent.getSystemPrompt(), temp);
    }

    private String executeRequest(LlmModel model, String userMessage, String systemPrompt) {
        return executeRequest(model, userMessage, systemPrompt, model.getTemperature());
    }

    private String executeRequest(LlmModel model, String userMessageStr, String systemPromptStr, Double temperature) {
        ChatModel chatModel = chatModelFactory.createChatModel(model.getChannel());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model.getModelIdentifier())
                .withTemperature(temperature.floatValue()) // Convert Double to Float for Spring AI 1.0.0-M1
                .build();

        List<Message> messages = new ArrayList<>();
        if (systemPromptStr != null && !systemPromptStr.isEmpty()) {
            messages.add(new SystemMessage(systemPromptStr));
        }
        messages.add(new UserMessage(userMessageStr));

        Prompt prompt = new Prompt(messages, options);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }

    public String chatWithTemplate(Long modelId, String templateContent, Map<String, Object> variables) {
        PromptTemplate template = new PromptTemplate(templateContent);
        String message = template.render(variables);
        return chat(modelId, message);
    }

    // 流式聊天方法
    public Flux<ChatResponse> streamChat(Long modelId, String userMessage) {
        LlmModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("Model not found"));

        return executeStreamRequest(model, userMessage, null);
    }

    public Flux<ChatResponse> streamChatWithAgent(Agent agent, String userMessage) {
        LlmModel model = agent.getLlmModel();
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();

        return executeStreamRequest(model, userMessage, agent.getSystemPrompt(), temp);
    }

    private Flux<ChatResponse> executeStreamRequest(LlmModel model, String userMessage, String systemPrompt) {
        return executeStreamRequest(model, userMessage, systemPrompt, model.getTemperature());
    }

    private Flux<ChatResponse> executeStreamRequest(LlmModel model, String userMessageStr, String systemPromptStr, Double temperature) {
        ChatModel chatModel = chatModelFactory.createChatModel(model.getChannel());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model.getModelIdentifier())
                .withTemperature(temperature.floatValue())
                .build();

        List<Message> messages = new ArrayList<>();
        if (systemPromptStr != null && !systemPromptStr.isEmpty()) {
            messages.add(new SystemMessage(systemPromptStr));
        }
        messages.add(new UserMessage(userMessageStr));

        Prompt prompt = new Prompt(messages, options);

        System.out.println("[LLM Stream] 开始流式请求: " + model.getModelIdentifier());

        // 使用 stream 方法返回 Flux，并添加操作符确保实时推送
        return chatModel.stream(prompt)
                .doOnNext(response -> {
                    if (response != null && response.getResult() != null) {
                        System.out.println("[LLM Stream] 收到数据块");
                    }
                })
                .doOnComplete(() -> System.out.println("[LLM Stream] 流式请求完成"))
                .doOnError(error -> System.err.println("[LLM Stream] 错误: " + error.getMessage()));
    }
}


