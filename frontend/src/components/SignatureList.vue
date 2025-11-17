<template>
  <div class="bg-white rounded-lg shadow-sm">
    <!-- 筛选栏 -->
    <div class="p-6 border-b border-gray-200">
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <input
          v-model="filters.callerService"
          type="text"
          placeholder="服务名称"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        />
        <select
          v-model="filters.status"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        >
          <option value="">全部状态</option>
          <option value="active">活跃</option>
          <option value="expired">已过期</option>
          <option value="revoked">已撤销</option>
        </select>
        <select
          v-model="filters.operation"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        >
          <option value="">全部操作</option>
          <option value="upload">上传</option>
          <option value="download">下载</option>
          <option value="delete">删除</option>
          <option value="*">所有</option>
        </select>
        <button
          @click="loadSignatures"
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition"
        >
          搜索
        </button>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="p-6 border-b border-gray-200 flex justify-between items-center">
      <div class="flex space-x-2">
        <button
          v-if="selectedSignatures.length > 0"
          @click="batchRevoke"
          class="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition text-sm"
        >
          批量撤销 ({{ selectedSignatures.length }})
        </button>
        <button
          @click="cleanExpired"
          class="px-4 py-2 bg-yellow-600 text-white rounded-lg hover:bg-yellow-700 transition text-sm"
        >
          清理过期签名
        </button>
      </div>
      <button
        @click="loadSignatures"
        class="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition text-sm"
      >
        刷新
      </button>
    </div>

    <!-- 表格 -->
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left">
              <input
                type="checkbox"
                @change="toggleSelectAll"
                :checked="selectedSignatures.length === signatures.length && signatures.length > 0"
                class="rounded border-gray-300"
              />
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              服务名称
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              操作类型
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              状态
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              使用次数
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              过期时间
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              操作
            </th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="sig in signatures" :key="sig.id" class="hover:bg-gray-50">
            <td class="px-6 py-4">
              <input
                type="checkbox"
                :value="sig.signatureToken"
                v-model="selectedSignatures"
                class="rounded border-gray-300"
              />
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="text-sm font-medium text-gray-900">{{ sig.callerService }}</div>
              <div v-if="sig.callerServiceId" class="text-xs text-gray-500">
                {{ sig.callerServiceId }}
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-800">
                {{ sig.allowedOperation }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span
                :class="getStatusClass(sig.status)"
                class="px-2 py-1 text-xs font-medium rounded-full"
              >
                {{ getStatusText(sig.status) }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ sig.usageCount }}
              <span v-if="sig.maxUsageCount > 0" class="text-gray-500">
                / {{ sig.maxUsageCount }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ formatDate(sig.expiresAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm space-x-2">
              <button
                @click="viewDetails(sig)"
                class="text-indigo-600 hover:text-indigo-900"
              >
                详情
              </button>
              <button
                v-if="sig.status === 'active'"
                @click="revokeSignature(sig.signatureToken)"
                class="text-red-600 hover:text-red-900"
              >
                撤销
              </button>
            </td>
          </tr>
          <tr v-if="signatures.length === 0">
            <td colspan="7" class="px-6 py-12 text-center text-gray-500">
              暂无签名数据
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
      <div class="text-sm text-gray-700">
        共 {{ totalCount }} 条记录，第 {{ pageIndex }} / {{ totalPages }} 页
      </div>
      <div class="flex space-x-2">
        <button
          @click="prevPage"
          :disabled="pageIndex === 1"
          class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          上一页
        </button>
        <button
          @click="nextPage"
          :disabled="pageIndex === totalPages"
          class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          下一页
        </button>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <SignatureDetailModal
      v-if="selectedSignature"
      :signature="selectedSignature"
      @close="selectedSignature = null"
      @refresh="loadSignatures"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import SignatureDetailModal from './SignatureDetailModal.vue'

const emit = defineEmits(['refresh'])

const signatures = ref<any[]>([])
const selectedSignatures = ref<string[]>([])
const selectedSignature = ref<any>(null)
const pageIndex = ref(1)
const pageSize = ref(20)
const totalCount = ref(0)
const totalPages = ref(0)

const filters = ref({
  callerService: '',
  status: '',
  operation: ''
})

const loadSignatures = async () => {
  try {
    const params = {
      pageIndex: pageIndex.value,
      pageSize: pageSize.value,
      ...filters.value
    }
    const response = await adminApi.getSignatures(params)
    signatures.value = response.items || []
    totalCount.value = response.totalCount || 0
    totalPages.value = response.totalPages || 0
    selectedSignatures.value = []
  } catch (error) {
    console.error('加载签名列表失败:', error)
  }
}

const toggleSelectAll = () => {
  if (selectedSignatures.value.length === signatures.value.length) {
    selectedSignatures.value = []
  } else {
    selectedSignatures.value = signatures.value.map(s => s.signatureToken)
  }
}

const revokeSignature = async (token: string) => {
  if (!confirm('确定要撤销此签名吗？')) return

  try {
    const reason = prompt('请输入撤销原因:') || '管理员手动撤销'
    await adminApi.revokeSignature(token, reason)
    alert('撤销成功')
    loadSignatures()
    emit('refresh')
  } catch (error) {
    alert('撤销失败')
  }
}

const batchRevoke = async () => {
  if (!confirm(`确定要批量撤销 ${selectedSignatures.value.length} 个签名吗？`)) return

  try {
    const reason = prompt('请输入撤销原因:') || '管理员批量撤销'
    await adminApi.batchRevokeSignatures(selectedSignatures.value, reason)
    alert('批量撤销成功')
    loadSignatures()
    emit('refresh')
  } catch (error) {
    alert('批量撤销失败')
  }
}

const cleanExpired = async () => {
  if (!confirm('确定要清理所有过期签名吗？')) return

  try {
    const result = await adminApi.cleanExpiredSignatures()
    alert(`已清理 ${result.cleanedCount} 个过期签名`)
    loadSignatures()
    emit('refresh')
  } catch (error) {
    alert('清理失败')
  }
}

const viewDetails = (sig: any) => {
  selectedSignature.value = sig
}

const prevPage = () => {
  if (pageIndex.value > 1) {
    pageIndex.value--
    loadSignatures()
  }
}

const nextPage = () => {
  if (pageIndex.value < totalPages.value) {
    pageIndex.value++
    loadSignatures()
  }
}

const getStatusClass = (status: string) => {
  const classes = {
    active: 'bg-green-100 text-green-800',
    expired: 'bg-yellow-100 text-yellow-800',
    revoked: 'bg-red-100 text-red-800'
  }
  return classes[status as keyof typeof classes] || 'bg-gray-100 text-gray-800'
}

const getStatusText = (status: string) => {
  const texts = {
    active: '活跃',
    expired: '已过期',
    revoked: '已撤销'
  }
  return texts[status as keyof typeof texts] || status
}

const formatDate = (date: string) => {
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadSignatures()
})
</script>
