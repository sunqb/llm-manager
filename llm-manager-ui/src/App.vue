<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from './services/api'

const router = useRouter()
const route = useRoute()

const isLoggedIn = computed(() => {
  // Simple check, in real app use store
  return !!localStorage.getItem('satoken') && route.name !== 'login'
})

const logout = () => {
  localStorage.removeItem('satoken')
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 font-sans text-slate-900 flex flex-col">
    <!-- 顶部导航栏: 使用 Backdrop Blur 和半透明背景 -->
    <header class="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex justify-between items-center">
        <!-- Logo 区域 -->
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center shadow-lg shadow-indigo-500/30">
            <span class="text-white text-lg">✨</span>
          </div>
          <h1 class="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-indigo-600 to-violet-600">
            LLM Manager
          </h1>
        </div>

        <!-- 导航菜单 -->
        <nav class="flex space-x-1">
          <RouterLink to="/" active-class="bg-indigo-50 text-indigo-700 font-semibold" class="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:bg-slate-100 transition-all duration-200">首页</RouterLink>
          <RouterLink to="/channels" active-class="bg-indigo-50 text-indigo-700 font-semibold" class="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:bg-slate-100 transition-all duration-200">渠道</RouterLink>
          <RouterLink to="/models" active-class="bg-indigo-50 text-indigo-700 font-semibold" class="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:bg-slate-100 transition-all duration-200">模型</RouterLink>
          <RouterLink to="/agents" active-class="bg-indigo-50 text-indigo-700 font-semibold" class="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:bg-slate-100 transition-all duration-200">智能体</RouterLink>
          <RouterLink to="/tokens" active-class="bg-indigo-50 text-indigo-700 font-semibold" class="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:bg-slate-100 transition-all duration-200">令牌</RouterLink>
          
          <div class="w-px h-6 bg-slate-200 mx-2 self-center"></div>
          
          <RouterLink to="/chat" active-class="ring-2 ring-indigo-500 ring-offset-2" class="px-4 py-2 rounded-full text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 shadow-md shadow-indigo-500/20 transition-all duration-200 flex items-center gap-2">
            <span>🚀</span> 调试台
          </RouterLink>
        </nav>

        <!-- 用户菜单 -->
        <div class="relative group" v-if="isLoggedIn">
          <button class="flex items-center gap-2 px-3 py-2 rounded-full hover:bg-slate-100 transition-colors">
            <div class="w-8 h-8 bg-gradient-to-tr from-indigo-500 to-violet-500 rounded-full flex items-center justify-center text-white font-bold text-sm shadow-md">
              A
            </div>
            <span class="text-sm font-medium text-slate-700">Admin</span>
            <span class="text-xs text-slate-400">▼</span>
          </button>
          
          <!-- 下拉菜单 -->
          <div class="absolute right-0 mt-2 w-48 bg-white rounded-xl shadow-xl border border-slate-100 py-1 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 transform origin-top-right z-50">
            <div class="px-4 py-3 border-b border-slate-100">
              <p class="text-sm text-slate-900 font-medium">管理员</p>
              <p class="text-xs text-slate-500 truncate">admin</p>
            </div>
            <button @click="logout" class="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors flex items-center gap-2">
              <span>🚪</span> 退出登录
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="flex-grow w-full max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8 animate-fade-in-up">
      <RouterView />
    </main>

    <!-- 页脚 -->
    <footer class="border-t border-slate-200 bg-white py-6">
      <div class="max-w-7xl mx-auto px-4 text-center text-slate-400 text-sm">
        &copy; 2025 LLM Manager System. Designed for Developers.
      </div>
    </footer>
  </div>
</template>

<style>
/* 简单的进入动画 */
@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
  animation: fade-in-up 0.5s ease-out forwards;
}
</style>
