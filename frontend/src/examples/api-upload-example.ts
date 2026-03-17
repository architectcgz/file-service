/**
 * API 上传示例
 * 
 * 演示如何使用后端 API 直接上传文件（非直传签名方式）
 */

import { uploadApi } from '@/api/upload'

// ============ 示例 1: 基础上传 ============
export async function basicUploadExample(file: File) {
  try {
    const result = await uploadApi.uploadFileViaApi(file)
    
    if (result.success) {
      console.log('上传成功!')
      console.log('文件URL:', result.url)
      console.log('文件Key:', result.key)
      return result.url
    } else {
      console.error('上传失败:', result.message)
      throw new Error(result.message)
    }
  } catch (error) {
    console.error('上传出错:', error)
    throw error
  }
}

// ============ 示例 2: 带进度回调的上传 ============
export async function uploadWithProgress(file: File) {
  try {
    const result = await uploadApi.uploadFileViaApi(file, (progress) => {
      console.log(`上传进度: ${progress}%`)
      // 这里可以更新UI进度条
      // updateProgressBar(progress)
    })
    
    if (result.success) {
      console.log('上传完成!')
      return result.url
    } else {
      throw new Error(result.message || '上传失败')
    }
  } catch (error) {
    console.error('上传失败:', error)
    throw error
  }
}

// ============ 示例 3: 批量上传文件 ============
export async function batchUploadExample(files: File[]) {
  const results = []
  
  for (const file of files) {
    try {
      console.log(`正在上传: ${file.name}`)
      
      const result = await uploadApi.uploadFileViaApi(file, (progress) => {
        console.log(`${file.name}: ${progress}%`)
      })
      
      if (result.success) {
        results.push({
          fileName: file.name,
          success: true,
          url: result.url
        })
        console.log(`✓ ${file.name} 上传成功`)
      } else {
        results.push({
          fileName: file.name,
          success: false,
          error: result.message
        })
        console.log(`✗ ${file.name} 上传失败:`, result.message)
      }
    } catch (error: any) {
      results.push({
        fileName: file.name,
        success: false,
        error: error.message
      })
      console.error(`✗ ${file.name} 上传出错:`, error)
    }
  }
  
  return results
}

// ============ 示例 4: 在 Vue 组件中使用 ============
export const vueComponentExample = `
<script setup lang="ts">
import { ref } from 'vue'
import { uploadApi } from '@/api/upload'

const uploading = ref(false)
const progress = ref(0)
const fileUrl = ref('')

const handleFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  
  uploading.value = true
  progress.value = 0
  
  try {
    const result = await uploadApi.uploadFileViaApi(file, (p) => {
      progress.value = p
    })
    
    if (result.success) {
      fileUrl.value = result.url || ''
      alert('上传成功!')
    } else {
      alert('上传失败: ' + result.message)
    }
  } catch (error) {
    console.error('上传出错:', error)
    alert('上传出错')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div>
    <input 
      type="file" 
      @change="handleFileUpload" 
      :disabled="uploading"
    />
    <div v-if="uploading">
      上传进度: {{ progress }}%
    </div>
    <div v-if="fileUrl">
      文件URL: <a :href="fileUrl" target="_blank">{{ fileUrl }}</a>
    </div>
  </div>
</template>
`

// ============ 示例 5: 错误处理和重试 ============
export async function uploadWithRetry(
  file: File, 
  maxRetries = 3
): Promise<string> {
  let lastError: Error | null = null
  
  for (let i = 0; i < maxRetries; i++) {
    try {
      console.log(`尝试上传 (${i + 1}/${maxRetries})...`)
      
      const result = await uploadApi.uploadFileViaApi(file, (progress) => {
        console.log(`第${i + 1}次尝试进度: ${progress}%`)
      })
      
      if (result.success && result.url) {
        console.log('上传成功!')
        return result.url
      } else {
        throw new Error(result.message || '上传失败')
      }
    } catch (error: any) {
      lastError = error
      console.error(`第${i + 1}次尝试失败:`, error.message)
      
      // 如果还有重试机会，等待一段时间后重试
      if (i < maxRetries - 1) {
        const waitTime = Math.pow(2, i) * 1000 // 指数退避: 1s, 2s, 4s
        console.log(`等待 ${waitTime}ms 后重试...`)
        await new Promise(resolve => setTimeout(resolve, waitTime))
      }
    }
  }
  
  throw new Error(`上传失败，已重试 ${maxRetries} 次。最后错误: ${lastError?.message}`)
}

// ============ API 上传 vs 直传签名对比 ============
export const comparisonNotes = `
API 上传方式（uploadFileViaApi）:
✓ 优点:
  - 实现简单，一个API调用即可
  - 后端统一处理，便于监控和日志记录
  - 可以在后端做额外的处理（如图片压缩、病毒扫描等）
  - 自动支持去重（基于文件哈希）

✗ 缺点:
  - 文件流量经过后端服务器，增加服务器带宽和负载
  - 受服务器请求大小限制
  - 相对较慢（多了一次中转）

直传签名方式（getDirectUploadSignature + uploadFileWithSignature）:
✓ 优点:
  - 文件直接上传到存储服务，不经过后端
  - 减轻后端服务器负载
  - 上传速度更快
  - 适合大文件上传

✗ 缺点:
  - 实现相对复杂（需要获取签名、上传文件、记录完成）
  - 需要配置CORS等
  - 错误处理更复杂

建议:
- 小文件（< 10MB）: 使用 API 上传方式
- 大文件（> 10MB）: 使用直传签名方式
- 需要后端处理的文件: 使用 API 上传方式
- 高并发场景: 使用直传签名方式
`
