<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 顶部导航栏 -->
    <nav class="bg-white shadow-sm border-b">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <h1 class="text-2xl font-bold text-gray-900">签名管理系统</h1>
          </div>
          <div class="flex items-center space-x-4">
            <span class="text-sm text-gray-600">欢迎，{{ username }}</span>
            <button
              @click="handleLogout"
              class="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-md transition"
            >
              登出
            </button>
          </div>
        </div>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 标签导航 -->
      <div class="bg-white rounded-lg shadow-sm mb-6">
        <div class="border-b border-gray-200">
          <nav class="-mb-px flex space-x-8 px-6" aria-label="Tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              @click="activeTab = tab.key"
              :class="[
                activeTab === tab.key
                  ? 'border-indigo-500 text-indigo-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
                'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition'
              ]"
            >
              {{ tab.label }}
            </button>
          </nav>
        </div>
      </div>

      <!-- 统计概览 -->
      <div v-if="activeTab === 'dashboard'" class="space-y-6">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <StatCard
            title="总签名数"
            :value="statistics.totalSignatures"
            icon="📊"
            color="blue"
          />
          <StatCard
            title="活跃签名"
            :value="statistics.activeSignatures"
            icon="✅"
            color="green"
          />
          <StatCard
            title="已过期"
            :value="statistics.expiredSignatures"
            icon="⏰"
            color="yellow"
          />
          <StatCard
            title="已撤销"
            :value="statistics.revokedSignatures"
            icon="❌"
            color="red"
          />
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div class="bg-white p-6 rounded-lg shadow-sm">
            <h3 class="text-lg font-semibold text-gray-900 mb-4">今日统计</h3>
            <div class="space-y-3">
              <div class="flex justify-between items-center">
                <span class="text-gray-600">今日颁发</span>
                <span class="text-2xl font-bold text-indigo-600">{{ statistics.todayIssued }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-gray-600">今日使用</span>
                <span class="text-2xl font-bold text-green-600">{{ statistics.todayUsed }}</span>
              </div>
            </div>
          </div>

          <div class="bg-white p-6 rounded-lg shadow-sm">
            <h3 class="text-lg font-semibold text-gray-900 mb-4">按服务统计</h3>
            <div class="space-y-2">
              <div
                v-for="(count, service) in statistics.signaturesByService"
                :key="service"
                class="flex justify-between items-center py-2 border-b border-gray-100 last:border-0"
              >
                <span class="text-gray-700">{{ service }}</span>
                <span class="text-sm font-medium text-gray-900">{{ count }}</span>
              </div>
              <div v-if="Object.keys(statistics.signaturesByService).length === 0" class="text-center text-gray-400 py-4">
                暂无数据
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 签名列表 -->
      <div v-if="activeTab === 'list'">
        <SignatureList @refresh="loadStatistics" />
      </div>

      <!-- 颁发签名 -->
      <div v-if="activeTab === 'issue'">
        <IssueSignature @issued="handleSignatureIssued" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminApi } from '@/api/admin'
import StatCard from '@/components/StatCard.vue'
import SignatureList from '@/components/SignatureList.vue'
import IssueSignature from '@/components/IssueSignature.vue'

const router = useRouter()
const authStore = useAuthStore()

const username = computed(() => authStore.username)

const activeTab = ref('dashboard')
const tabs = [
  { key: 'dashboard', label: '仪表盘' },
  { key: 'list', label: '签名列表' },
  { key: 'issue', label: '颁发签名' }
]

const statistics = ref({
  totalSignatures: 0,
  activeSignatures: 0,
  expiredSignatures: 0,
  revokedSignatures: 0,
  todayIssued: 0,
  todayUsed: 0,
  signaturesByService: {} as Record<string, number>,
  signaturesByOperation: {} as Record<string, number>
})

const loadStatistics = async () => {
  try {
    const data = await adminApi.getSignatureStatistics()
    statistics.value = data
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const handleSignatureIssued = () => {
  loadStatistics()
  activeTab.value = 'list'
}

onMounted(() => {
  loadStatistics()
})
</script>
