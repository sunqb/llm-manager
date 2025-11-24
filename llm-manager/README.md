# LLM Manager

A Spring Boot application to manage LLM Models, Prompts, and Channels (Providers), utilizing **Spring AI** for dynamic client instantiation.

## Requirements
- Java 17+
- Maven

## Architecture
- **Channels**: Configuration for AI Providers (OpenAI, Ollama, Azure, etc.) storing API keys and Base URLs.
- **Models**: Specific model definitions (e.g., "gpt-4", "llama3") linked to a Channel.
- **Prompts**: Stored templates for reuse.
- **Chat**: Dynamic execution of prompts against models.

## Key Features
- **Dynamic Client Factory**: `ChatModelFactory` creates Spring AI clients at runtime based on database configurations, allowing you to switch between multiple OpenAI accounts or providers on the fly without restarting.

## API Endpoints

### Channels
- `GET /api/channels`
- `POST /api/channels`

### Models
- `GET /api/models`
- `POST /api/models`

### Chat
- `POST /api/chat/{modelId}` - Send a raw message to a model.
- `POST /api/chat/{modelId}/template` - Use a prompt template.

## Running
```bash
mvn spring-boot:run
```
Ensure you have set up a Channel via API before chatting.

## Configuration
Access H2 Console at `http://localhost:8080/h2-console`
JDBC URL: `jdbc:h2:mem:llmmanager`
User: `sa`
Password: (empty)
