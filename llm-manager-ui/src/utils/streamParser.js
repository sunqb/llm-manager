// SSE流式数据解析工具

// JSON字符串转对象
function str2json(str) {
  try {
    return JSON.parse(str)
  } catch (e) {
    return null
  }
}

/**
 * 解析SSE消息数据
 * @param {string} dataStr - 原始数据字符串
 * @param {object} cache - 缓存对象，用于处理分片数据
 * @returns {object} 解析结果
 */
export function analysisStreamData(dataStr = '', cache = { value: '' }) {
  const newData = []
  let _success = true
  let errorObj = {}

  // 按行分割数据
  const lines = dataStr.split(/[\r\n]+/g).map((str) => str.replace(/\n/g, ''))

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()

    // 没有数据 执行下一次循环
    if (!line) continue

    // 完成标记
    if (line.includes('data: [DONE]')) break

    // 删除 "data:" 前缀
    let templineData = line.replace('data:', '').trim()

    // 如果无法解析JSON，加入缓存
    let testJson1 = str2json(templineData)
    let lineData = ''

    if (testJson1 === null) {
      // 数据不完整，加入缓存
      cache.value = cache.value + templineData

      // 测试缓存是否能成功解析
      let testJson2 = str2json(cache.value)
      if (testJson2 === null) {
        // 仍然不完整，继续下一个
        continue
      } else {
        // 拼接成功
        lineData = cache.value
        cache.value = ''
      }
    } else {
      lineData = templineData
      cache.value = '' // 清空缓存
    }

    // 完成标记
    if (lineData === '[DONE]') break

    // 解析JSON数据
    let jsonData = str2json(lineData)

    // 解析失败
    if (!jsonData) {
      console.warn('解析失败:', lineData)
      continue
    }

    // 处理错误
    if (jsonData?.error) {
      console.error('流式响应错误', jsonData)
      _success = false
      errorObj = {
        message: jsonData.error.message || '未知错误',
        type: jsonData.error.type || 'unknown',
        code: jsonData.error.code
      }
      continue
    }

    // 提取内容和思考过程
    if (jsonData?.choices) {
      const delta = jsonData.choices[0]?.delta
      const content = delta?.content
      const reasoning = delta?.reasoning_content

      if (content || reasoning) {
        newData.push({
          content: content || '',
          reasoning: reasoning || ''
        })
      }
    }
  }

  return {
    success: _success,
    error: errorObj,
    list: newData
  }
}

/**
 * 使用 fetch API 处理流式请求
 * @param {string} url - 请求URL
 * @param {object} options - fetch选项
 * @param {function} onChunk - 每次接收到数据块的回调 (chunk) 或 ({content, reasoning})
 * @param {function} onComplete - 完成时的回调
 * @param {function} onError - 错误时的回调
 */
export async function streamFetch(url, options, onChunk, onComplete, onError) {
  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        'Accept': 'text/event-stream'
      }
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    const cache = { value: '' }

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        onComplete?.()
        break
      }

      // 解码数据块
      const chunk = decoder.decode(value, { stream: true })

      // 解析SSE数据
      const result = analysisStreamData(chunk, cache)

      if (!result.success) {
        onError?.(result.error)
        break
      }

      // 回调每个内容片段
      if (result.list.length > 0) {
        // 检查是否为新格式（包含 content 和 reasoning）
        const firstItem = result.list[0]
        if (typeof firstItem === 'object' && ('content' in firstItem || 'reasoning' in firstItem)) {
          // 新格式：分别传递 content 和 reasoning
          result.list.forEach(item => {
            onChunk?.({ content: item.content, reasoning: item.reasoning })
          })
        } else {
          // 旧格式：直接传递字符串（兼容性）
          onChunk?.(result.list.join(''))
        }
      }
    }
  } catch (error) {
    console.error('流式请求错误:', error)
    onError?.(error)
  }
}
