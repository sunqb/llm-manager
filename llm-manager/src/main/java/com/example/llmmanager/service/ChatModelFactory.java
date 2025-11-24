package com.example.llmmanager.service;

import com.example.llmmanager.entity.Channel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatModelFactory {

    private final Map<Long, ChatModel> clientCache = new ConcurrentHashMap<>();

    public ChatModel createChatModel(Channel channel) {
        // In a real app, handle cache invalidation when channel is updated
        return clientCache.computeIfAbsent(channel.getId(), id -> buildClient(channel));
    }

    private ChatModel buildClient(Channel channel) {
        switch (channel.getType()) {
            case OPENAI:
            case OLLAMA: // Treat Ollama as OpenAI-compatible for now
                return createOpenAiClient(channel);
            // Add other cases like AZURE here
            default:
                throw new IllegalArgumentException("Unsupported provider: " + channel.getType());
        }
    }

    private ChatModel createOpenAiClient(Channel channel) {
        String apiKey = channel.getApiKey();
        String baseUrl = (channel.getBaseUrl() != null && !channel.getBaseUrl().isEmpty()) 
                         ? channel.getBaseUrl() 
                         : "https://api.openai.com";
        
        // Fix: Spring AI OpenAiApi appends /v1 automatically, so we must remove it from user input to avoid /v1/v1
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Spring AI 1.0.0-M1 OpenAiApi constructor typically requires apiKey.
        // Some versions allow baseUrl.
        // If baseUrl is customized, we might need a specific constructor or builder.
        // For 1.0.0-M1: new OpenAiApi(baseUrl, apiKey) is valid.
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        
        // Create the ChatModel
        return new OpenAiChatModel(openAiApi);
    }
}
