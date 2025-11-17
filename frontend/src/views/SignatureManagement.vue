<template>
  <div class="p-6 bg-gray-50 min-h-full">
    <!-- 页面标题 -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-800">签名管理</h1>
      <p class="text-gray-600 mt-1">管理和查看服务签名信息</p>
    </div>

    <!-- 主内容区 -->
    <div>
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
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import StatCard from '@/components/StatCard.vue'
import SignatureList from '@/components/SignatureList.vue'
import IssueSignature from '@/components/IssueSignature.vue'

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

const handleSignatureIssued = () => {
  loadStatistics()
  activeTab.value = 'list'
}

onMounted(() => {
  loadStatistics()
})
</script>
