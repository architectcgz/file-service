<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
    <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
      <div class="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex justify-between items-center">
        <h3 class="text-xl font-semibold text-gray-900">签名详情</h3>
        <button
          @click="$emit('close')"
          class="text-gray-400 hover:text-gray-600 transition"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div class="p-6 space-y-4">
        <!-- 基本信息 -->
        <div class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-3">基本信息</h4>
          <dl class="grid grid-cols-2 gap-3">
            <div>
              <dt class="text-sm text-gray-500">签名ID</dt>
              <dd class="text-sm font-medium text-gray-900">{{ signature.id }}</dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">状态</dt>
              <dd>
                <span :class="getStatusClass(signature.status)" class="px-2 py-1 text-xs font-medium rounded-full">
                  {{ getStatusText(signature.status) }}
                </span>
              </dd>
            </div>
            <div class="col-span-2">
              <dt class="text-sm text-gray-500">签名Token</dt>
              <dd class="text-sm font-mono text-gray-900 break-all bg-white p-2 rounded border">
                {{ signature.signatureToken }}
              </dd>
            </div>
          </dl>
        </div>

        <!-- 调用方信息 -->
        <div class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-3">调用方信息</h4>
          <dl class="grid grid-cols-2 gap-3">
            <div>
              <dt class="text-sm text-gray-500">服务名称</dt>
              <dd class="text-sm font-medium text-gray-900">{{ signature.callerService }}</dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">服务ID</dt>
              <dd class="text-sm text-gray-900">{{ signature.callerServiceId || '-' }}</dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">创建者IP</dt>
              <dd class="text-sm text-gray-900">{{ signature.creatorIp || '-' }}</dd>
            </div>
          </dl>
        </div>

        <!-- 权限配置 -->
        <div class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-3">权限配置</h4>
          <dl class="grid grid-cols-2 gap-3">
            <div>
              <dt class="text-sm text-gray-500">允许的操作</dt>
              <dd>
                <span class="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-800">
                  {{ signature.allowedOperation }}
                </span>
              </dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">允许的文件类型</dt>
              <dd class="text-sm text-gray-900">{{ signature.allowedFileTypes || '全部' }}</dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">最大文件大小</dt>
              <dd class="text-sm text-gray-900">
                {{ signature.maxFileSize ? formatFileSize(signature.maxFileSize) : '无限制' }}
              </dd>
            </div>
          </dl>
        </div>

        <!-- 使用情况 -->
        <div class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-3">使用情况</h4>
          <dl class="grid grid-cols-2 gap-3">
            <div>
              <dt class="text-sm text-gray-500">使用次数</dt>
              <dd class="text-sm font-medium text-gray-900">
                {{ signature.usageCount }}
                <span v-if="signature.maxUsageCount > 0" class="text-gray-500">
                  / {{ signature.maxUsageCount }}
                </span>
                <span v-else class="text-gray-500">/ 无限制</span>
              </dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">最后使用时间</dt>
              <dd class="text-sm text-gray-900">
                {{ signature.lastUsedAt ? formatDate(signature.lastUsedAt) : '未使用' }}
              </dd>
            </div>
          </dl>
        </div>

        <!-- 时间信息 -->
        <div class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-3">时间信息</h4>
          <dl class="grid grid-cols-2 gap-3">
            <div>
              <dt class="text-sm text-gray-500">创建时间</dt>
              <dd class="text-sm text-gray-900">{{ formatDate(signature.createdAt) }}</dd>
            </div>
            <div>
              <dt class="text-sm text-gray-500">过期时间</dt>
              <dd class="text-sm text-gray-900">{{ formatDate(signature.expiresAt) }}</dd>
            </div>
            <div v-if="signature.revokedAt" class="col-span-2">
              <dt class="text-sm text-gray-500">撤销时间</dt>
              <dd class="text-sm text-gray-900">{{ formatDate(signature.revokedAt) }}</dd>
            </div>
          </dl>
        </div>

        <!-- 撤销信息 -->
        <div v-if="signature.status === 'revoked'" class="bg-red-50 rounded-lg p-4">
          <h4 class="font-semibold text-red-900 mb-3">撤销信息</h4>
          <dl class="space-y-2">
            <div>
              <dt class="text-sm text-red-700">撤销原因</dt>
              <dd class="text-sm text-red-900">{{ signature.revokeReason || '-' }}</dd>
            </div>
          </dl>
        </div>

        <!-- 备注 -->
        <div v-if="signature.notes" class="bg-gray-50 rounded-lg p-4">
          <h4 class="font-semibold text-gray-900 mb-2">备注</h4>
          <p class="text-sm text-gray-700">{{ signature.notes }}</p>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="border-t border-gray-200 px-6 py-4 flex justify-end space-x-3">
        <button
          @click="$emit('close')"
          class="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition"
        >
          关闭
        </button>
        <button
          v-if="signature.status === 'active'"
          @click="handleRevoke"
          class="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition"
        >
          撤销签名
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { adminApi } from '@/api/admin'

const props = defineProps<{
  signature: any
}>()

const emit = defineEmits(['close', 'refresh'])

const handleRevoke = async () => {
  if (!confirm('确定要撤销此签名吗？')) return

  try {
    const reason = prompt('请输入撤销原因:') || '管理员手动撤销'
    await adminApi.revokeSignature(props.signature.signatureToken, reason)
    alert('撤销成功')
    emit('refresh')
    emit('close')
  } catch (error) {
    alert('撤销失败')
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

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>
