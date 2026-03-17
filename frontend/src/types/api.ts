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

export interface ServicesListResponse {
  success: boolean
  message: string
  data?: {
    services: string[]
    count: number
  }
}

export interface ServiceListResponse {
  success: boolean
  message: string
  data: {
    name: string
  }[]
}

export interface Service {
  id: string
  name: string
  description?: string
  createTime: string
  updateTime: string
  isEnabled: boolean
  bucketCount: number
}

export interface Bucket {
  id: string
  name: string
  description?: string
  serviceId: string
  serviceName: string
  createTime: string
  updateTime: string
  isEnabled: boolean
  fileCount: number
}

export interface FileInfo {
  id: string
  fileHash: string
  fileKey: string
  fileUrl: string
  originalFileName: string
  fileSize: number
  contentType: string
  fileExtension: string
  referenceCount: number
  uploaderId?: string
  createTime: string
  lastAccessTime: string
}

export interface CreateTableRequest {
  service: string
}

export interface CreateServiceRequest {
  name: string
  description?: string
}

export interface CreateBucketRequest {
  bucketName: string
  description?: string
}

export interface DeleteFileRequest {
  fileKey: string
  bucketName?: string | null
}

// 上传相关类型定义
export interface DirectUploadSignatureRequest {
  fileName: string
  fileType: string
  bucket: string
  fileHash?: string
  fileSize?: number
  service?: string
  folder?: string
}

export interface DirectUploadSignature {
  url: string
  key: string
  policy: string
  signature: string
  accessKeyId: string
  fields: Record<string, string>
}

export interface DirectUploadSignatureResponse {
  success: boolean
  needUpload: boolean
  signature?: DirectUploadSignature
  existingFileUrl?: string
  fileUrl?: string
  fileKey?: string
  fileHash?: string
  fileCategory?: string
  maxSizeBytes?: number
  message?: string
}

export interface RecordDirectUploadRequest {
  fileHash: string
  fileKey: string
  fileUrl: string
  originalFileName: string
  fileSize: number
  contentType: string
  bucketName?: string
  service?: string
}

export interface RecordDirectUploadResponse {
  success: boolean
  message?: string
  fileId?: string
}

