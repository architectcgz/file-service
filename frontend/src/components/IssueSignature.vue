<template>
  <div class="bg-white rounded-lg shadow-sm p-6">
    <h2 class="text-2xl font-bold text-gray-900 mb-6">颁发新签名</h2>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <!-- 错误提示 -->
      <div v-if="errorMessage" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
        {{ errorMessage }}
      </div>

      <!-- 成功提示 -->
      <div v-if="successMessage" class="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-lg">
        <p class="font-medium mb-2">{{ successMessage }}</p>
        <div v-if="issuedToken" class="mt-3 p-3 bg-white rounded border border-green-300">
          <p class="text-sm text-gray-600 mb-1">签名Token:</p>
          <p class="text-sm font-mono text-gray-900 break-all">{{ issuedToken }}</p>
          <button
            type="button"
            @click="copyToken"
            class="mt-2 text-sm text-green-600 hover:text-green-700"
          >
            📋 复制Token
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- 调用方服务名称 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            调用方服务名称 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.callerService"
            type="text"
            required
            placeholder="例如: blog-api"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>

        <!-- 调用方服务ID -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            调用方服务ID（可选）
          </label>
          <input
            v-model="form.callerServiceId"
            type="text"
            placeholder="例如: blog-instance-001"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>

        <!-- 允许的操作类型 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            允许的操作类型 <span class="text-red-500">*</span>
          </label>
          <select
            v-model="form.allowedOperation"
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          >
            <option value="upload">上传 (upload)</option>
            <option value="download">下载 (download)</option>
            <option value="delete">删除 (delete)</option>
            <option value="list">列表 (list)</option>
            <option value="*">所有操作 (*)</option>
          </select>
        </div>

        <!-- 允许的文件类型 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            允许的文件类型（可选）
          </label>
          <input
            v-model="form.allowedFileTypes"
            type="text"
            placeholder="例如: image,document,video（逗号分隔）"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
          <p class="mt-1 text-xs text-gray-500">留空表示允许所有文件类型，使用 * 表示所有类型</p>
        </div>

        <!-- 最大文件大小 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            最大文件大小（字节）
          </label>
          <input
            v-model.number="form.maxFileSize"
            type="number"
            min="0"
            placeholder="例如: 10485760 (10MB)"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
          <p class="mt-1 text-xs text-gray-500">留空表示无限制</p>
        </div>

        <!-- 过期时间 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            有效期 <span class="text-red-500">*</span>
          </label>
          <div class="space-y-2">
            <select
              v-model="expiryType"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            >
              <option value="custom">自定义时长</option>
              <option value="permanent">长期有效</option>
            </select>
            <input
              v-if="expiryType === 'custom'"
              v-model.number="form.expiryMinutes"
              type="number"
              min="1"
              required
              placeholder="输入分钟数，默认: 60"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
            <div v-else class="text-sm text-gray-600 bg-yellow-50 border border-yellow-200 rounded-lg p-3">
              ⚠️ 签名将在 10 年后过期（5,256,000 分钟）
            </div>
          </div>
        </div>

        <!-- 最大使用次数 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">
            最大使用次数
          </label>
          <input
            v-model.number="form.maxUsageCount"
            type="number"
            min="0"
            placeholder="0 表示无限制"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>
      </div>

      <!-- 备注 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-2">
          备注（可选）
        </label>
        <textarea
          v-model="form.notes"
          rows="3"
          placeholder="例如: 用于用户上传头像"
          class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        ></textarea>
      </div>

      <!-- 提交按钮 -->
      <div class="flex space-x-4">
        <button
          type="submit"
          :disabled="loading"
          class="px-6 py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition"
        >
          <span v-if="loading">颁发中...</span>
          <span v-else>颁发签名</span>
        </button>
        <button
          type="button"
          @click="resetForm"
          class="px-6 py-3 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition"
        >
          重置
        </button>
      </div>
    </form>

    <!-- 快速预设 -->
    <div class="mt-8 pt-8 border-t border-gray-200">
      <h3 class="text-lg font-semibold text-gray-900 mb-4">快速预设</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <button
          @click="applyPreset('upload-image')"
          class="p-4 border-2 border-gray-200 rounded-lg hover:border-indigo-500 transition text-left"
        >
          <div class="font-medium text-gray-900">📷 图片上传</div>
          <div class="text-sm text-gray-500 mt-1">图片类型，10MB，60分钟</div>
        </button>
        <button
          @click="applyPreset('upload-document')"
          class="p-4 border-2 border-gray-200 rounded-lg hover:border-indigo-500 transition text-left"
        >
          <div class="font-medium text-gray-900">📄 文档上传</div>
          <div class="text-sm text-gray-500 mt-1">文档类型，50MB，120分钟</div>
        </button>
        <button
          @click="applyPreset('one-time')"
          class="p-4 border-2 border-gray-200 rounded-lg hover:border-indigo-500 transition text-left"
        >
          <div class="font-medium text-gray-900">🔒 一次性上传</div>
          <div class="text-sm text-gray-500 mt-1">仅使用1次，30分钟</div>
        </button>
        <button
          @click="applyPreset('permanent')"
          class="p-4 border-2 border-yellow-300 bg-yellow-50 rounded-lg hover:border-yellow-500 transition text-left"
        >
          <div class="font-medium text-gray-900">♾️ 长期有效</div>
          <div class="text-sm text-gray-500 mt-1">10年有效期，无限次使用</div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { adminApi } from '@/api/admin'

const emit = defineEmits(['issued'])

const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const issuedToken = ref('')
const expiryType = ref<'custom' | 'permanent'>('custom')

const form = reactive({
  callerService: '',
  callerServiceId: '',
  allowedOperation: 'upload',
  allowedFileTypes: '',
  maxFileSize: null as number | null,
  expiryMinutes: 60,
  maxUsageCount: 0,
  notes: ''
})

const handleSubmit = async () => {
  errorMessage.value = ''
  successMessage.value = ''
  issuedToken.value = ''
  loading.value = true

  try {
    // 如果选择长期有效，设置为10年（5,256,000分钟）
    const submitData = {
      ...form,
      expiryMinutes: expiryType.value === 'permanent' ? 5256000 : form.expiryMinutes
    }
    
    const result = await adminApi.issueSignature(submitData)
    if (result.success) {
      successMessage.value = result.message || '签名颁发成功'
      issuedToken.value = result.signatureToken
      emit('issued')
      // 3秒后重置表单
      setTimeout(() => {
        resetForm()
      }, 3000)
    } else {
      errorMessage.value = result.message || '颁发失败'
    }
  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '颁发失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.callerService = ''
  form.callerServiceId = ''
  form.allowedOperation = 'upload'
  form.allowedFileTypes = ''
  form.maxFileSize = null
  form.expiryMinutes = 60
  form.maxUsageCount = 0
  form.notes = ''
  expiryType.value = 'custom'
  errorMessage.value = ''
  successMessage.value = ''
  issuedToken.value = ''
}

const applyPreset = (preset: string) => {
  switch (preset) {
    case 'upload-image':
      expiryType.value = 'custom'
      form.allowedOperation = 'upload'
      form.allowedFileTypes = 'image'
      form.maxFileSize = 10485760 // 10MB
      form.expiryMinutes = 60
      form.maxUsageCount = 0
      form.notes = '图片上传预设'
      break
    case 'upload-document':
      expiryType.value = 'custom'
      form.allowedOperation = 'upload'
      form.allowedFileTypes = 'document'
      form.maxFileSize = 52428800 // 50MB
      form.expiryMinutes = 120
      form.maxUsageCount = 0
      form.notes = '文档上传预设'
      break
    case 'one-time':
      expiryType.value = 'custom'
      form.allowedOperation = 'upload'
      form.allowedFileTypes = ''
      form.maxFileSize = null
      form.expiryMinutes = 30
      form.maxUsageCount = 1
      form.notes = '一次性上传预设'
      break
    case 'permanent':
      expiryType.value = 'permanent' // 使用长期有效
      form.allowedOperation = 'upload'
      form.allowedFileTypes = ''
      form.maxFileSize = null
      form.maxUsageCount = 0
      form.notes = '长期有效签名'
      break
  }
}

const copyToken = () => {
  navigator.clipboard.writeText(issuedToken.value)
  alert('Token已复制到剪贴板')
}
</script>
