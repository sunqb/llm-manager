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

  // Chat - 流式
  chatStream(modelId, message, conversationId, onChunk, onComplete, onError) {
    const token = localStorage.getItem('satoken')
    // 如果有 conversationId，添加到 URL 查询参数
    const url = conversationId
      ? `http://localhost:8080/api/chat/${modelId}/stream-flux?conversationId=${conversationId}`
      : `http://localhost:8080/api/chat/${modelId}/stream-flux`

    return streamFetch(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
          'satoken': token || ''
        },
        body: message
      },
      onChunk,
      onComplete,
      onError
    )
  },

  chatWithAgentStream(slug, message, conversationId, onChunk, onComplete, onError) {
    const token = localStorage.getItem('satoken')

    // 如果有 conversationId，添加到 URL 查询参数（支持会话历史）
    const url = conversationId
      ? `http://localhost:8080/api/chat/agents/${slug}/stream?conversationId=${conversationId}`
      : `http://localhost:8080/api/chat/agents/${slug}/stream`

    return streamFetch(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
          'satoken': token || ''
        },
        body: message
      },
      onChunk,
      onComplete,
      onError
    )
  },

  // Chat - 流式（带工具调用）
  chatStreamWithTools(modelId, message, conversationId, toolNames, onChunk, onComplete, onError) {
    const token = localStorage.getItem('satoken')

    // 构建 URL 查询参数
    const params = new URLSearchParams()
    if (conversationId) {
      params.append('conversationId', conversationId)
    }
    // 支持传递 toolNames 数组
    if (toolNames && toolNames.length > 0) {
      toolNames.forEach(name => params.append('toolNames', name))
    }

    const queryString = params.toString()
    const url = queryString
      ? `http://localhost:8080/api/chat/${modelId}/with-tools/stream?${queryString}`
      : `http://localhost:8080/api/chat/${modelId}/with-tools/stream`

    return streamFetch(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
          'satoken': token || ''
        },
        body: message
      },
      onChunk,
      onComplete,
      onError
    )
  },

  // Chat - 流式（带图片 URL，多模态）
  chatStreamWithImageUrl(modelId, message, imageUrls, conversationId, onChunk, onComplete, onError) {
    const token = localStorage.getItem('satoken')

    // 构建 URL 查询参数
    const params = new URLSearchParams()
    params.append('message', message)
    if (conversationId) {
      params.append('conversationId', conversationId)
    }
    // 支持传递 imageUrls 数组
    if (imageUrls && imageUrls.length > 0) {
      imageUrls.forEach(url => params.append('imageUrls', url))
    }

    const url = `http://localhost:8080/api/chat/${modelId}/with-image-url?${params.toString()}`

    return streamFetch(
      url,
      {
        method: 'POST',
        headers: {
          'satoken': token || ''
        }
      },
      onChunk,
      onComplete,
      onError
    )
  }
}
