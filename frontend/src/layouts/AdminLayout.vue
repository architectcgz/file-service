<template>
  <div class="flex h-screen bg-gray-100">
    <!-- 侧边栏 -->
    <aside class="w-64 bg-white shadow-lg">
      <div class="flex flex-col h-full">
        <!-- Logo 和标题 -->
        <div class="flex items-center justify-center h-16 bg-blue-600 text-white">
          <h1 class="text-xl font-bold">文件服务管理后台</h1>
        </div>

        <!-- 导航菜单 -->
        <nav class="flex-1 overflow-y-auto py-4">
          <div class="px-3 mb-2">
            <h3 class="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">管理功能</h3>
          </div>
          <router-link
            v-for="item in adminMenuItems"
            :key="item.path"
            :to="item.path"
            class="flex items-center px-6 py-3 text-gray-700 hover:bg-blue-50 hover:text-blue-600 transition-colors"
            active-class="bg-blue-50 text-blue-600 border-r-4 border-blue-600"
          >
            <span class="text-xl mr-3">{{ item.icon }}</span>
            <span class="font-medium">{{ item.name }}</span>
          </router-link>

          <div class="px-3 mt-6 mb-2">
            <h3 class="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">测试工具</h3>
          </div>
          <router-link
            v-for="item in testMenuItems"
            :key="item.path"
            :to="item.path"
            class="flex items-center px-6 py-3 text-gray-700 hover:bg-green-50 hover:text-green-600 transition-colors"
            active-class="bg-green-50 text-green-600 border-r-4 border-green-600"
          >
            <span class="text-xl mr-3">{{ item.icon }}</span>
            <span class="font-medium">{{ item.name }}</span>
          </router-link>
        </nav>

        <!-- 底部用户信息 -->
        <div class="border-t border-gray-200 p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center">
              <div class="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-white font-bold">
                {{ username.charAt(0).toUpperCase() }}
              </div>
              <span class="ml-3 text-sm font-medium text-gray-700">{{ username }}</span>
            </div>
            <button
              @click="handleLogout"
              class="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded transition"
              title="登出"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="flex-1 overflow-y-auto">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = computed(() => authStore.username || '管理员')

const adminMenuItems = [
  { path: '/admin/signatures', name: '签名管理', icon: '🔐' },
]

const testMenuItems = [
  { path: '/admin/test/upload', name: '上传测试', icon: '📤' },
  { path: '/admin/test/api-upload', name: 'API上传测试', icon: '🔌' },
  { path: '/admin/test/test-upload', name: '测试上传', icon: '🧪' },
]

const handleLogout = () => {
  if (confirm('确定要登出吗？')) {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* 滚动条样式 */
nav::-webkit-scrollbar {
  width: 6px;
}

nav::-webkit-scrollbar-track {
  background: #f1f1f1;
}

nav::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

nav::-webkit-scrollbar-thumb:hover {
  background: #555;
}
</style>
