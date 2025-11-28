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
- Database: MySQL / TiDB (see `docs/MYSQL_SETUP.md`).
- Default schema is created from `src/main/resources/schema.sql` on startup (`spring.sql.init.mode=always`).
- Update `spring.datasource.*` in `src/main/resources/application.yml` to match your local credentials.
- Configure AI provider credentials via environment variables:
  - `OPENAI_API_KEY`
  - `OPENAI_BASE_URL` (optional, defaults to `https://api.openai.com`)

## 更新日志
- 2025-11-28：将持久层从 Spring Data JPA + H2 迁移为 MyBatis-Plus + MySQL/TiDB，新增启动时自动建表脚本 `schema.sql` 与数据库配置文档 `docs/MYSQL_SETUP.md`，并清理 `application.yml` 中的数据库账号密码（改为空字符串占位）。
