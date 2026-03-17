import axios, { AxiosInstance } from 'axios'
import type {
  DirectUploadSignatureRequest,
  DirectUploadSignatureResponse,
  RecordDirectUploadRequest,
  RecordDirectUploadResponse
} from '@/types/api'

const api: AxiosInstance = axios.create({
  baseURL: '/api/upload',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const uploadApi = {
  // 获取直传签名
  getDirectUploadSignature(
    request: DirectUploadSignatureRequest
  ): Promise<DirectUploadSignatureResponse> {
    return api.post('/direct-signature', request).then(res => res.data)
  },

  // 使用直传签名上传文件
  async uploadFileWithSignature(
    file: File,
    signature: DirectUploadSignatureResponse['signature']
  ): Promise<void> {
    if (!signature) {
      throw new Error('签名信息不存在')
    }

    console.log('上传签名信息:', signature)
    console.log('文件信息:', { name: file.name, size: file.size, type: file.type })

    const formData = new FormData()
    
    // 按照S3 POST策略的要求，严格按照签名中的字段顺序添加
    // 首先添加key字段（必需）
    formData.append('key', signature.key)
    
    // 添加其他策略字段
    if (signature.fields) {
      Object.entries(signature.fields).forEach(([key, value]) => {
        // 避免重复添加key字段
        if (key !== 'key') {
          formData.append(key, value)
        }
      })
    }
    
    // 添加策略和签名
    formData.append('policy', signature.policy)
    formData.append('x-amz-signature', signature.signature)
    
    // 最后添加文件（必须是最后一个字段）
    formData.append('file', file)

    // 调试：打印FormData内容
    console.log('FormData字段:')
    for (const [key, value] of formData.entries()) {
      console.log(`${key}:`, value)
    }

    // 直接上传到S3兼容的存储
    await axios.post(signature.url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 记录直传完成的文件
  recordDirectUpload(
    request: RecordDirectUploadRequest
  ): Promise<RecordDirectUploadResponse> {
    return api.post('/record-direct-upload', request).then(res => res.data)
  },

  // 通过后端 API 直接上传文件（非直传签名方式）
  async uploadFileViaApi(
    file: File,
    onProgress?: (progress: number) => void
  ): Promise<{
    success: boolean
    url?: string
    key?: string
    message?: string
  }> {
    const formData = new FormData()
    formData.append('file', file)

    const response = await api.post('/rustfs', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(progress)
        }
      }
    })

    return response.data
  }
}
