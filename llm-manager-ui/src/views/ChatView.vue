<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
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
const useTools = ref(false) // 是否启用工具调用
const showToolPanel = ref(false) // 是否显示工具选择面板
const availableTools = ref({}) // 可用的工具列表 {name -> description}
const selectedTools = ref([]) // 已选择的工具名称列表
const messages = ref([])
const userInput = ref('')
const loading = ref(false)
const chatContainer = ref(null) // Ref for auto-scrolling
const conversationId = ref(null) // 会话ID，前端控制

// 生成不含"-"的 UUID
const generateConversationId = () => {
    return crypto.randomUUID().replace(/-/g, '')
}

// 开始新对话
const startNewConversation = () => {
    conversationId.value = generateConversationId()
    messages.value = []

    // 保存到 localStorage
    localStorage.setItem('conversationId', conversationId.value)
    localStorage.setItem('chatMessages', JSON.stringify(messages.value))

    console.log('[新对话] conversationId:', conversationId.value)
}

const load = async () => {
    const [mRes, aRes, toolsRes] = await Promise.all([
        api.getModels(),
        api.getAgents(),
        api.getAvailableTools()
    ])
    models.value = mRes.data
    agents.value = aRes.data

    // 加载工具列表
    if (toolsRes.data.success) {
        availableTools.value = toolsRes.data.tools || {}
        // 默认选中所有工具
        selectedTools.value = Object.keys(availableTools.value)
        console.log('[工具列表] 已加载:', selectedTools.value)
    }

    if (models.value.length) selectedModelId.value = models.value[0].id
    if (agents.value.length) selectedAgentSlug.value = agents.value[0].slug

    // 尝试从 localStorage 恢复会话
    const savedConversationId = localStorage.getItem('conversationId')
    const savedMessages = localStorage.getItem('chatMessages')

    if (savedConversationId && savedMessages) {
        conversationId.value = savedConversationId
        messages.value = JSON.parse(savedMessages)
        console.log('[恢复会话] conversationId:', conversationId.value, '消息数:', messages.value.length)
    } else {
        // 初始化新对话
        startNewConversation()
    }
}

const scrollToBottom = () => {
    nextTick(() => {
        if (chatContainer.value) {
            chatContainer.value.scrollTop = chatContainer.value.scrollHeight
        }
    })
}

// 全选/取消全选工具
const toggleAllTools = () => {
    const allToolNames = Object.keys(availableTools.value)
    if (selectedTools.value.length === allToolNames.length) {
        selectedTools.value = []
    } else {
        selectedTools.value = [...allToolNames]
    }
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
            // 使用智能体流式API（内部接口，支持会话历史）
            await api.chatWithAgentStream(
                selectedAgentSlug.value,
                text,
                conversationId.value,
                (chunk) => {
                    // 实时追加内容
                    messages.value[assistantMsgIndex].content += chunk
                },
                () => {
                    // 完成，保存消息到 localStorage
                    localStorage.setItem('chatMessages', JSON.stringify(messages.value))
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
            // 根据 useTools 状态选择不同的 API
            if (useTools.value) {
                // 使用工具调用流式API（传递选中的工具列表）
                await api.chatStreamWithTools(
                    selectedModelId.value,
                    text,
                    conversationId.value,
                    selectedTools.value, // 传递选中的工具列表
                    (chunk) => {
                        messages.value[assistantMsgIndex].content += chunk
                    },
                    () => {
                        localStorage.setItem('chatMessages', JSON.stringify(messages.value))
                        loading.value = false
                    },
                    (error) => {
                        messages.value[assistantMsgIndex] = {
                            role: 'error',
                            content: '错误: ' + (error.message || '请求失败')
                        }
                        loading.value = false
                    }
                )
            } else {
                // 使用普通流式API
                await api.chatStream(
                    selectedModelId.value,
                    text,
                    conversationId.value,
                    (chunk) => {
                        messages.value[assistantMsgIndex].content += chunk
                    },
                    () => {
                        localStorage.setItem('chatMessages', JSON.stringify(messages.value))
                        loading.value = false
                    },
                    (error) => {
                        messages.value[assistantMsgIndex] = {
                            role: 'error',
                            content: '错误: ' + (error.message || '请求失败')
                        }
                        loading.value = false
                    }
                )
            }
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

// 点击外部关闭工具面板
const toolPanelRef = ref(null)
const handleClickOutside = (event) => {
    if (showToolPanel.value && toolPanelRef.value && !toolPanelRef.value.contains(event.target)) {
        showToolPanel.value = false
    }
}
onMounted(() => {
    document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside)
})
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

            <div v-if="!useAgent" class="flex items-center gap-2 flex-grow max-w-2xl">
                <select v-model="selectedModelId" class="flex-grow px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
                    <option v-for="m in models" :key="m.id" :value="m.id">📦 {{ m.name }} ({{ m.modelIdentifier }})</option>
                </select>
                <label class="flex items-center gap-2 cursor-pointer px-3 py-2 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors whitespace-nowrap" title="启用工具调用（如天气查询等）">
                    <input type="checkbox" v-model="useTools" class="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-2 focus:ring-indigo-500" />
                    <span class="text-sm text-slate-700">🔧 工具</span>
                </label>
                <!-- 工具选择下拉面板（仅当启用工具时显示） -->
                <div v-if="useTools && Object.keys(availableTools).length > 0" class="relative" ref="toolPanelRef">
                    <button
                        @click="showToolPanel = !showToolPanel"
                        class="flex items-center gap-2 px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm hover:bg-slate-50 transition-colors"
                        :class="{ 'ring-2 ring-indigo-500': showToolPanel }"
                    >
                        <span class="text-slate-700">已选 {{ selectedTools.length }}/{{ Object.keys(availableTools).length }}</span>
                        <svg class="w-4 h-4 text-slate-400 transition-transform" :class="{ 'rotate-180': showToolPanel }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                        </svg>
                    </button>
                    <!-- 下拉面板 -->
                    <div v-if="showToolPanel" class="absolute top-full left-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg z-20 min-w-[200px] max-h-[240px] overflow-y-auto">
                        <div class="p-2 border-b border-slate-100 flex justify-between items-center">
                            <span class="text-xs text-slate-500">选择工具</span>
                            <button @click="toggleAllTools" class="text-xs text-indigo-600 hover:text-indigo-800">
                                {{ selectedTools.length === Object.keys(availableTools).length ? '取消全选' : '全选' }}
                            </button>
                        </div>
                        <div class="p-1">
                            <label
                                v-for="(desc, name) in availableTools"
                                :key="name"
                                class="flex items-start gap-2 px-2 py-1.5 rounded hover:bg-slate-50 cursor-pointer"
                                :title="desc"
                            >
                                <input
                                    type="checkbox"
                                    :value="name"
                                    v-model="selectedTools"
                                    class="mt-0.5 w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-2 focus:ring-indigo-500"
                                />
                                <div class="flex-1 min-w-0">
                                    <div class="text-sm text-slate-700 truncate">{{ name }}</div>
                                    <div class="text-xs text-slate-400 truncate">{{ desc }}</div>
                                </div>
                            </label>
                        </div>
                    </div>
                </div>
            </div>

            <div v-else class="flex items-center gap-2 flex-grow max-w-xl">
                <select v-model="selectedAgentSlug" class="flex-grow px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-violet-500 focus:border-violet-500 outline-none">
                    <option v-for="a in agents" :key="a.id" :value="a.slug">🤖 {{ a.name }}</option>
                </select>
            </div>
        </div>
        
        <div class="flex items-center gap-2">
            <button @click="startNewConversation" class="text-slate-400 hover:text-green-600 text-sm flex items-center gap-1 transition-colors px-3 py-1.5 rounded-lg hover:bg-green-50" title="开始新对话">
                <span class="text-lg">✨</span> 新对话
            </button>
            <button @click="messages = []; localStorage.setItem('chatMessages', '[]')" class="text-slate-400 hover:text-red-500 text-sm flex items-center gap-1 transition-colors px-3 py-1.5 rounded-lg hover:bg-red-50" title="清空对话">
                <span class="text-lg">🗑</span> 清空
            </button>
        </div>
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
