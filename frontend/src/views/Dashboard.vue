<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 顶部导航栏 -->
    <nav class="bg-white shadow-sm border-b">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center">
            <h1 class="text-xl font-bold text-gray-800">文件服务管理后台</h1>
          </div>
          <div class="flex items-center space-x-4">
            <span class="text-sm text-gray-600">欢迎，{{ authStore.username }}</span>
            <button
              @click="handleLogout"
              class="px-4 py-2 text-sm font-medium text-red-600 hover:text-red-700 hover:bg-red-50 rounded-lg transition"
            >
              登出
            </button>
          </div>
        </div>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 标签页导航 -->
      <div class="mb-6">
        <div class="border-b border-gray-200">
          <nav class="-mb-px flex space-x-8">
            <button
              v-for="tab in tabs"
              :key="tab.id"
              @click="activeTab = tab.id"
              :class="[
                'py-4 px-1 border-b-2 font-medium text-sm transition',
                activeTab === tab.id
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              ]"
            >
              {{ tab.name }}
            </button>
          </nav>
        </div>
      </div>

      <!-- 存储桶管理 -->
      <div v-show="activeTab === 'buckets'" class="space-y-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex justify-between items-center mb-4">
            <h2 class="text-lg font-semibold text-gray-800">存储桶管理</h2>
            <button
              @click="showCreateBucketModal = true"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
            >
              创建存储桶
            </button>
          </div>

          <div v-if="bucketsLoading" class="text-center py-8">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <p class="mt-2 text-gray-600">加载中...</p>
          </div>

          <div v-else-if="buckets.length === 0" class="text-center py-8 text-gray-500">
            暂无存储桶
          </div>

          <div v-else class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    存储桶名称
                  </th>
                  <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody class="bg-white divide-y divide-gray-200">
                <tr v-for="bucket in buckets" :key="bucket">
                  <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {{ bucket }}
                  </td>
                  <td class="px-6 py-4 whitespace-nowrap text-right text-sm">
                    <button
                      @click="handleDeleteBucket(bucket)"
                      class="text-red-600 hover:text-red-900 font-medium"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 表管理 -->
      <div v-show="activeTab === 'tables'" class="space-y-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="mb-4">
            <h2 class="text-lg font-semibold text-gray-800 mb-4">创建存储表</h2>
            <div class="flex space-x-4">
              <input
                v-model="newTableService"
                type="text"
                placeholder="请输入服务名称"
                class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
              />
              <button
                @click="handleCreateTable"
                :disabled="!newTableService || tableLoading"
                class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span v-if="tableLoading">创建中...</span>
                <span v-else>创建表</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 文件管理 -->
      <div v-show="activeTab === 'files'" class="space-y-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="mb-4">
            <h2 class="text-lg font-semibold text-gray-800 mb-4">删除文件</h2>
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  文件 Key
                </label>
                <input
                  v-model="deleteFileKey"
                  type="text"
                  placeholder="请输入文件 Key"
                  class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  存储桶名称（可选）
                </label>
                <input
                  v-model="deleteFileBucket"
                  type="text"
                  placeholder="留空则使用默认存储桶"
                  class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
                />
              </div>
              <button
                @click="handleDeleteFile"
                :disabled="!deleteFileKey || fileLoading"
                class="px-6 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span v-if="fileLoading">删除中...</span>
                <span v-else>删除文件</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建存储桶模态框 -->
    <div
      v-if="showCreateBucketModal"
      class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
      @click.self="showCreateBucketModal = false"
    >
      <div class="bg-white rounded-lg shadow-xl p-6 max-w-md w-full mx-4">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">创建存储桶</h3>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">
              存储桶名称
            </label>
            <input
              v-model="newBucketName"
              type="text"
              placeholder="3-63个字符，小写字母、数字、点和连字符"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
            />
            <p class="mt-1 text-xs text-gray-500">
              存储桶名称必须符合S3命名规则
            </p>
          </div>
          <div class="flex justify-end space-x-3">
            <button
              @click="showCreateBucketModal = false"
              class="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition"
            >
              取消
            </button>
            <button
              @click="handleCreateBucket"
              :disabled="!newBucketName || bucketLoading"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span v-if="bucketLoading">创建中...</span>
              <span v-else>创建</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 消息提示 -->
    <div
      v-if="message"
      :class="[
        'fixed top-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 transition',
        messageType === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
      ]"
    >
      {{ message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminApi } from '@/api/admin'
import type { AxiosError } from 'axios'

interface Tab {
  id: string
  name: string
}

type MessageType = 'success' | 'error'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref<string>('buckets')
const tabs: Tab[] = [
  { id: 'buckets', name: '存储桶管理' },
  { id: 'tables', name: '表管理' },
  { id: 'files', name: '文件管理' }
]

// 存储桶相关
const buckets = ref<string[]>([])
const bucketsLoading = ref<boolean>(false)
const showCreateBucketModal = ref<boolean>(false)
const newBucketName = ref<string>('')
const bucketLoading = ref<boolean>(false)

// 表相关
const newTableService = ref<string>('')
const tableLoading = ref<boolean>(false)

// 文件相关
const deleteFileKey = ref<string>('')
const deleteFileBucket = ref<string>('')
const fileLoading = ref<boolean>(false)

// 消息提示
const message = ref<string>('')
const messageType = ref<MessageType>('success')

const showMessage = (msg: string, type: MessageType = 'success'): void => {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

// 加载存储桶列表
const loadBuckets = async (): Promise<void> => {
  bucketsLoading.value = true
  try {
    const response = await adminApi.listBuckets()
    if (response.success) {
      buckets.value = response.data?.buckets || []
    } else {
      showMessage(response.message || '加载存储桶列表失败', 'error')
    }
  } catch (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    showMessage(axiosError.response?.data?.message || '加载存储桶列表失败', 'error')
  } finally {
    bucketsLoading.value = false
  }
}

// 创建存储桶
const handleCreateBucket = async (): Promise<void> => {
  if (!newBucketName.value.trim()) {
    showMessage('请输入存储桶名称', 'error')
    return
  }

  bucketLoading.value = true
  try {
    const response = await adminApi.createBucket(newBucketName.value.trim())
    if (response.success) {
      showMessage(response.message || '创建成功', 'success')
      showCreateBucketModal.value = false
      newBucketName.value = ''
      await loadBuckets()
    } else {
      showMessage(response.message || '创建失败', 'error')
    }
  } catch (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    showMessage(axiosError.response?.data?.message || '创建失败', 'error')
  } finally {
    bucketLoading.value = false
  }
}

// 删除存储桶
const handleDeleteBucket = async (bucketName: string): Promise<void> => {
  if (!confirm(`确定要删除存储桶 "${bucketName}" 吗？此操作不可恢复。`)) {
    return
  }

  try {
    const response = await adminApi.deleteBucket(bucketName)
    if (response.success) {
      showMessage(response.message || '删除成功', 'success')
      await loadBuckets()
    } else {
      showMessage(response.message || '删除失败', 'error')
    }
  } catch (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    showMessage(axiosError.response?.data?.message || '删除失败', 'error')
  }
}

// 创建表
const handleCreateTable = async (): Promise<void> => {
  if (!newTableService.value.trim()) {
    showMessage('请输入服务名称', 'error')
    return
  }

  tableLoading.value = true
  try {
    const response = await adminApi.createTable(newTableService.value.trim())
    if (response.success) {
      showMessage(response.message || '创建成功', 'success')
      newTableService.value = ''
    } else {
      showMessage(response.message || '创建失败', 'error')
    }
  } catch (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    showMessage(axiosError.response?.data?.message || '创建失败', 'error')
  } finally {
    tableLoading.value = false
  }
}

// 删除文件
const handleDeleteFile = async (): Promise<void> => {
  if (!deleteFileKey.value.trim()) {
    showMessage('请输入文件 Key', 'error')
    return
  }

  if (!confirm(`确定要删除文件 "${deleteFileKey.value}" 吗？此操作不可恢复。`)) {
    return
  }

  fileLoading.value = true
  try {
    const response = await adminApi.deleteFile(
      deleteFileKey.value.trim(),
      deleteFileBucket.value.trim() || null
    )
    if (response.success) {
      showMessage(response.message || '删除成功', 'success')
      deleteFileKey.value = ''
      deleteFileBucket.value = ''
    } else {
      showMessage(response.message || '删除失败', 'error')
    }
  } catch (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    showMessage(axiosError.response?.data?.message || '删除失败', 'error')
  } finally {
    fileLoading.value = false
  }
}

// 登出
const handleLogout = async (): Promise<void> => {
  await authStore.logout()
  router.push('/login')
}

onMounted(async () => {
  // 检查登录状态
  const isLoggedIn = await authStore.checkStatus()
  if (!isLoggedIn) {
    router.push('/login')
    return
  }

  // 加载存储桶列表
  if (activeTab.value === 'buckets') {
    await loadBuckets()
  }
})

// 切换标签时加载数据
const watchTab = (): void => {
  if (activeTab.value === 'buckets' && buckets.value.length === 0) {
    loadBuckets()
  }
}

// 使用 watch 监听 activeTab
watch(activeTab, watchTab)
</script>

