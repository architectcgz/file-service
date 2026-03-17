import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios'
import type {
  AdminResponseDto,
  LoginResponse,
  StatusResponse,
  BucketsListResponse,
  ServicesListResponse,
  CreateTableRequest,
  CreateBucketRequest,
  DeleteFileRequest
} from '@/types/api'

const api: AxiosInstance = axios.create({
  baseURL: '/api/admin',
  withCredentials: true, // 支持 session cookie
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 可以在这里添加 API Key
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 如果需要使用 API Key，可以从 localStorage 获取
    const apiKey = localStorage.getItem('adminApiKey')
    if (apiKey && config.headers) {
      config.headers['X-Admin-Api-Key'] = apiKey
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // 未授权，清除登录状态
      localStorage.removeItem('isLoggedIn')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const adminApi = {
  // 登录
  login(username: string, password: string): Promise<LoginResponse> {
    return api.post('/login', { username, password })
  },

  // 登出
  logout(): Promise<AdminResponseDto> {
    return api.post('/logout')
  },

  // 检查状态
  getStatus(): Promise<StatusResponse> {
    return api.get('/status')
  },

  // 创建存储表
  createTable(service: string): Promise<AdminResponseDto> {
    return api.post('/tables/create', { service } as CreateTableRequest)
  },

  // 创建存储桶
  createBucket(bucketName: string): Promise<AdminResponseDto> {
    return api.post('/buckets/create', { bucketName } as CreateBucketRequest)
  },

  // 删除服务
  deleteService(serviceId: string): Promise<AdminResponseDto> {
    return api.delete(`/services/${serviceId}`)
  },

  // 删除存储桶
  deleteBucket(bucketId: string): Promise<AdminResponseDto> {
    return api.delete(`/buckets/${bucketId}`)
  },
  
  // 删除文件夹
  deleteFolder(bucketId: string, folderName: string): Promise<AdminResponseDto> {
    return api.delete(`/buckets/${bucketId}/folders`, { params: { folderName } })
  },

  // 列出存储桶
  listBuckets(): Promise<BucketsListResponse> {
    return api.get('/buckets/list')
  },

  // 列出指定存储桶中的文件夹
  listFolders(serviceName: string, bucketName: string): Promise<AdminResponseDto> {
    return api.get(`/services/${serviceName}/buckets/${bucketName}/folders`)
  },
  
  // 列出指定文件夹下的文件（支持分页）
  listFilesInFolder(
    serviceName: string,
    bucketName: string, 
    folder: string, 
    pageSize: number = 20, 
    continuationToken?: string
  ): Promise<AdminResponseDto> {
    const params = new URLSearchParams()
    params.append('pageSize', pageSize.toString())
    if (continuationToken) {
      params.append('continuationToken', continuationToken)
    }
    return api.get(`/services/${serviceName}/buckets/${bucketName}/folders/${encodeURIComponent(folder)}/files?${params.toString()}`)
  },

  // 同步 RustFS 存储桶到数据库
  syncBuckets(): Promise<AdminResponseDto> {
    return api.post('/buckets/sync')
  },

  // 获取服务列表
  listServices(): Promise<AdminResponseDto> {
    return api.get('/services')
  },

  // 创建服务
  createService(data: { name: string; description?: string }): Promise<AdminResponseDto> {
    return api.post('/services/create', data)
  },

  // 创建存储桶
  createBucketInService(serviceId: string, data: { bucketName: string; description?: string }): Promise<AdminResponseDto> {
    return api.post(`/services/${serviceId}/buckets/create`, data)
  },

  // 获取服务下的存储桶列表
  getBucketsByService(serviceId: string): Promise<AdminResponseDto> {
    return api.get(`/services/${serviceId}/buckets`)
  },

  // 获取存储桶中的文件列表
  getFilesByBucket(bucketId: string, page: number = 1, pageSize: number = 20): Promise<AdminResponseDto> {
    return api.get(`/buckets/${bucketId}/files`, { params: { page, pageSize } })
  },

  // 列出服务
  listServicesList(): Promise<ServicesListResponse> {
    return api.get('/services/list')
  },

  // 删除文件
  deleteFile(fileKey: string, bucketName: string | null = null): Promise<AdminResponseDto> {
    return api.post('/files/delete', { fileKey, bucketName } as DeleteFileRequest)
  },

  // ==================== 签名管理 API ====================
  
  // 颁发签名
  issueSignature(data: any): Promise<any> {
    return api.post('/signatures/issue', data)
  },

  // 获取签名列表
  getSignatures(params: any): Promise<any> {
    return api.get('/signatures', { params })
  },

  // 获取签名详情
  getSignature(signatureToken: string): Promise<any> {
    return api.get(`/signatures/${signatureToken}`)
  },

  // 撤销签名
  revokeSignature(signatureToken: string, reason: string): Promise<any> {
    return api.post(`/signatures/${signatureToken}/revoke`, { reason })
  },

  // 批量撤销签名
  batchRevokeSignatures(signatureTokens: string[], reason: string): Promise<any> {
    return api.post('/signatures/batch-revoke', { signatureTokens, reason })
  },

  // 更新签名过期时间
  updateSignatureExpiry(signatureToken: string, newExpiryTime: string): Promise<any> {
    return api.put(`/signatures/${signatureToken}/expiry`, { newExpiryTime })
  },

  // 清理过期签名
  cleanExpiredSignatures(): Promise<any> {
    return api.post('/signatures/clean-expired')
  },

  // 获取签名统计
  getSignatureStatistics(): Promise<any> {
    return api.get('/signatures/statistics')
  }
}
