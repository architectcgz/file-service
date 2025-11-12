<template>
  <div class="test-upload-container">
    <div class="upload-card">
      <h1>文件直传测试</h1>
      
      <!-- 服务选择 -->
      <div class="form-group">
        <label for="service-select">服务名称：</label>
        <select 
          id="service-select" 
          v-model="selectedService" 
          class="form-control"
          @change="onServiceChange"
        >
          <option value="">请选择服务</option>
          <option v-for="service in services" :key="service.id" :value="service.name">
            {{ service.name }}
          </option>
          <option value="custom">创建新服务</option>
        </select>
        <small class="form-text">服务名称对应数据库表，如 'blog' 对应 'uploaded_files_blog' 表</small>
      </div>

      <!-- 自定义服务名输入 -->
      <div v-if="selectedService === 'custom'" class="form-group">
        <label for="custom-service">新服务名称：</label>
        <input 
          id="custom-service"
          v-model="customService" 
          type="text" 
          class="form-control"
          placeholder="输入新服务名称（如: blog, market, admin等）"
        />
        <small class="form-text">仅支持字母、数字和下划线</small>
      </div>

      <!-- 存储桶选择 -->
      <div class="form-group">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <label for="bucket-select" style="margin-bottom: 0;">选择存储桶：</label>
          <button 
            @click="syncBuckets" 
            class="btn btn-sync"
            :disabled="syncing"
            title="同步 RustFS 中的存储桶到数据库"
          >
            {{ syncing ? '同步中...' : '🔄 同步存储桶' }}
          </button>
        </div>
        <select 
          id="bucket-select" 
          v-model="selectedBucket" 
          class="form-control"
          @change="onBucketChange"
        >
          <option value="">请选择存储桶</option>
          <option v-for="bucket in buckets" :key="bucket" :value="bucket">
            {{ bucket }}
          </option>
          <option value="custom">自定义存储桶名</option>
        </select>
        <small class="form-text">存储桶是S3的概念，用于在同一服务下区分不同类型的文件</small>
      </div>

      <!-- 自定义存储桶名输入 -->
      <div v-if="selectedBucket === 'custom'" class="form-group">
        <label for="custom-bucket">自定义存储桶名：</label>
        <input 
          id="custom-bucket"
          v-model="customBucket" 
          type="text" 
          class="form-control"
          placeholder="输入存储桶名称"
        />
      </div>

      <!-- 文件夹选择 -->
      <div class="form-group">
        <label for="folder-select">选择文件夹：</label>
        <select 
          id="folder-select" 
          v-model="selectedFolder" 
          class="form-control"
          @change="onFolderChange"
          :disabled="!actualBucket"
        >
          <option value="">自动判断（根据文件类型）</option>
          <option v-for="folder in folders" :key="folder" :value="folder">
            {{ folder }}
          </option>
          <option value="custom">自定义文件夹</option>
        </select>
        <small class="form-text">
          {{ actualBucket ? '文件夹是存储桶下的子目录，用于组织文件' : '请先选择存储桶' }}
        </small>
      </div>

      <!-- 自定义文件夹名输入 -->
      <div v-if="selectedFolder === 'custom'" class="form-group">
        <label for="custom-folder">自定义文件夹名：</label>
        <input 
          id="custom-folder"
          v-model="customFolder" 
          type="text" 
          class="form-control"
          placeholder="输入文件夹名称（如: temp, backup等）"
        />
        <small class="form-text">仅支持字母、数字、下划线和连字符</small>
      </div>

      <!-- 文件选择 -->
      <div class="form-group">
        <label for="file-input">选择文件：</label>
        <input 
          id="file-input"
          type="file" 
          @change="onFileChange" 
          class="form-control"
          ref="fileInput"
        />
      </div>

      <!-- 文件信息显示 -->
      <div v-if="selectedFile" class="file-info">
        <h3>文件信息</h3>
        <div class="info-item">
          <strong>文件名：</strong> {{ selectedFile.name }}
        </div>
        <div class="info-item">
          <strong>文件类型：</strong> {{ selectedFile.type }}
        </div>
        <div class="info-item">
          <strong>文件大小：</strong> {{ formatFileSize(selectedFile.size) }}
        </div>
        <div v-if="fileHash" class="info-item">
          <strong>文件哈希：</strong> 
          <code>{{ fileHash }}</code>
        </div>
      </div>

      <!-- 上传按钮 -->
      <div class="button-group">
        <button 
          @click="uploadFile" 
          :disabled="!canUpload || uploading"
          class="btn btn-primary"
        >
          {{ uploading ? '上传中...' : '开始上传' }}
        </button>
        <button 
          @click="resetForm" 
          class="btn btn-secondary"
          :disabled="uploading"
        >
          重置
        </button>
      </div>

      <!-- 上传进度 -->
      <div v-if="uploading" class="progress-container">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progress + '%' }"></div>
        </div>
        <div class="progress-text">{{ progress }}%</div>
        <div class="status-text">{{ statusText }}</div>
      </div>

      <!-- 上传结果 -->
      <div v-if="uploadResult" class="upload-result">
        <div :class="['result-message', uploadResult.success ? 'success' : 'error']">
          {{ uploadResult.message }}
        </div>
        <div v-if="uploadResult.success && uploadResult.fileUrl" class="result-details">
          <div class="info-item">
            <strong>文件URL：</strong>
            <a :href="uploadResult.fileUrl" target="_blank" class="file-link">
              {{ uploadResult.fileUrl }}
            </a>
          </div>
          <div class="info-item">
            <strong>文件Key：</strong> 
            <code>{{ uploadResult.fileKey }}</code>
          </div>
          <div v-if="uploadResult.fileCategory" class="info-item">
            <strong>文件分类：</strong> {{ uploadResult.fileCategory }}
          </div>
          
          <!-- 如果是图片，显示预览 -->
          <div v-if="uploadResult.fileUrl && isImageFile(selectedFile)" class="image-preview">
            <h4>图片预览：</h4>
            <img :src="uploadResult.fileUrl" alt="上传的图片" />
          </div>
        </div>
      </div>

      <!-- 日志信息 -->
      <div v-if="logs.length > 0" class="logs-container">
        <h3>日志信息</h3>
        <div class="logs">
          <div v-for="(log, index) in logs" :key="index" :class="['log-item', log.type]">
            <span class="log-time">{{ log.time }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { uploadApi } from '@/api/upload'
import { adminApi } from '@/api/admin'

interface UploadResult {
  success: boolean
  message: string
  fileUrl?: string
  fileKey?: string
  fileCategory?: string
}

interface LogItem {
  time: string
  message: string
  type: 'info' | 'success' | 'error'
}

// 状态
interface ServiceInfo {
  id: string
  name: string
  description?: string
}

const services = ref<ServiceInfo[]>([])
const selectedService = ref<string>('')
const customService = ref<string>('')
const buckets = ref<string[]>([])
const selectedBucket = ref<string>('')
const customBucket = ref<string>('')
const folders = ref<string[]>([])
const selectedFolder = ref<string>('')
const customFolder = ref<string>('')
const selectedFile = ref<File | null>(null)
const fileHash = ref<string>('')
const uploading = ref(false)
const syncing = ref(false)
const progress = ref(0)
const statusText = ref('')
const uploadResult = ref<UploadResult | null>(null)
const logs = ref<LogItem[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 计算属性
const actualService = computed(() => {
  return selectedService.value === 'custom' ? customService.value : selectedService.value
})

const actualBucket = computed(() => {
  return selectedBucket.value === 'custom' ? customBucket.value : selectedBucket.value
})

const actualFolder = computed(() => {
  return selectedFolder.value === 'custom' ? customFolder.value : selectedFolder.value
})

const canUpload = computed(() => {
  return selectedFile.value && actualBucket.value && actualService.value && !uploading.value
})

// 方法
const addLog = (message: string, type: 'info' | 'success' | 'error' = 'info') => {
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
  logs.value.push({ time, message, type })
}

const loadServices = async () => {
  try {
    const response = await adminApi.listServices()
    if (response.success && response.data) {
      // response.data 是服务对象数组，保存完整对象
      if (Array.isArray(response.data)) {
        services.value = response.data
        addLog(`成功加载 ${response.data.length} 个服务`, 'success')
      }
    }
  } catch (error: any) {
    addLog(`加载服务列表失败: ${error.message}`, 'error')
    console.error('加载服务失败:', error)
  }
}

const loadBuckets = async (serviceId: string) => {
  if (!serviceId) {
    buckets.value = []
    return
  }
  
  try {
    addLog(`正在加载服务的存储桶...`, 'info')
    const response = await adminApi.getBucketsByService(serviceId)
    if (response.success && response.data) {
      if (Array.isArray(response.data)) {
        buckets.value = response.data.map((b: any) => b.name)
        addLog(`成功加载 ${response.data.length} 个存储桶`, 'success')
      }
    }
  } catch (error: any) {
    addLog(`加载存储桶列表失败: ${error.message}`, 'error')
    console.error('加载存储桶失败:', error)
    buckets.value = []
  }
}

// 监听服务选择变化
watch(selectedService, async (newService) => {
  // 清空存储桶和文件夹
  selectedBucket.value = ''
  buckets.value = []
  selectedFolder.value = ''
  folders.value = []
  
  if (newService && newService !== 'custom') {
    // 查找服务ID
    const service = services.value.find(s => s.name === newService)
    if (service) {
      await loadBuckets(service.id)
    }
  }
})

const loadFolders = async (bucketName: string) => {
  try {
    addLog(`正在加载存储桶 '${bucketName}' 的文件夹...`, 'info')
    const response = await adminApi.listFolders(bucketName)
    if (response.success && response.data) {
      folders.value = response.data.folders || []
      addLog(`成功加载 ${response.data.count} 个文件夹`, 'success')
    }
  } catch (error: any) {
    addLog(`加载文件夹列表失败: ${error.message}`, 'error')
    console.error('加载文件夹失败:', error)
    folders.value = []
  }
}

const syncBuckets = async () => {
  try {
    syncing.value = true
    addLog('开始同步 RustFS 存储桶到数据库...', 'info')
    
    const response = await adminApi.syncBuckets()
    
    if (response.success && response.data) {
      const { syncedCount, syncedBuckets, totalRustFSBuckets, totalDBBuckets } = response.data
      
      if (syncedCount > 0) {
        addLog(`成功同步 ${syncedCount} 个存储桶: ${syncedBuckets.join(', ')}`, 'success')
        addLog(`RustFS 总数: ${totalRustFSBuckets}, 数据库总数: ${totalDBBuckets}`, 'info')
        
        // 如果已选择服务，重新加载该服务的存储桶
        if (selectedService.value && selectedService.value !== 'custom') {
          const service = services.value.find(s => s.name === selectedService.value)
          if (service) {
            await loadBuckets(service.id)
          }
        }
      } else {
        addLog(`所有存储桶已同步，无需操作`, 'info')
      }
    }
  } catch (error: any) {
    addLog(`同步存储桶失败: ${error.message}`, 'error')
    console.error('同步存储桶失败:', error)
  } finally {
    syncing.value = false
  }
}

const onServiceChange = () => {
  uploadResult.value = null
}

const onBucketChange = async () => {
  uploadResult.value = null
  selectedFolder.value = ''
  customFolder.value = ''
  folders.value = []
  
  // 如果选择了具体的存储桶（不是 custom 或空），则加载文件夹列表
  if (actualBucket.value && selectedBucket.value !== 'custom') {
    await loadFolders(actualBucket.value)
  }
}

const onFolderChange = () => {
  uploadResult.value = null
}

const onFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  
  if (file) {
    selectedFile.value = file
    uploadResult.value = null
    addLog(`选择文件: ${file.name}`, 'info')
    
    // 计算文件哈希
    try {
      statusText.value = '计算文件哈希...'
      const hash = await calculateFileHash(file)
      fileHash.value = hash
      addLog(`文件哈希计算完成: ${hash.substring(0, 16)}...`, 'success')
    } catch (error: any) {
      addLog(`计算文件哈希失败: ${error.message}`, 'error')
    }
  }
}

const calculateFileHash = async (file: File): Promise<string> => {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
}

const uploadFile = async () => {
  if (!selectedFile.value || !actualBucket.value) {
    return
  }

  uploading.value = true
  progress.value = 0
  uploadResult.value = null

  try {
    // 0. 确保服务的表已创建
    if (actualService.value) {
      try {
        addLog(`检查服务 '${actualService.value}' 的表...`, 'info')
        const tableResult = await adminApi.createTable(actualService.value)
        if (tableResult.message?.includes('已存在')) {
          addLog(`服务 '${actualService.value}' 的表已存在`, 'info')
        } else {
          addLog(`服务 '${actualService.value}' 的表创建成功`, 'success')
        }
      } catch (error: any) {
        // 表可能已存在，继续执行
        if (error.response?.data?.message?.includes('已存在')) {
          addLog(`服务 '${actualService.value}' 的表已存在`, 'info')
        } else {
          addLog(`创建表失败: ${error.message}，尝试继续...`, 'info')
        }
      }
    }

    // 1. 获取直传签名
    addLog('正在获取上传签名...', 'info')
    statusText.value = '获取上传签名...'
    progress.value = 10

    const signatureResponse = await uploadApi.getDirectUploadSignature({
      fileName: selectedFile.value.name,
      fileType: selectedFile.value.type,
      bucket: actualBucket.value,
      fileHash: fileHash.value,
      fileSize: selectedFile.value.size,
      service: actualService.value,
      folder: actualFolder.value || undefined
    })

    addLog('上传签名获取成功', 'success')
    progress.value = 20

    // 2. 检查是否需要上传（文件可能已存在）
    if (!signatureResponse.needUpload) {
      addLog('文件已存在，无需上传', 'success')
      uploadResult.value = {
        success: true,
        message: '文件已存在，无需重复上传',
        fileUrl: signatureResponse.existingFileUrl,
        fileKey: signatureResponse.fileKey
      }
      progress.value = 100
      statusText.value = '完成'
      return
    }

    // 3. 使用签名上传文件
    addLog('开始上传文件到存储服务...', 'info')
    statusText.value = '上传文件中...'
    progress.value = 30

    await uploadApi.uploadFileWithSignature(selectedFile.value, signatureResponse.signature)
    
    addLog('文件上传成功', 'success')
    progress.value = 80

    // 4. 记录上传信息
    addLog('记录上传信息...', 'info')
    statusText.value = '记录上传信息...'

    const recordResponse = await uploadApi.recordDirectUpload({
      fileHash: signatureResponse.fileHash || fileHash.value,
      fileKey: signatureResponse.fileKey || signatureResponse.signature?.key || '',
      fileUrl: signatureResponse.fileUrl || '',
      originalFileName: selectedFile.value.name,
      fileSize: selectedFile.value.size,
      contentType: selectedFile.value.type,
      bucketName: actualBucket.value,
      service: actualService.value
    })

    progress.value = 100
    statusText.value = '上传完成'
    
    addLog(`上传完成！文件ID: ${recordResponse.fileId}`, 'success')

    uploadResult.value = {
      success: true,
      message: '文件上传成功！',
      fileUrl: signatureResponse.fileUrl,
      fileKey: signatureResponse.fileKey,
      fileCategory: signatureResponse.fileCategory
    }

  } catch (error: any) {
    console.error('上传失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '上传失败'
    addLog(`上传失败: ${errorMessage}`, 'error')
    uploadResult.value = {
      success: false,
      message: `上传失败: ${errorMessage}`
    }
  } finally {
    uploading.value = false
  }
}

const resetForm = () => {
  selectedFile.value = null
  fileHash.value = ''
  uploadResult.value = null
  progress.value = 0
  statusText.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
  addLog('表单已重置', 'info')
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

const isImageFile = (file: File | null): boolean => {
  return file?.type.startsWith('image/') || false
}

// 初始化
onMounted(() => {
  loadServices()
  addLog('页面初始化完成', 'info')
})
</script>

<style scoped>
.test-upload-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.upload-card {
  background: white;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

h1 {
  margin-bottom: 30px;
  color: #333;
  font-size: 28px;
}

h3 {
  margin: 20px 0 15px;
  color: #555;
  font-size: 18px;
}

h4 {
  margin: 15px 0 10px;
  color: #666;
  font-size: 16px;
}

.form-group {
  margin-bottom: 20px;
}

label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #555;
}

.form-control {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.form-control:focus {
  outline: none;
  border-color: #4CAF50;
}

.form-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #6c757d;
  line-height: 1.4;
}

select.form-control {
  cursor: pointer;
}

.file-info {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 20px;
}

.info-item {
  margin-bottom: 8px;
  line-height: 1.6;
}

.info-item:last-child {
  margin-bottom: 0;
}

code {
  background: #e9ecef;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
}

.button-group {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.3s, transform 0.1s;
}

.btn:active {
  transform: translateY(1px);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #4CAF50;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #45a049;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #5a6268;
}

.btn-sync {
  padding: 6px 12px;
  font-size: 13px;
  background: #17a2b8;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.btn-sync:hover:not(:disabled) {
  background: #138496;
}

.btn-sync:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.progress-container {
  margin-bottom: 20px;
}

.progress-bar {
  width: 100%;
  height: 24px;
  background: #e9ecef;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4CAF50, #45a049);
  transition: width 0.3s ease;
  border-radius: 12px;
}

.progress-text {
  text-align: center;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.status-text {
  text-align: center;
  color: #666;
  font-size: 14px;
}

.upload-result {
  margin-bottom: 20px;
  padding: 15px;
  border-radius: 4px;
}

.result-message {
  padding: 12px;
  border-radius: 4px;
  margin-bottom: 15px;
  font-weight: 600;
}

.result-message.success {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.result-message.error {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.result-details {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
}

.file-link {
  color: #007bff;
  text-decoration: none;
  word-break: break-all;
}

.file-link:hover {
  text-decoration: underline;
}

.image-preview {
  margin-top: 15px;
}

.image-preview img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.logs-container {
  background: #1e1e1e;
  padding: 15px;
  border-radius: 4px;
  color: #d4d4d4;
}

.logs-container h3 {
  color: #d4d4d4;
  margin-top: 0;
}

.logs {
  max-height: 300px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 13px;
}

.log-item {
  padding: 4px 0;
  display: flex;
  gap: 10px;
}

.log-time {
  color: #858585;
  min-width: 70px;
}

.log-message {
  flex: 1;
}

.log-item.info .log-message {
  color: #4ec9b0;
}

.log-item.success .log-message {
  color: #6a9955;
}

.log-item.error .log-message {
  color: #f48771;
}
</style>
