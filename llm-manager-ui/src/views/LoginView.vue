<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const form = ref({ username: '', password: '' })
const error = ref('')

const login = async () => {
  try {
    const res = await axios.post('http://localhost:8080/api/auth/login', form.value)
    if (res.data.code === 200) {
      localStorage.setItem('satoken', res.data.data.tokenValue)
      router.push('/')
    } else {
      error.value = res.data.msg || '登录失败'
    }
  } catch (e) {
    error.value = '网络错误'
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-50">
    <div class="bg-white p-8 rounded-2xl shadow-xl border border-slate-100 w-full max-w-md">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-indigo-600">LLM Manager</h1>
        <p class="text-slate-500 mt-2">请登录以管理系统</p>
      </div>
      
      <form @submit.prevent="login" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-slate-700 mb-1">用户名</label>
          <input v-model="form.username" class="w-full px-4 py-3 rounded-lg border border-slate-200 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all" placeholder="admin" required />
        </div>
        
        <div>
          <label class="block text-sm font-medium text-slate-700 mb-1">密码</label>
          <input type="password" v-model="form.password" class="w-full px-4 py-3 rounded-lg border border-slate-200 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all" placeholder="123456" required />
        </div>

        <div v-if="error" class="text-red-500 text-sm text-center bg-red-50 p-2 rounded">
          {{ error }}
        </div>

        <button type="submit" class="w-full py-3 bg-indigo-600 text-white rounded-lg font-bold hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-500/30">
          登录
        </button>
      </form>
    </div>
  </div>
</template>
