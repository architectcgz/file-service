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

    const formData = new FormData()
    
    // 按照S3 POST策略的要求，先添加所有字段
    formData.append('key', signature.key)
    formData.append('policy', signature.policy)
    formData.append('x-amz-algorithm', signature.fields['x-amz-algorithm'])
    formData.append('x-amz-credential', signature.fields['x-amz-credential'])
    formData.append('x-amz-date', signature.fields['x-amz-date'])
    formData.append('x-amz-signature', signature.signature)
    
    // 最后添加文件
    formData.append('file', file)

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
  }
}
