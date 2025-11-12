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

  // 删除存储桶
  deleteBucket(bucketName: string): Promise<AdminResponseDto> {
    return api.post('/buckets/delete', { bucketName } as CreateBucketRequest)
  },

  // 列出存储桶
  listBuckets(): Promise<BucketsListResponse> {
    return api.get('/buckets/list')
  },

  // 列出指定存储桶中的文件夹
  listFolders(bucketName: string): Promise<AdminResponseDto> {
    return api.get(`/buckets/${bucketName}/folders`)
  },
  
  // 列出指定文件夹下的文件
  listFilesInFolder(bucketName: string, folder: string): Promise<AdminResponseDto> {
    return api.get(`/buckets/${bucketName}/folders/${encodeURIComponent(folder)}/files`)
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
  }
}
