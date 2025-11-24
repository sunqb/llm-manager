import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import ChannelList from '../views/ChannelList.vue'
import ModelList from '../views/ModelList.vue'
import AgentList from '../views/AgentList.vue'
import TokenList from '../views/TokenList.vue'
import ChatView from '../views/ChatView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', name: 'home', component: HomeView },
    { path: '/channels', name: 'channels', component: ChannelList },
    { path: '/models', name: 'models', component: ModelList },
    { path: '/agents', name: 'agents', component: AgentList },
    { path: '/tokens', name: 'tokens', component: TokenList },
    { path: '/chat', name: 'chat', component: ChatView },
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('satoken')
  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
