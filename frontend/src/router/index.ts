import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/admin/signatures',
    children: [
      {
        path: '/admin/signatures',
        name: 'SignatureManagement',
        component: () => import('@/views/SignatureManagement.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/admin/test/upload',
        name: 'UploadTest',
        component: () => import('@/views/UploadTest.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/admin/test/api-upload',
        name: 'ApiUploadTest',
        component: () => import('@/views/ApiUploadTest.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/admin/test/test-upload',
        name: 'TestUpload',
        component: () => import('@/views/TestUpload.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  // 兼容旧路由，重定向到新路由
  {
    path: '/api-upload-test',
    redirect: '/admin/test/api-upload'
  },
  {
    path: '/test-upload',
    redirect: '/admin/test/test-upload'
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
    next('/admin/signatures')
  } else {
    next()
  }
})

export default router

