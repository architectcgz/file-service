import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios'
import type {
  AdminResponseDto,
  LoginResponse,
  StatusResponse,
  BucketsListResponse,
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

  // 删除存储桶
  deleteBucket(bucketName: string): Promise<AdminResponseDto> {
    return api.post('/buckets/delete', { bucketName } as CreateBucketRequest)
  },

  // 列出存储桶
  listBuckets(): Promise<BucketsListResponse> {
    return api.get('/buckets/list')
  },

  // 删除文件
  deleteFile(fileKey: string, bucketName: string | null = null): Promise<AdminResponseDto> {
    return api.post('/files/delete', { fileKey, bucketName } as DeleteFileRequest)
  }
}

