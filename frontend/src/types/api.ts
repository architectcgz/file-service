// API 响应类型定义
export interface AdminResponseDto {
  success: boolean
  message: string
  data?: any
}

export interface LoginResponse {
  success: boolean
  message: string
  data?: {
    username: string
    sessionTimeoutMinutes: number
  }
}

export interface StatusResponse {
  success: boolean
  message: string
  data?: {
    isLoggedIn: boolean
    username?: string
    loginTime?: string
    sessionTimeoutMinutes: number
  }
}

export interface BucketsListResponse {
  success: boolean
  message: string
  data?: {
    buckets: string[]
    count: number
  }
}

export interface CreateTableRequest {
  service: string
}

export interface CreateBucketRequest {
  bucketName: string
}

export interface DeleteFileRequest {
  fileKey: string
  bucketName?: string | null
}

