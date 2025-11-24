<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api'

const channels = ref([])
const form = ref({ name: '', type: 'OPENAI', apiKey: '', baseUrl: '' })
const showForm = ref(false) // Control form visibility
const editingId = ref(null)

const load = async () => {
  try {
    const res = await api.getChannels()
    channels.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    console.error('Failed to load channels:', e)
  }
}

const submit = async () => {
  if (editingId.value) {
    await api.updateChannel(editingId.value, form.value)
  } else {
    await api.createChannel(form.value)
  }
  resetForm()
  await load()
}

const edit = (channel) => {
  form.value = { ...channel }
  editingId.value = channel.id
  showForm.value = true
}

const resetForm = () => {
  form.value = { name: '', type: 'OPENAI', apiKey: '', baseUrl: '' }
  editingId.value = null
  showForm.value = false
}

const remove = async (id) => {
  if(confirm('确定要删除该渠道吗？')) {
    await api.deleteChannel(id)
    await load()
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- Page Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-3xl font-bold text-slate-900">渠道管理</h2>
        <p class="text-slate-500 mt-2">管理与 OpenAI, Ollama 等 AI 提供商的连接配置。</p>
      </div>
      <button @click="showForm = !showForm; if(!showForm) resetForm()" class="px-4 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span>{{ showForm ? '取消' : '添加渠道' }}</span>
        <span v-if="!showForm" class="text-lg">+</span>
      </button>
    </div>

    <!-- Add Form (Collapsible) -->
    <transition name="slide-fade">
      <div v-if="showForm" class="bg-white rounded-2xl shadow-lg border border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-800 mb-4">{{ editingId ? '编辑渠道' : '配置新渠道' }}</h3>
        <form @submit.prevent="submit" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">渠道名称</label>
              <input v-model="form.name" placeholder="例如: OpenAI 生产环境" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" required />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">提供商类型</label>
              <div class="relative">
                <select v-model="form.type" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all appearance-none bg-white">
                  <option value="OPENAI">OpenAI</option>
                  <option value="OLLAMA">Ollama</option>
                  <option value="AZURE_OPENAI">Azure OpenAI</option>
                </select>
                <div class="absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none text-slate-500">
                  ▼
                </div>
              </div>
            </div>
            <div class="space-y-1 md:col-span-2">
              <label class="text-sm font-medium text-slate-700">API Key</label>
              <input v-model="form.apiKey" type="password" placeholder="sk-..." class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all font-mono" required />
            </div>
            <div class="space-y-1 md:col-span-2">
              <label class="text-sm font-medium text-slate-700">Base URL (可选)</label>
              <input v-model="form.baseUrl" placeholder="例如: https://api.openai-proxy.com" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all font-mono text-sm" />
              <p class="text-xs text-slate-400">如果您使用代理或本地 Ollama (http://localhost:11434)，请在此配置。</p>
            </div>
          </div>
          <div class="flex justify-end pt-2">
            <button type="submit" class="px-6 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors">
              保存配置
            </button>
          </div>
        </form>
      </div>
    </transition>

    <!-- Channel List (Cards) -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div v-for="(channel, index) in channels" :key="channel.id || index" class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between h-full group">
        <div>
          <div class="flex justify-between items-start mb-2">
            <div class="flex items-center gap-2">
              <span class="text-2xl" v-if="channel.type === 'OPENAI'">🧠</span>
              <span class="text-2xl" v-else-if="channel.type === 'OLLAMA'">🦙</span>
              <span class="text-2xl" v-else>☁️</span>
              <h3 class="font-bold text-slate-800">{{ channel.name }}</h3>
            </div>
            <span class="px-2 py-1 bg-slate-100 text-slate-600 text-xs font-medium rounded-md border border-slate-200">
              {{ channel.type }}
            </span>
          </div>
          <div class="text-sm text-slate-500 font-mono bg-slate-50 p-2 rounded border border-slate-100 truncate">
             {{ channel.baseUrl || 'Default URL' }}
          </div>
        </div>
        <div class="mt-4 flex justify-end opacity-0 group-hover:opacity-100 transition-opacity gap-3">
          <button @click="edit(channel)" class="text-sm text-indigo-500 hover:text-indigo-700 font-medium flex items-center gap-1">
            ✏️ 编辑
          </button>
          <button @click="remove(channel.id)" class="text-sm text-red-500 hover:text-red-700 font-medium flex items-center gap-1">
            🗑 删除
          </button>
        </div>
      </div>
      
      <!-- Empty State -->
      <div v-if="channels.length === 0" class="md:col-span-2 bg-slate-50 rounded-xl border-2 border-dashed border-slate-200 p-12 text-center">
        <div class="text-4xl mb-4">🔌</div>
        <h3 class="text-slate-900 font-medium text-lg">暂无渠道</h3>
        <p class="text-slate-500">点击右上角添加您的第一个模型提供商渠道。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}
.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1);
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}
</style>
