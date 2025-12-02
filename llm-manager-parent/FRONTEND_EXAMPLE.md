# 前端使用示例：会话历史管理

## 核心概念

**conversationId 由前端管理**：
- ✅ **前端决定**：何时开始新对话（生成新 UUID）
- ✅ **前端决定**：何时继续对话（复用 conversationId）
- ✅ **后端职责**：接收 conversationId 并加载/保存历史

## 完整示例代码

### 1. 基础流式对话组件

```javascript
// ChatComponent.vue
<template>
  <div class="chat-container">
    <!-- 对话历史 -->
    <div class="messages">
      <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
        <div class="content">{{ msg.content }}</div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <input
        v-model="userInput"
        @keyup.enter="sendMessage"
        placeholder="输入消息..."
      />
      <button @click="sendMessage">发送</button>
      <button @click="startNewConversation" class="new-chat">新对话</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const messages = ref([])
const userInput = ref('')
const conversationId = ref(null)
const modelId = ref(1)

// 生成不含"-"的 UUID
function generateConversationId() {
  return crypto.randomUUID().replace(/-/g, '')
}

// 页面加载时初始化
onMounted(() => {
  // 尝试从 localStorage 恢复会话
  const savedConversationId = localStorage.getItem('conversationId')
  const savedMessages = localStorage.getItem('messages')

  if (savedConversationId && savedMessages) {
    conversationId.value = savedConversationId
    messages.value = JSON.parse(savedMessages)
  } else {
    // 初始化新对话
    startNewConversation()
  }
})

// 开始新对话
function startNewConversation() {
  conversationId.value = generateConversationId()
  messages.value = []

  // 保存到 localStorage
  localStorage.setItem('conversationId', conversationId.value)
  localStorage.setItem('messages', JSON.stringify(messages.value))

  console.log('[新对话] conversationId:', conversationId.value)
}

// 发送消息
async function sendMessage() {
  if (!userInput.value.trim()) return

  const userMessage = userInput.value
  userInput.value = ''

  // 添加用户消息到界面
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: userMessage
  })

  // 准备接收助手回复
  const assistantMessage = {
    id: Date.now() + 1,
    role: 'assistant',
    content: ''
  }
  messages.value.push(assistantMessage)

  try {
    // 调用流式 API（带 conversationId）
    const url = `/api/chat/${modelId.value}/stream-flux?conversationId=${conversationId.value}`

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain'
      },
      body: userMessage
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)

          if (data === '[DONE]') {
            console.log('[流式完成]')
            break
          }

          try {
            const json = JSON.parse(data)
            const content = json.choices?.[0]?.delta?.content

            if (content) {
              assistantMessage.content += content
            }
          } catch (e) {
            // 忽略解析错误
          }
        }
      }
    }

    // 保存到 localStorage
    localStorage.setItem('messages', JSON.stringify(messages.value))

  } catch (error) {
    console.error('[发送失败]', error)
    assistantMessage.content = '发送失败: ' + error.message
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 20px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 20px;
}

.message {
  margin: 10px 0;
  padding: 10px;
  border-radius: 8px;
}

.message.user {
  background: #e3f2fd;
  text-align: right;
}

.message.assistant {
  background: #f5f5f5;
}

.input-area {
  display: flex;
  gap: 10px;
}

.input-area input {
  flex: 1;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.input-area button {
  padding: 10px 20px;
  background: #1976d2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.input-area button.new-chat {
  background: #4caf50;
}
</style>
```

## 2. 使用场景说明

### 场景 1：单次对话（不保存历史）

**不传 conversationId**：
```javascript
// 每次都是独立对话，不会加载历史
const response = await fetch(`/api/chat/1/stream-flux`, {
  method: 'POST',
  body: userMessage
})
```

**后端行为**：
- conversationId = null
- 不添加 MemoryAdvisor
- 不查询数据库
- 不保存历史
- ✅ 性能最优

### 场景 2：连续对话（保存历史）

**传递 conversationId**：
```javascript
// 同一个 conversationId 实现连续对话
const conversationId = generateConversationId()

// 第一次对话
await fetch(`/api/chat/1/stream-flux?conversationId=${conversationId}`, {
  method: 'POST',
  body: '你好'
})

// 第二次对话（复用 conversationId）
await fetch(`/api/chat/1/stream-flux?conversationId=${conversationId}`, {
  method: 'POST',
  body: '你刚才说什么？'  // 模型会记得之前的对话
})
```

**后端行为**：
- conversationId = "abc123..."
- 添加 MemoryAdvisor
- 查询数据库加载历史
- 保存新消息到数据库
- ✅ 支持上下文连续对话

### 场景 3：跨页面保持对话

**使用 localStorage**：
```javascript
// 保存会话
function saveConversation() {
  localStorage.setItem('conversationId', conversationId.value)
  localStorage.setItem('messages', JSON.stringify(messages.value))
}

// 恢复会话
function restoreConversation() {
  const savedId = localStorage.getItem('conversationId')
  const savedMessages = localStorage.getItem('messages')

  if (savedId && savedMessages) {
    conversationId.value = savedId
    messages.value = JSON.parse(savedMessages)
  }
}
```

**使用 Vuex/Pinia**：
```javascript
// store.js
export const useChatStore = defineStore('chat', {
  state: () => ({
    conversationId: null,
    messages: []
  }),

  actions: {
    startNewConversation() {
      this.conversationId = crypto.randomUUID().replace(/-/g, '')
      this.messages = []
    },

    addMessage(message) {
      this.messages.push(message)
    }
  }
})
```

## 3. API 接口说明

### 流式对话接口

```bash
POST /api/chat/{modelId}/stream-flux
Query参数: conversationId (可选)
Body: 纯文本消息

# 示例1：不带历史
curl -X POST http://localhost:8080/api/chat/1/stream-flux \
  -H "Content-Type: text/plain" \
  -d "你好"

# 示例2：带历史（连续对话）
curl -X POST "http://localhost:8080/api/chat/1/stream-flux?conversationId=abc123" \
  -H "Content-Type: text/plain" \
  -d "你好"
```

### 支持 Reasoning 的接口

```bash
POST /api/chat/{modelId}/stream-with-reasoning
Query参数: conversationId (可选)
Body: 纯文本消息

# 用于 OpenAI o1 等支持思考的模型
curl -X POST "http://localhost:8080/api/chat/1/stream-with-reasoning?conversationId=abc123" \
  -H "Content-Type: text/plain" \
  -d "解决这道数学题"
```

## 4. 数据库查看

```sql
-- 查看某个会话的所有历史
SELECT * FROM chat_history
WHERE conversation_id = 'abc123def456...'
ORDER BY create_time;

-- 查看所有会话
SELECT conversation_id, COUNT(*) as message_count
FROM chat_history
GROUP BY conversation_id
ORDER BY MAX(create_time) DESC;
```

## 5. 最佳实践

### ✅ 推荐做法

1. **前端生成 UUID**：使用 `crypto.randomUUID().replace(/-/g, '')`
2. **持久化会话**：使用 localStorage 或状态管理库
3. **UI 提示**：显示"新对话"按钮，让用户主动开始新会话
4. **自动保存**：每次对话后保存到 localStorage

### ❌ 不推荐做法

1. 不要依赖后端生成 conversationId（后端不再自动生成）
2. 不要每次请求都生成新 UUID（无法连续对话）
3. 不要在不需要历史时传递 conversationId（影响性能）

### 性能优化

**仅在需要时启用历史**：
```javascript
// 场景1：快速问答（不需要历史）
function quickChat(message) {
  return fetch(`/api/chat/1/stream-flux`, {  // 不传 conversationId
    method: 'POST',
    body: message
  })
}

// 场景2：深度对话（需要历史）
function deepChat(message, conversationId) {
  return fetch(`/api/chat/1/stream-flux?conversationId=${conversationId}`, {
    method: 'POST',
    body: message
  })
}
```

## 6. 完整工作流程

```
1. 用户打开聊天页面
   ↓
2. 前端生成 conversationId = crypto.randomUUID().replace(/-/g, '')
   ↓
3. 用户发送消息："你好"
   ↓
4. 前端调用: /api/chat/1/stream-flux?conversationId=abc123
   ↓
5. 后端：添加 MemoryAdvisor → 查询历史（首次为空）→ 调用 LLM
   ↓
6. 后端：保存到数据库
   - conversation_id: abc123
   - USER: "你好"
   - ASSISTANT: "你好！有什么可以帮助你的吗？"
   ↓
7. 用户发送第二条消息："今天天气怎么样"
   ↓
8. 前端调用: /api/chat/1/stream-flux?conversationId=abc123 (同一个ID)
   ↓
9. 后端：添加 MemoryAdvisor → 查询历史（加载之前的对话）→ 调用 LLM
   ↓
10. LLM 看到完整上下文：
    - USER: "你好"
    - ASSISTANT: "你好！有什么可以帮助你的吗？"
    - USER: "今天天气怎么样"
   ↓
11. 后端：保存新消息到数据库
   ↓
12. 用户点击"新对话"按钮
   ↓
13. 前端生成新的 conversationId = def456 (新UUID)
   ↓
14. 新对话开始，历史不相互干扰 ✅
```

这样设计，前端完全控制对话流程，后端只负责存储和加载历史，职责清晰！
