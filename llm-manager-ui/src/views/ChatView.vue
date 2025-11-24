<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import api from '../services/api'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 配置 marked
marked.setOptions({
    breaks: true,
    gfm: true,
    headerIds: false,
    mangle: false
})

// 配置 DOMPurify
const purifyConfig = {
    ALLOWED_TAGS: [
        'p', 'br', 'strong', 'em', 'u', 's', 'del', 'ins',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'ul', 'ol', 'li',
        'blockquote', 'code', 'pre',
        'a', 'img',
        'table', 'thead', 'tbody', 'tr', 'th', 'td',
        'hr', 'div', 'span'
    ],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'id']
}

const models = ref([])
const agents = ref([])
const selectedModelId = ref(null)
const selectedAgentSlug = ref(null)
const useAgent = ref(false)
const messages = ref([])
const userInput = ref('')
const loading = ref(false)
const debugToken = ref('') // For testing agent auth
const chatContainer = ref(null) // Ref for auto-scrolling

const load = async () => {
    const [mRes, aRes] = await Promise.all([api.getModels(), api.getAgents()])
    models.value = mRes.data
    agents.value = aRes.data
    if (models.value.length) selectedModelId.value = models.value[0].id
    if (agents.value.length) selectedAgentSlug.value = agents.value[0].slug
}

const scrollToBottom = () => {
    nextTick(() => {
        if (chatContainer.value) {
            chatContainer.value.scrollTop = chatContainer.value.scrollHeight
        }
    })
}

// Markdown 渲染函数
const renderMarkdown = (content) => {
    if (!content) return ''
    try {
        const rawHtml = marked.parse(content)
        return DOMPurify.sanitize(rawHtml, purifyConfig)
    } catch (error) {
        console.error('Markdown parsing error:', error)
        return content
    }
}

watch(messages, scrollToBottom, { deep: true })

const send = async () => {
    if (!userInput.value.trim() || loading.value) return

    const text = userInput.value
    messages.value.push({ role: 'user', content: text })
    userInput.value = ''
    loading.value = true

    // 创建一个临时的assistant消息用于实时追加内容
    const assistantMsgIndex = messages.value.length
    messages.value.push({ role: 'assistant', content: '' })

    try {
        if (useAgent.value) {
            if(!debugToken.value) {
                messages.value[assistantMsgIndex] = { role: 'error', content: '请先输入访问令牌 (Token) 以测试智能体。' }
                loading.value = false
                return
            }

            // 使用流式API
            await api.chatWithAgentStream(
                selectedAgentSlug.value,
                text,
                debugToken.value,
                (chunk) => {
                    // 实时追加内容
                    messages.value[assistantMsgIndex].content += chunk
                },
                () => {
                    // 完成
                    loading.value = false
                },
                (error) => {
                    // 错误处理
                    messages.value[assistantMsgIndex] = {
                        role: 'error',
                        content: '错误: ' + (error.message || '请求失败')
                    }
                    loading.value = false
                }
            )
        } else {
            // 使用流式API
            await api.chatStream(
                selectedModelId.value,
                text,
                (chunk) => {
                    // 实时追加内容
                    messages.value[assistantMsgIndex].content += chunk
                },
                () => {
                    // 完成
                    loading.value = false
                },
                (error) => {
                    // 错误处理
                    messages.value[assistantMsgIndex] = {
                        role: 'error',
                        content: '错误: ' + (error.message || '请求失败')
                    }
                    loading.value = false
                }
            )
        }
    } catch (e) {
        messages.value[assistantMsgIndex] = {
            role: 'error',
            content: '错误: ' + (e.response?.data || e.message)
        }
        loading.value = false
    }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col h-[calc(100vh-8rem)] bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden">
    <!-- Top Bar -->
    <div class="bg-slate-50/80 backdrop-blur p-4 border-b border-slate-200 flex flex-wrap gap-4 items-center justify-between z-10">
        <div class="flex items-center gap-4 flex-grow">
            <div class="flex items-center bg-white rounded-lg p-1 shadow-sm border border-slate-200">
                <button 
                    @click="useAgent = false"
                    :class="['px-3 py-1.5 rounded-md text-sm font-medium transition-all', !useAgent ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-50']"
                >
                    原生模型
                </button>
                <button 
                    @click="useAgent = true"
                    :class="['px-3 py-1.5 rounded-md text-sm font-medium transition-all', useAgent ? 'bg-violet-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-50']"
                >
                    智能体
                </button>
            </div>

            <div v-if="!useAgent" class="flex items-center gap-2 flex-grow max-w-md">
                <select v-model="selectedModelId" class="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
                    <option v-for="m in models" :key="m.id" :value="m.id">📦 {{ m.name }} ({{ m.modelIdentifier }})</option>
                </select>
            </div>

            <div v-else class="flex items-center gap-2 flex-grow max-w-xl">
                <select v-model="selectedAgentSlug" class="w-1/2 px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-violet-500 focus:border-violet-500 outline-none">
                    <option v-for="a in agents" :key="a.id" :value="a.slug">🤖 {{ a.name }}</option>
                </select>
                <input v-model="debugToken" placeholder="粘贴 Access Token" class="w-1/2 px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-violet-500 focus:border-violet-500 outline-none font-mono" />
            </div>
        </div>
        
        <button @click="messages = []" class="text-slate-400 hover:text-red-500 text-sm flex items-center gap-1 transition-colors" title="清空对话">
            <span class="text-lg">🗑</span> 清空
        </button>
    </div>

    <!-- Chat Area -->
    <div ref="chatContainer" class="flex-grow bg-slate-50 p-6 overflow-y-auto space-y-6 scroll-smooth">
        <div v-if="messages.length === 0" class="h-full flex flex-col items-center justify-center text-slate-400 space-y-4 opacity-60">
            <div class="w-20 h-20 bg-slate-200 rounded-full flex items-center justify-center text-4xl">
                💬
            </div>
            <p class="text-lg font-medium">准备好开始对话了吗？</p>
            <p class="text-sm">选择一个模型或智能体，发送消息开始交互。</p>
        </div>

        <transition-group name="message">
            <div v-for="(msg, idx) in messages" :key="idx" 
                :class="['flex w-full', msg.role === 'user' ? 'justify-end' : 'justify-start']">
                
                <!-- Avatar for Assistant -->
                <div v-if="msg.role !== 'user'" class="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex-shrink-0 flex items-center justify-center text-white text-xs mr-3 shadow-md mt-1">
                    {{ msg.role === 'error' ? '!' : 'AI' }}
                </div>

                <div :class="[
                    'max-w-[80%] px-5 py-3.5 rounded-2xl shadow-sm text-sm leading-relaxed break-words markdown-content',
                    msg.role === 'user'
                        ? 'bg-indigo-600 text-white rounded-br-sm'
                        : msg.role === 'error'
                            ? 'bg-red-50 text-red-700 border border-red-100 rounded-bl-sm'
                            : 'bg-white text-slate-800 border border-slate-100 rounded-bl-sm'
                ]">
                    <!-- 用户消息 -->
                    <div v-if="msg.role === 'user'">{{ msg.content }}</div>

                    <!-- AI 消息 - 如果内容为空且正在 loading，显示三个点 -->
                    <div v-else-if="msg.role === 'assistant' && !msg.content && loading" class="flex items-center gap-1">
                        <div class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0s"></div>
                        <div class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0.2s"></div>
                        <div class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0.4s"></div>
                    </div>

                    <!-- AI 消息 - 有内容时显示 markdown -->
                    <div v-else-if="msg.role !== 'user'" v-html="renderMarkdown(msg.content)"></div>
                </div>

                <!-- Avatar for User (Optional, simplified out for cleanliness or added on right) -->
            </div>
        </transition-group>
    </div>

    <!-- Input Area -->
    <div class="p-4 bg-white border-t border-slate-200">
        <div class="relative max-w-4xl mx-auto">
            <input 
                @keyup.enter="send" 
                v-model="userInput" 
                class="w-full pl-5 pr-12 py-4 bg-slate-50 border border-slate-200 rounded-full text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white shadow-inner transition-all" 
                placeholder="输入您的消息..." 
                :disabled="loading" 
            />
            <button 
                @click="send" 
                :disabled="loading || !userInput.trim()"
                class="absolute right-2 top-2 bottom-2 w-10 h-10 bg-indigo-600 rounded-full flex items-center justify-center text-white shadow-md hover:bg-indigo-700 disabled:opacity-50 disabled:hover:bg-indigo-600 transition-all active:scale-95"
            >
                <span class="text-sm transform -rotate-45 translate-x-px -translate-y-px">➤</span>
            </button>
        </div>
        <div class="text-center mt-2">
            <p class="text-xs text-slate-400">AI 生成内容仅供参考</p>
        </div>
    </div>
  </div>
</template>

<style scoped>
.message-enter-active,
.message-leave-active {
  transition: all 0.3s ease;
}
.message-enter-from,
.message-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* Markdown 样式 */
.markdown-content {
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.markdown-content :deep(strong) {
  font-weight: 700;
  color: inherit;
}

.markdown-content :deep(em) {
  font-style: italic;
}

.markdown-content :deep(u) {
  text-decoration: underline;
}

.markdown-content :deep(s),
.markdown-content :deep(del) {
  text-decoration: line-through;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  font-weight: 600;
  margin-top: 1em;
  margin-bottom: 0.5em;
  line-height: 1.3;
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.3em;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.15em;
}

.markdown-content :deep(p) {
  margin-bottom: 0.8em;
  line-height: 1.6;
}

.markdown-content :deep(br) {
  display: block;
  content: "";
  margin-top: 0.4em;
}

.markdown-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 1em;
  overflow-x: auto;
  margin: 0.8em 0;
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  font-size: 0.85em;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-left: 1.5em;
  margin-bottom: 0.8em;
}

.markdown-content :deep(li) {
  margin-bottom: 0.3em;
  line-height: 1.6;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding-left: 1em;
  color: #6a737d;
  margin: 0.8em 0;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.8em 0;
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  border: 1px solid #dfe2e5;
  padding: 0.5em;
  text-align: left;
}

.markdown-content :deep(table th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.markdown-content :deep(a) {
  color: #0969da;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 1.5em 0;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.8em 0;
}

/* 确保第一个元素没有上边距 */
.markdown-content :deep(> *:first-child) {
  margin-top: 0;
}

/* 确保最后一个元素没有下边距 */
.markdown-content :deep(> *:last-child) {
  margin-bottom: 0;
}
</style>
