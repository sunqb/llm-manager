<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api'

const agents = ref([])
const models = ref([])
const form = ref({ name: '', slug: '', systemPrompt: '', llmModel: { id: null }, temperatureOverride: null })
const showForm = ref(false)
const editingId = ref(null)

const load = async () => {
  try {
    const [aRes, mRes] = await Promise.all([api.getAgents(), api.getModels()])
    agents.value = Array.isArray(aRes.data) ? aRes.data : []
    models.value = Array.isArray(mRes.data) ? mRes.data : []
    
    if (!editingId.value && models.value.length > 0 && !form.value.llmModel?.id) {
      form.value.llmModel = { id: models.value[0].id }
    }
  } catch (e) {
    console.error('Failed to load agents/models:', e)
  }
}

const submit = async () => {
  const payload = { ...form.value }
  if (typeof payload.llmModel === 'number' || typeof payload.llmModel === 'string') {
      payload.llmModel = { id: payload.llmModel }
  }

  if (editingId.value) {
      await api.updateAgent(editingId.value, payload)
  } else {
      await api.createAgent(payload)
  }
  resetForm()
  await load()
}

const edit = (agent) => {
    form.value = JSON.parse(JSON.stringify(agent))
    if (!form.value.llmModel) form.value.llmModel = { id: null }
    editingId.value = agent.id
    showForm.value = true
}

const resetForm = () => {
    form.value = { 
        name: '', 
        slug: '', 
        systemPrompt: '', 
        llmModel: { id: models.value[0]?.id || null }, 
        temperatureOverride: null 
    }
    editingId.value = null
    showForm.value = false
}

const remove = async (id) => {
    if(confirm('确定要删除该智能体吗？')) {
        await api.deleteAgent(id)
        await load()
    }
}

onMounted(load)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex justify-between items-end">
      <div>
        <h2 class="text-3xl font-bold text-slate-900">智能体管理</h2>
        <p class="text-slate-500 mt-2">创建和管理 AI 智能体，为应用提供专属的对话接口。</p>
      </div>
      <button @click="showForm = !showForm; if(!showForm) resetForm()" class="px-4 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span>{{ showForm ? '取消' : '添加智能体' }}</span>
        <span v-if="!showForm" class="text-lg">+</span>
      </button>
    </div>

    <!-- Form -->
    <transition name="slide-fade">
      <div v-if="showForm" class="bg-white rounded-2xl shadow-lg border border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-800 mb-4">{{ editingId ? '编辑智能体' : '创建新智能体' }}</h3>
        <form @submit.prevent="submit" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">智能体名称</label>
              <input v-model="form.name" placeholder="例如: 代码助手" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" required />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">唯一标识 (Slug)</label>
              <input v-model="form.slug" placeholder="例如: coder-v1" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all font-mono" required />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">基础模型</label>
              <div class="relative">
                <select v-model="form.llmModel.id" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all appearance-none bg-white" required>
                  <option v-for="m in models" :key="m.id" :value="m.id">{{ m.name }} ({{ m.modelIdentifier }})</option>
                </select>
                <div class="absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none text-slate-500">▼</div>
              </div>
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">温度覆盖 (可选)</label>
              <input v-model.number="form.temperatureOverride" type="number" step="0.1" placeholder="覆盖模型默认温度" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" />
            </div>
          </div>
          <div class="space-y-1">
            <label class="text-sm font-medium text-slate-700">系统提示词 (System Prompt)</label>
            <textarea v-model="form.systemPrompt" placeholder="设定智能体的人设和行为准则..." class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all h-32 font-mono text-sm" required></textarea>
          </div>
          <div class="flex justify-end pt-2">
            <button type="submit" class="px-6 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors">
              {{ editingId ? '保存更改' : '创建智能体' }}
            </button>
          </div>
        </form>
      </div>
    </transition>

    <!-- List -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div v-for="(agent, index) in agents" :key="agent.id || index" class="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex flex-col h-full group">
        <div class="flex justify-between items-start mb-4">
          <div class="flex items-center gap-3">
            <div class="w-12 h-12 rounded-full bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center text-white font-bold text-xl shadow-lg shadow-violet-200">
              {{ agent.name.charAt(0) }}
            </div>
            <div>
              <h3 class="font-bold text-lg text-slate-900">{{ agent.name }}</h3>
              <p class="text-xs font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded inline-block">/{{ agent.slug }}</p>
            </div>
          </div>
          <div class="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-2">
             <button @click="edit(agent)" class="text-slate-400 hover:text-indigo-500 p-1 rounded-full hover:bg-indigo-50 transition-colors">
              ✏️
            </button>
            <button @click="remove(agent.id)" class="text-slate-400 hover:text-red-500 p-1 rounded-full hover:bg-red-50 transition-colors">
              🗑
            </button>
          </div>
        </div>
        
        <div class="flex-grow mb-4">
          <div class="bg-slate-50 rounded-lg p-3 border border-slate-100 h-24 overflow-y-auto">
            <p class="text-xs text-slate-600 font-mono whitespace-pre-wrap">{{ agent.systemPrompt }}</p>
          </div>
        </div>

        <div class="flex items-center justify-between pt-4 border-t border-slate-100">
          <div class="flex items-center gap-2 text-xs text-slate-500">
            <span class="flex items-center gap-1 bg-slate-100 px-2 py-1 rounded-full">
              📦 {{ agent.llmModel?.name }}
            </span>
            <span v-if="agent.temperatureOverride !== null" class="flex items-center gap-1 bg-orange-50 text-orange-600 px-2 py-1 rounded-full">
              🌡️ {{ agent.temperatureOverride }}
            </span>
          </div>
          <RouterLink to="/chat" class="text-xs font-bold text-indigo-600 hover:text-indigo-800 flex items-center gap-1">
            测试 <span class="text-sm">→</span>
          </RouterLink>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="agents.length === 0" class="md:col-span-2 bg-slate-50 rounded-xl border-2 border-dashed border-slate-200 p-12 text-center">
        <div class="text-4xl mb-4">🤖</div>
        <h3 class="text-slate-900 font-medium text-lg">暂无智能体</h3>
        <p class="text-slate-500">创建您的第一个智能体，开始构建 AI 应用。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slide-fade-enter-active { transition: all 0.3s ease-out; }
.slide-fade-leave-active { transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1); }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateY(-10px); opacity: 0; }
</style>
