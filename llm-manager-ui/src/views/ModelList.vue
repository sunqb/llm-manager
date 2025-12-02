<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api'

const models = ref([])
const channels = ref([])
const form = ref({ name: '', modelIdentifier: '', channel: { id: null }, temperature: 0.7 })
const showForm = ref(false)
const editingId = ref(null)

const load = async () => {
  try {
    const [mRes, cRes] = await Promise.all([api.getModels(), api.getChannels()])
    models.value = Array.isArray(mRes.data) ? mRes.data : []
    channels.value = Array.isArray(cRes.data) ? cRes.data : []
    
    // Set default channel if creating new and not yet set
    if (!editingId.value && channels.value.length > 0 && !form.value.channel?.id) {
       form.value.channel = { id: channels.value[0].id }
    }
  } catch (e) {
    console.error('Failed to load models/channels:', e)
  }
}

const submit = async () => {
  const payload = {
    name: form.value.name,
    modelIdentifier: form.value.modelIdentifier,
    channelId: form.value.channel?.id || form.value.channel,  // 转换为 channelId
    temperature: form.value.temperature,
    description: form.value.description,
    maxTokens: form.value.maxTokens
  }

  if (editingId.value) {
    await api.updateModel(editingId.value, payload)
  } else {
    await api.createModel(payload)
  }
  resetForm()
  await load()
}

const edit = (model) => {
  // 深拷贝并转换字段名
  form.value = {
    name: model.name,
    modelIdentifier: model.modelIdentifier,
    channel: { id: model.channelId },  // 从 channelId 转换为 channel.id
    temperature: model.temperature,
    description: model.description,
    maxTokens: model.maxTokens
  }

  editingId.value = model.id
  showForm.value = true
}

const resetForm = () => {
  form.value = {
    name: '',
    modelIdentifier: '',
    channel: { id: channels.value[0]?.id || null },
    temperature: 0.7,
    description: '',
    maxTokens: null
  }
  editingId.value = null
  showForm.value = false
}

const remove = async (id) => {
  if(confirm('确定要删除该模型吗？')) {
    await api.deleteModel(id)
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
        <h2 class="text-3xl font-bold text-slate-900">模型管理</h2>
        <p class="text-slate-500 mt-2">定义可用的 LLM 模型及其默认参数。</p>
      </div>
      <button @click="showForm = !showForm; if(!showForm) resetForm()" class="px-4 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span>{{ showForm ? '取消' : '添加模型' }}</span>
        <span v-if="!showForm" class="text-lg">+</span>
      </button>
    </div>

    <!-- Form -->
    <transition name="slide-fade">
      <div v-if="showForm" class="bg-white rounded-2xl shadow-lg border border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-800 mb-4">{{ editingId ? '编辑模型' : '注册新模型' }}</h3>
        <form @submit.prevent="submit" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">显示名称</label>
              <input v-model="form.name" placeholder="例如: GPT-4 生产环境" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" required />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">模型标识 (Model ID)</label>
              <input v-model="form.modelIdentifier" placeholder="例如: gpt-4-turbo" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all font-mono" required />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">所属渠道</label>
              <div class="relative">
                <select v-model="form.channel.id" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all appearance-none bg-white" required>
                  <option v-for="c in channels" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
                <div class="absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none text-slate-500">▼</div>
              </div>
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">默认温度 (0.0 - 2.0)</label>
              <input v-model.number="form.temperature" type="number" step="0.1" min="0" max="2" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" />
            </div>
            <div class="space-y-1">
              <label class="text-sm font-medium text-slate-700">最大 Tokens (可选)</label>
              <input v-model.number="form.maxTokens" type="number" min="1" placeholder="例如: 4096" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all" />
            </div>
            <div class="space-y-1 md:col-span-2">
              <label class="text-sm font-medium text-slate-700">模型描述 (可选)</label>
              <textarea v-model="form.description" placeholder="例如: 用于生产环境的高性能模型" rows="2" class="w-full px-4 py-2 rounded-lg border border-slate-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none transition-all resize-none"></textarea>
            </div>
          </div>
          <div class="flex justify-end pt-2">
            <button type="submit" class="px-6 py-2 bg-indigo-600 text-white rounded-lg font-semibold shadow hover:bg-indigo-700 transition-colors">
              保存模型
            </button>
          </div>
        </form>
      </div>
    </transition>

    <!-- List -->
    <div class="grid grid-cols-1 gap-4">
      <div v-for="(model, index) in models" :key="model.id || index" class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm hover:shadow-md transition-all flex items-center justify-between group">
        <div class="flex items-center gap-4">
          <div class="w-10 h-10 bg-indigo-50 text-indigo-600 rounded-lg flex items-center justify-center text-lg">
            📦
          </div>
          <div>
            <h3 class="font-bold text-slate-800">{{ model.name }}</h3>
            <div class="flex items-center gap-3 text-sm text-slate-500 mt-1">
              <span class="font-mono bg-slate-100 px-1.5 py-0.5 rounded text-slate-600">{{ model.modelIdentifier }}</span>
              <span class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 bg-slate-300 rounded-full"></span>
                {{ model.channel?.name }}
              </span>
              <span class="flex items-center gap-1">
                🌡️ {{ model.temperature }}
              </span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button @click="edit(model)" class="text-sm text-indigo-500 hover:text-indigo-700 font-medium flex items-center gap-1">
            ✏️ 编辑
          </button>
          <button @click="remove(model.id)" class="text-sm text-red-500 hover:text-red-700 font-medium flex items-center gap-1">
            🗑 删除
          </button>
        </div>
      </div>

       <!-- Empty State -->
      <div v-if="models.length === 0" class="bg-slate-50 rounded-xl border-2 border-dashed border-slate-200 p-12 text-center">
        <div class="text-4xl mb-4">📦</div>
        <h3 class="text-slate-900 font-medium text-lg">暂无模型</h3>
        <p class="text-slate-500">请先添加渠道，然后创建关联的模型。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slide-fade-enter-active { transition: all 0.3s ease-out; }
.slide-fade-leave-active { transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1); }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateY(-10px); opacity: 0; }
</style>
