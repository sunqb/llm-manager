<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api'

const tokens = ref([])
const form = ref({ name: '' })
const showForm = ref(false)
const showUsage = ref(null) // Token ID for which usage is shown

const load = async () => {
  const res = await api.getTokens()
  tokens.value = res.data
}

const submit = async () => {
  await api.createToken(form.value)
  form.value.name = ''
  showForm.value = false
  await load()
}

const revoke = async (id) => {
  if(confirm('确定要吊销该令牌吗？它将立即失效。')) {
    await api.revokeToken(id)
    await load()
  }
}

const copy = (text) => {
    navigator.clipboard.writeText(text)
    // In a real app, use a toast notification
    alert('已复制到剪贴板')
}

const copyCurl = (token) => {
    const cmd = `curl -X POST http://localhost:8080/api/external/agents/{agent_slug}/chat \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer ${token}" \\
  -d '{"message": "Hello World"}'`
    copy(cmd)
}

const toggleUsage = (id) => {
    showUsage.value = showUsage.value === id ? null : id
}

onMounted(load)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-3xl font-bold text-slate-900">令牌管理</h2>
        <p class="text-slate-500 mt-2">发行 API 密钥，用于外部应用集成。</p>
      </div>
      <button @click="showForm = !showForm" class="px-4 py-2 bg-amber-500 text-white rounded-lg font-semibold shadow hover:bg-amber-600 transition-colors flex items-center gap-2">
        <span>{{ showForm ? '取消' : '发行令牌' }}</span>
        <span v-if="!showForm" class="text-lg">+</span>
      </button>
    </div>

    <!-- Form -->
    <transition name="slide-fade">
      <div v-if="showForm" class="bg-white rounded-2xl shadow-lg border border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-800 mb-4">发行新令牌</h3>
        <form @submit.prevent="submit" class="flex gap-4 items-end">
          <div class="flex-grow space-y-1">
            <label class="text-sm font-medium text-slate-700">客户端名称</label>
            <input v-model="form.name" placeholder="例如: 移动端 App, 测试脚本" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-amber-500 focus:ring-2 focus:ring-amber-200 outline-none transition-all" required />
          </div>
          <button type="submit" class="px-6 py-2 bg-amber-500 text-white rounded-lg font-semibold shadow hover:bg-amber-600 transition-colors h-[42px]">
            生成令牌
          </button>
        </form>
      </div>
    </transition>

    <!-- List (Table) -->
    <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
      <table class="min-w-full divide-y divide-slate-200">
        <thead class="bg-slate-50">
          <tr>
            <th class="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">名称</th>
            <th class="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Token (点击复制)</th>
            <th class="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">状态</th>
            <th class="px-6 py-4 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">操作</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-slate-200">
          <template v-for="(token, index) in tokens" :key="token.id || index">
            <tr class="hover:bg-slate-50 transition-colors">
              <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900">{{ token.name }}</td>
              <td class="px-6 py-4 whitespace-nowrap">
                <button @click="copy(token.token)" class="group flex items-center gap-2 px-3 py-1.5 rounded-md bg-slate-100 hover:bg-slate-200 border border-slate-200 transition-colors w-full max-w-xs">
                  <span class="font-mono text-xs text-slate-600 truncate">{{ token.token.substring(0, 12) }}...</span>
                  <span class="text-slate-400 group-hover:text-slate-600 text-xs">📋</span>
                </button>
              </td>
              <td class="px-6 py-4 whitespace-nowrap">
                <span :class="[
                  'px-2.5 py-0.5 rounded-full text-xs font-medium',
                  token.active 
                    ? 'bg-green-100 text-green-800 border border-green-200' 
                    : 'bg-slate-100 text-slate-500 border border-slate-200'
                ]">
                  {{ token.active ? '🟢 有效' : '⚫ 已失效' }}
                </span>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium flex justify-end gap-2">
                <button @click="toggleUsage(token.id)" class="text-indigo-600 hover:text-indigo-800 hover:bg-indigo-50 px-3 py-1 rounded-md transition-colors">
                  用法
                </button>
                <button v-if="token.active" @click="revoke(token.id)" class="text-red-500 hover:text-red-700 hover:bg-red-50 px-3 py-1 rounded-md transition-colors">
                  吊销
                </button>
                <span v-else class="text-slate-300 cursor-not-allowed px-3 py-1">
                  -
                </span>
              </td>
            </tr>
            <!-- Usage Details Row -->
            <tr v-if="showUsage === token.id" class="bg-slate-50">
              <td colspan="4" class="px-6 py-4">
                <div class="bg-slate-900 rounded-lg p-4 text-slate-300 text-xs font-mono overflow-x-auto">
                  <p class="mb-2 text-slate-400"># 调用示例 (cURL)</p>
                  <div class="flex justify-between items-start">
                    <pre class="whitespace-pre-wrap break-all">curl -X POST http://localhost:8080/api/external/agents/{agent_slug}/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {{ token.token }}" \
  -d '{"message": "Hello World"}'</pre>
                    <button @click="copyCurl(token.token)" class="ml-4 text-indigo-400 hover:text-indigo-300">
                      📋
                    </button>
                  </div>
                  <p class="mt-2 text-slate-500 italic">注意: 请将 {agent_slug} 替换为您创建的智能体标识。</p>
                </div>
              </td>
            </tr>
          </template>
          
          <tr v-if="tokens.length === 0">
            <td colspan="4" class="px-6 py-12 text-center text-slate-500">
              暂无令牌，请点击上方按钮发行。
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.slide-fade-enter-active { transition: all 0.3s ease-out; }
.slide-fade-leave-active { transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1); }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateY(-10px); opacity: 0; }
</style>
