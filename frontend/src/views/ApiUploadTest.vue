<template>
  <div class="min-h-screen bg-gray-50 py-8 px-4">
    <div class="max-w-4xl mx-auto">
      <div class="bg-white rounded-lg shadow-lg p-6">
        <!-- 导航按钮 -->
        <div class="flex justify-between items-center mb-6">
          <div>
            <h1 class="text-3xl font-bold text-gray-900 mb-2">API 上传测试</h1>
            <p class="text-gray-600">
              测试通过后端 API 直接上传文件（非直传签名方式）
            </p>
          </div>
          <div class="flex gap-3">
            <router-link
              to="/"
              class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center space-x-2"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
              <span>直传签名测试</span>
            </router-link>
            <router-link
              to="/admin"
              class="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors flex items-center space-x-2"
            >
              <span>🔐 管理后台</span>
            </router-link>
          </div>
        </div>

        <!-- 配置区域 -->
        <div class="bg-gray-50 border border-gray-200 rounded-lg p-4 mb-6">
          <!-- 签名Token配置 -->
          <div class="mb-4 pb-4 border-b border-gray-200">
            <div class="flex items-center gap-2 mb-3">
              <h3 class="text-base font-semibold text-gray-900">🔐 API 签名配置</h3>
              <span class="text-xs text-gray-500">(可选，用于测试签名验证)</span>
            </div>
            <div class="flex gap-2">
              <input
                v-model="signatureToken"
                type="text"
                placeholder="输入签名Token（从管理后台颁发获取）"
                class="flex-1 px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
              <button
                @click="clearSignatureToken"
                class="px-4 py-2 bg-red-600 text-white text-sm rounded-lg hover:bg-red-700 transition"
              >
                清除
              </button>
            </div>
            <div class="mt-2 text-xs text-gray-600">
              <span v-if="signatureToken" class="text-green-600">✅ 已配置签名Token，将在请求头中携带</span>
              <span v-else class="text-gray-500">ℹ️ 未配置签名Token，将使用传统方式（需要共享密钥）</span>
            </div>
          </div>

          <!-- 存储桶配置 -->
          <div>
            <div class="flex items-center gap-2 mb-3">
              <h3 class="text-base font-semibold text-gray-900">🪣 存储桶配置</h3>
              <span class="text-xs text-gray-500">(必需，指定文件上传的目标存储桶)</span>
            </div>
            <div class="flex gap-2">
              <input
                v-model="bucketName"
                type="text"
                placeholder="例如: test-default, my-production-bucket"
                class="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
              <button
                @click="resetBucket"
                class="px-4 py-2 bg-gray-600 text-white text-sm rounded-lg hover:bg-gray-700 transition"
              >
                重置
              </button>
            </div>
            <div class="mt-2 text-xs text-gray-600">
              <span v-if="bucketName" class="text-green-600">✅ 当前存储桶: <strong>{{ bucketName }}</strong></span>
              <span v-else class="text-red-600">⚠️ 请配置存储桶名称</span>
            </div>
            <div class="mt-1 text-xs text-gray-500">
              ℹ️ 存储桶命名规则：3-63个字符，仅小写字母、数字和连字符，不能以连字符开头或结尾
            </div>
          </div>
        </div>

        <!-- 上传区域 -->
        <div class="mb-8">
          <div class="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center hover:border-blue-500 transition-colors">
            <input
              type="file"
              ref="fileInput"
              @change="handleFileChange"
              class="hidden"
              id="file-upload"
            />
            <label
              for="file-upload"
              class="cursor-pointer flex flex-col items-center"
            >
              <svg
                class="w-12 h-12 text-gray-400 mb-3"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                />
              </svg>
              <span class="text-sm text-gray-600">点击选择文件或拖拽文件到此处</span>
              <span class="text-xs text-gray-500 mt-2">支持所有文件类型</span>
            </label>
          </div>

          <!-- 已选择的文件信息 -->
          <div v-if="selectedFile" class="mt-4 p-4 bg-blue-50 rounded-lg">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-3">
                <svg class="w-8 h-8 text-blue-500" fill="currentColor" viewBox="0 0 20 20">
                  <path
                    fill-rule="evenodd"
                    d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"
                    clip-rule="evenodd"
                  />
                </svg>
                <div>
                  <p class="text-sm font-medium text-gray-900">{{ selectedFile.name }}</p>
                  <p class="text-xs text-gray-500">{{ formatFileSize(selectedFile.size) }} - {{ selectedFile.type || '未知类型' }}</p>
                </div>
              </div>
              <button
                @click="clearFile"
                class="text-red-500 hover:text-red-700 text-sm"
              >
                移除
              </button>
            </div>
          </div>

          <!-- 上传按钮 -->
          <div v-if="selectedFile" class="mt-4 flex gap-4">
            <button
              @click="uploadFile"
              :disabled="uploading"
              class="flex-1 bg-blue-600 text-white py-3 px-6 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors font-medium"
            >
              <span v-if="!uploading">上传文件</span>
              <span v-else class="flex items-center justify-center">
                <svg class="animate-spin h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                上传中...
              </span>
            </button>
          </div>

          <!-- 上传进度 -->
          <div v-if="uploading" class="mt-4">
            <div class="bg-gray-200 rounded-full h-2 overflow-hidden">
              <div
                class="bg-blue-600 h-full transition-all duration-300"
                :style="{ width: `${uploadProgress}%` }"
              ></div>
            </div>
            <p class="text-sm text-gray-600 mt-2 text-center">{{ uploadProgress }}%</p>
          </div>
        </div>

        <!-- 上传结果 -->
        <div v-if="uploadResult" class="mb-8">
          <div :class="uploadResult.success ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'" class="border rounded-lg p-4">
            <div class="flex items-start">
              <svg
                v-if="uploadResult.success"
                class="w-6 h-6 text-green-500 mr-3 flex-shrink-0"
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                <path
                  fill-rule="evenodd"
                  d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                  clip-rule="evenodd"
                />
              </svg>
              <svg
                v-else
                class="w-6 h-6 text-red-500 mr-3 flex-shrink-0"
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                <path
                  fill-rule="evenodd"
                  d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                  clip-rule="evenodd"
                />
              </svg>
              <div class="flex-1">
                <h3 :class="uploadResult.success ? 'text-green-800' : 'text-red-800'" class="font-medium">
                  {{ uploadResult.success ? '上传成功' : '上传失败' }}
                </h3>
                <p :class="uploadResult.success ? 'text-green-700' : 'text-red-700'" class="text-sm mt-1">
                  {{ uploadResult.message }}
                </p>
                <div v-if="uploadResult.success && uploadResult.url" class="mt-3 space-y-2">
                  <div class="text-sm">
                    <span class="font-medium text-gray-700">文件URL:</span>
                    <a :href="uploadResult.url" target="_blank" class="text-blue-600 hover:text-blue-800 ml-2 break-all">
                      {{ uploadResult.url }}
                    </a>
                  </div>
                  <div v-if="uploadResult.key" class="text-sm">
                    <span class="font-medium text-gray-700">文件Key:</span>
                    <span class="ml-2 text-gray-600">{{ uploadResult.key }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 上传历史 -->
        <div v-if="uploadHistory.length > 0" class="mt-8">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">上传历史</h2>
          <div class="space-y-3">
            <div
              v-for="(item, index) in uploadHistory"
              :key="index"
              class="border rounded-lg p-4 hover:bg-gray-50 transition-colors"
            >
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="flex items-center space-x-2">
                    <span class="font-medium text-gray-900">{{ item.fileName }}</span>
                    <span
                      :class="item.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
                      class="px-2 py-1 text-xs rounded-full"
                    >
                      {{ item.success ? '成功' : '失败' }}
                    </span>
                  </div>
                  <p class="text-sm text-gray-500 mt-1">{{ item.timestamp }}</p>
                  <div v-if="item.url" class="mt-2">
                    <a :href="item.url" target="_blank" class="text-sm text-blue-600 hover:text-blue-800 break-all">
                      {{ item.url }}
                    </a>
                  </div>
                  <p v-if="!item.success" class="text-sm text-red-600 mt-1">{{ item.error }}</p>
                </div>
                <button
                  @click="removeFromHistory(index)"
                  class="text-gray-400 hover:text-red-500 ml-4"
                  title="从历史中移除"
                >
                  <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      fill-rule="evenodd"
                      d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                      clip-rule="evenodd"
                    />
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <button
            @click="clearHistory"
            class="mt-4 text-sm text-red-600 hover:text-red-800"
          >
            清空历史记录
          </button>
        </div>

        <!-- 说明信息 -->
        <div class="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h3 class="text-sm font-medium text-blue-900 mb-2">测试说明</h3>
          <ul class="text-sm text-blue-800 space-y-1 list-disc list-inside">
            <li>此测试使用后端 API 直接上传（POST /api/upload/rustfs）</li>
            <li>文件通过 FormData 发送到后端，由后端处理上传到存储服务</li>
            <li>支持自动去重，相同文件（基于哈希）只存储一次</li>
            <li>与直传签名方式相比，这种方式流量经过后端服务器</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import axios from 'axios'

// 签名Token配置
const signatureToken = ref(localStorage.getItem('signatureToken') || '')

// 存储桶配置
const bucketName = ref(localStorage.getItem('bucketName') || 'test-default')

// 清除签名Token
const clearSignatureToken = () => {
  signatureToken.value = ''
  localStorage.removeItem('signatureToken')
}

// 重置存储桶
const resetBucket = () => {
  bucketName.value = 'test-default'
}

// 监听signatureToken变化，自动保存到localStorage
watch(signatureToken, (newVal) => {
  if (newVal) {
    localStorage.setItem('signatureToken', newVal)
  }
})

// 监听bucketName变化，自动保存到localStorage
watch(bucketName, (newVal) => {
  if (newVal) {
    localStorage.setItem('bucketName', newVal)
  }
})

// 状态
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResult = ref<{
  success: boolean
  message: string
  url?: string
  key?: string
} | null>(null)

// 上传历史
interface UploadHistoryItem {
  fileName: string
  timestamp: string
  success: boolean
  url?: string
  error?: string
}
const uploadHistory = ref<UploadHistoryItem[]>([])

// 文件选择处理
const fileInput = ref<HTMLInputElement>()
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0]
    uploadResult.value = null
  }
}

// 清除选择的文件
const clearFile = () => {
  selectedFile.value = null
  uploadProgress.value = 0
  uploadResult.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 上传文件
const uploadFile = async () => {
  if (!selectedFile.value) return

  uploading.value = true
  uploadProgress.value = 0
  uploadResult.value = null

  try {
    // 创建 FormData
    const formData = new FormData()
    formData.append('file', selectedFile.value)

    console.log('开始上传文件:', {
      name: selectedFile.value.name,
      size: selectedFile.value.size,
      type: selectedFile.value.type
    })

    // 发送请求到后端 API
    const headers: any = {
      'Content-Type': 'multipart/form-data',
      'X-Bucket': bucketName.value,  // 必需：指定目标存储桶
      'X-Folder': 'api-uploads'      // 可选：指定目标文件夹
    }
    
    // 如果配置了签名Token，添加到请求头
    if (signatureToken.value) {
      headers['X-Signature-Token'] = signatureToken.value
      console.log('🔐 使用签名Token:', signatureToken.value.substring(0, 20) + '...')
    }
    
    console.log('📦 上传到存储桶:', bucketName.value)
    
    const response = await axios.post('/api/upload/rustfs', formData, {
      headers,
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          uploadProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        }
      }
    })

    console.log('上传响应:', response.data)

    // 处理成功响应
    uploadResult.value = {
      success: response.data.success,
      message: response.data.message || '上传成功',
      url: response.data.url,
      key: response.data.key
    }

    // 添加到历史记录
    uploadHistory.value.unshift({
      fileName: selectedFile.value.name,
      timestamp: new Date().toLocaleString('zh-CN'),
      success: true,
      url: response.data.url
    })

    // 清除选择的文件
    setTimeout(() => {
      clearFile()
    }, 1000)
  } catch (error: any) {
    console.error('上传失败:', error)
    
    const errorMessage = error.response?.data?.message || error.message || '上传失败'
    
    uploadResult.value = {
      success: false,
      message: errorMessage
    }

    // 添加失败记录到历史
    if (selectedFile.value) {
      uploadHistory.value.unshift({
        fileName: selectedFile.value.name,
        timestamp: new Date().toLocaleString('zh-CN'),
        success: false,
        error: errorMessage
      })
    }
  } finally {
    uploading.value = false
  }
}

// 从历史中移除
const removeFromHistory = (index: number) => {
  uploadHistory.value.splice(index, 1)
}

// 清空历史记录
const clearHistory = () => {
  if (confirm('确定要清空所有历史记录吗？')) {
    uploadHistory.value = []
  }
}
</script>
