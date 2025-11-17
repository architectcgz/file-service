import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'UploadTest',
    component: () => import('@/views/UploadTest.vue')
  },
  {
    path: '/api-upload-test',
    name: 'ApiUploadTest',
    component: () => import('@/views/ApiUploadTest.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/admin',
    name: 'SignatureManagement',
    component: () => import('@/views/SignatureManagement.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true'
  
  if (to.meta.requiresAuth && !isLoggedIn) {
    // 需要登录但未登录，跳转到登录页
    next('/login')
  } else if (to.path === '/login' && isLoggedIn) {
    // 已登录用户访问登录页，跳转到管理页
    next('/admin')
  } else {
    next()
  }
})

export default router

