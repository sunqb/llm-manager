<template>
  <div class="space-y-8">
    <!-- 欢迎 Header -->
    <div class="text-center space-y-2">
      <h2 class="text-4xl font-extrabold tracking-tight text-slate-900">
        欢迎使用 <span class="text-indigo-600">LLM Manager</span>
      </h2>
      <p class="text-lg text-slate-500 max-w-2xl mx-auto">
        一站式管理您的大语言模型基础设施。连接渠道、定义模型、发布智能体，一切尽在掌握。
      </p>
    </div>
    
    <!-- 仪表盘卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
      <!-- 渠道管理卡片 -->
      <div class="group bg-white rounded-2xl p-8 shadow-sm hover:shadow-xl transition-all duration-300 border border-slate-100 relative overflow-hidden">
        <div class="absolute top-0 right-0 -mt-4 -mr-4 w-24 h-24 bg-blue-50 rounded-full opacity-50 group-hover:scale-150 transition-transform duration-500"></div>
        <div class="relative z-10">
          <div class="w-12 h-12 bg-blue-100 text-blue-600 rounded-xl flex items-center justify-center mb-4 text-2xl">
            🔌
          </div>
          <h3 class="font-bold text-xl text-slate-900 mb-2">渠道管理</h3>
          <p class="text-slate-500 mb-6">配置 OpenAI, Azure, Ollama 等多种模型提供商的连接密钥与参数。</p>
          <RouterLink to="/channels" class="inline-flex items-center font-semibold text-blue-600 hover:text-blue-800">
            去配置 <span class="ml-1 text-lg">→</span>
          </RouterLink>
        </div>
      </div>

      <!-- 智能体管理卡片 -->
      <div class="group bg-white rounded-2xl p-8 shadow-sm hover:shadow-xl transition-all duration-300 border border-slate-100 relative overflow-hidden">
        <div class="absolute top-0 right-0 -mt-4 -mr-4 w-24 h-24 bg-violet-50 rounded-full opacity-50 group-hover:scale-150 transition-transform duration-500"></div>
        <div class="relative z-10">
          <div class="w-12 h-12 bg-violet-100 text-violet-600 rounded-xl flex items-center justify-center mb-4 text-2xl">
            🤖
          </div>
          <h3 class="font-bold text-xl text-slate-900 mb-2">智能体管理</h3>
          <p class="text-slate-500 mb-6">基于模型创建带有人设的智能体，支持温度调节与系统提示词定制。</p>
          <RouterLink to="/agents" class="inline-flex items-center font-semibold text-violet-600 hover:text-violet-800">
            去创建 <span class="ml-1 text-lg">→</span>
          </RouterLink>
        </div>
      </div>

      <!-- 令牌管理卡片 -->
      <div class="group bg-white rounded-2xl p-8 shadow-sm hover:shadow-xl transition-all duration-300 border border-slate-100 relative overflow-hidden">
        <div class="absolute top-0 right-0 -mt-4 -mr-4 w-24 h-24 bg-amber-50 rounded-full opacity-50 group-hover:scale-150 transition-transform duration-500"></div>
        <div class="relative z-10">
          <div class="w-12 h-12 bg-amber-100 text-amber-600 rounded-xl flex items-center justify-center mb-4 text-2xl">
            🔑
          </div>
          <h3 class="font-bold text-xl text-slate-900 mb-2">令牌管理</h3>
          <p class="text-slate-500 mb-6">为外部应用发行安全的 API 密钥，细粒度控制访问权限与统计。</p>
          <RouterLink to="/tokens" class="inline-flex items-center font-semibold text-amber-600 hover:text-amber-800">
            去发行 <span class="ml-1 text-lg">→</span>
          </RouterLink>
        </div>
      </div>
    </div>

    <!-- 快速开始区域 -->
    <div class="mt-12 bg-indigo-900 rounded-2xl p-8 md:p-12 text-center text-white shadow-2xl shadow-indigo-900/20 relative overflow-hidden">
      <!-- 装饰背景 -->
      <div class="absolute top-0 left-0 w-full h-full opacity-10 pointer-events-none">
        <svg class="w-full h-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <path d="M0 100 C 20 0 50 0 100 100 Z" fill="white" />
        </svg>
      </div>
      
      <div class="relative z-10">
        <h3 class="text-2xl font-bold mb-4">准备好开始调试了吗？</h3>
        <p class="text-indigo-200 mb-8 max-w-2xl mx-auto">
          您可以直接在调试台中测试您配置的模型和智能体，无需编写任何代码。
        </p>
        <RouterLink to="/chat" class="inline-block px-8 py-3 bg-white text-indigo-900 font-bold rounded-full hover:bg-indigo-50 transition-colors shadow-lg">
          进入调试台
        </RouterLink>
      </div>
    </div>
  </div>
</template>
