import { createRouter, createWebHistory, RouteLocationNormalized, NavigationGuardNext } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface RouteMeta {
  requiresAuth?: boolean
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false } as RouteMeta
  },
  {
    path: '/',
    name: 'ServiceManagement',
    component: () => import('@/views/ServiceManagement.vue'),
    meta: { requiresAuth: true } as RouteMeta
  },
  {
    path: '/services/:serviceName',
    name: 'BucketList',
    component: () => import('@/views/ServiceManagement.vue'),
    meta: { requiresAuth: true } as RouteMeta
  },
  {
    path: '/services/:serviceName/:bucketName',
    name: 'FolderList',
    component: () => import('@/views/ServiceManagement.vue'),
    meta: { requiresAuth: true } as RouteMeta
  },
  {
    path: '/services/:serviceName/:bucketName/:folderName',
    name: 'FileList',
    component: () => import('@/views/ServiceManagement.vue'),
    meta: { requiresAuth: true } as RouteMeta
  },
  {
    path: '/test-upload',
    name: 'TestUpload',
    component: () => import('@/views/TestUpload.vue'),
    meta: { requiresAuth: true } as RouteMeta
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true } as RouteMeta
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (
  to: RouteLocationNormalized,
  _from: RouteLocationNormalized,
  next: NavigationGuardNext
) => {
  const authStore = useAuthStore()
  const meta = to.meta as RouteMeta
  
  if (meta.requiresAuth) {
    if (!authStore.isLoggedIn) {
      // 尝试检查状态
      const isLoggedIn = await authStore.checkStatus()
      if (!isLoggedIn) {
        next('/login')
        return
      }
    }
  } else {
    // 如果已登录，访问登录页时重定向到首页
    if (authStore.isLoggedIn) {
      next('/')
      return
    }
  }
  
  next()
})

export default router

