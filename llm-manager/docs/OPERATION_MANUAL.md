# LLM Manager System - Operation Manual

This document describes how to setup, configure, and use the LLM Manager System.

## 1. Architecture Overview

The system consists of two parts:
1.  **Backend**: A Spring Boot application (Port 8080) utilizing Spring AI to manage LLM connections.
2.  **Frontend**: A Vue.js application (Port 5173) providing the management interface.

## 2. Quick Start

### Backend Setup
1.  Navigate to `llm-manager`.
2.  Run `mvn spring-boot:run`.
3.  The API will be available at `http://localhost:8080/api`.

### Frontend Setup
1.  Navigate to `llm-manager-ui`.
2.  Run `npm install` (if not done).
3.  Run `npm run dev`.
4.  Open `http://localhost:5173` in your browser.

## 3. Key Concepts & Usage

### 3.1 Channels (Providers)
Channels represent your connection to an AI Provider.
-   **Name**: A friendly name (e.g., "My Personal OpenAI").
-   **Type**: Currently supports `OPENAI`. (Ollama/Azure placeholders exist).
-   **API Key**: Your secret key from the provider (sk-...).
-   **Base URL**: Optional. Use this to proxy requests (e.g., `https://api.openai-proxy.com` or local Ollama endpoints).

**How to use**:
1.  Go to **Channels** tab.
2.  Click "Add Channel".
3.  Enter your OpenAI API Key.

### 3.2 Models
Models link a specific model ID (like `gpt-4`) to a configured Channel.
-   **Name**: Display name (e.g., "GPT-4 Production").
-   **Model ID**: The actual string required by the provider (e.g., `gpt-4-turbo`).
-   **Temperature**: Default creativity setting (0.0 - 1.0).

**How to use**:
1.  Go to **Models** tab.
2.  Select a Channel.
3.  Enter the Model ID.

### 3.3 Agents (Smart Bots)
Agents wrap a Model with a specific "Persona" (System Prompt). They are accessible via a public API.
-   **Slug**: A unique URL identifier (e.g., `customer-support-v1`).
-   **System Prompt**: Instructions defining the agent's behavior.

**How to use**:
1.  Go to **Agents** tab.
2.  Create an agent with a System Prompt like "You are a Python expert. Only reply with code."
3.  This agent is now callable via the External API using its slug.

### 3.4 Tokens (Security)
To use the External Agent API, clients need a Bearer Token.
1.  Go to **Tokens** tab.
2.  Generate a token for a client (e.g., "Mobile App").
3.  Copy the token (starts with `sk-`).

## 4. Playground & Testing
The **Playground** tab allows you to test both raw models and configured agents.
-   **Raw Model Mode**: Direct chat with a configured model. No token required (uses internal API).
-   **Agent Mode**: Simulates an external client. **You must paste a valid Token** generated in the Tokens tab to use this, as it hits the protected `/api/external` endpoint.

## 5. API Reference (For External Developers)

**Endpoint**: `POST http://localhost:8080/api/external/agents/{slug}/chat`

**Headers**:
-   `Content-Type: application/json`
-   `Authorization: Bearer <YOUR_TOKEN>`

**Body**:
```json
{
    "message": "Hello, can you help me?"
}
```

**Response**:
```json
{
    "response": "Sure! I am here to help..."
}
```
