import axios from 'axios'
import { streamFetch } from '@/utils/streamParser'

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.request.use(config => {
  const token = localStorage.getItem('satoken')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

apiClient.interceptors.response.use(response => {
    // Handle Sa-Token "soft" 401 errors (HTTP 200 but code=401 in body)
    if (response.data && response.data.code === 401) {
        localStorage.removeItem('satoken')
        window.location.href = '/login'
        return Promise.reject(new Error('Unauthorized'))
    }
    return response
}, error => {
    if (error.response && error.response.status === 401) {
        localStorage.removeItem('satoken')
        window.location.href = '/login'
    }
    return Promise.reject(error)
})

export default {
  // Channels
  getChannels() { return apiClient.get('/channels') },
  createChannel(data) { return apiClient.post('/channels', data) },
  updateChannel(id, data) { return apiClient.put(`/channels/${id}`, data) },
  deleteChannel(id) { return apiClient.delete(`/channels/${id}`) },

  // Models
  getModels() { return apiClient.get('/models') },
  createModel(data) { return apiClient.post('/models', data) },
  updateModel(id, data) { return apiClient.put(`/models/${id}`, data) },
  deleteModel(id) { return apiClient.delete(`/models/${id}`) },

  // Agents
  getAgents() { return apiClient.get('/agents') },
  createAgent(data) { return apiClient.post('/agents', data) },
  updateAgent(id, data) { return apiClient.put(`/agents/${id}`, data) },
  deleteAgent(id) { return apiClient.delete(`/agents/${id}`) },

  // Tokens
  getTokens() { return apiClient.get('/tokens') },
  createToken(data) { return apiClient.post('/tokens', data) },
  revokeToken(id) { return apiClient.post(`/tokens/${id}/revoke`) },

  // Tools - 工具列表
  getAvailableTools() { return apiClient.get('/chat/tools') },

  // Chat - 阻塞式
  chat(modelId, message) { return apiClient.post(`/chat/${modelId}`, message) },
  chatWithAgent(slug, message, token) {
      return axios.post(`http://localhost:8080/api/external/agents/${slug}/chat`, { message }, {
          headers: { 'Authorization': `Bearer ${token}` }
      })
  },

  /**
   * 统一流式对话接口
   *
   * 支持所有对话场景：
   * - 基础对话：message + modelId
   * - 智能体对话：message + agentSlug
   * - 工具调用：enableTools=true + toolNames
   * - 多模态：mediaUrls 传入图片URL
   *
   * @param {object} request 请求参数
   * @param {string} request.message 用户消息（必填）
   * @param {number} request.modelId 模型ID（与 agentSlug 二选一）
   * @param {string} request.agentSlug 智能体标识（与 modelId 二选一）
   * @param {string} request.conversationId 会话ID（可选）
   * @param {boolean} request.enableTools 是否启用工具调用（默认 false）
   * @param {string[]} request.toolNames 工具名称列表（可选）
   * @param {string[]} request.mediaUrls 媒体 URL 列表（可选）
   * @param {function} onChunk 每次接收到数据块的回调
   * @param {function} onComplete 完成时的回调
   * @param {function} onError 错误时的回调
   */
  chatStream(request, onChunk, onComplete, onError) {
    const token = localStorage.getItem('satoken')

    return streamFetch(
      'http://localhost:8080/api/chat/stream',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'satoken': token || ''
        },
        body: JSON.stringify(request)
      },
      onChunk,
      onComplete,
      onError
    )
  }
}
