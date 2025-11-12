<template>
  <div class="service-management">
    <div class="header">
      <h1>服务管理</h1>
      <button @click="showCreateServiceDialog = true" class="btn-primary">
        <span class="icon">+</span>
        创建服务
      </button>
    </div>

    <!-- 服务列表 -->
    <div class="services-container">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="services.length === 0" class="empty">
        暂无服务，请创建一个服务
      </div>
      <div v-else class="services-grid">
        <div 
          v-for="service in services" 
          :key="service.id" 
          class="service-card"
          :class="{ active: selectedService?.id === service.id }"
          @click="selectService(service)"
        >
          <div class="service-header">
            <h3>{{ service.name }}</h3>
            <span class="badge">{{ service.bucketCount }} 个存储桶</span>
          </div>
          <p v-if="service.description" class="service-desc">{{ service.description }}</p>
          <div class="service-footer">
            <span class="time">创建于 {{ formatDate(service.createTime) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 存储桶和文件管理区域 -->
    <div v-if="selectedService" class="detail-container">
      <div class="detail-header">
        <h2>{{ selectedService.name }} - 存储桶管理</h2>
        <button @click="showCreateBucketDialog = true" class="btn-secondary">
          <span class="icon">+</span>
          创建存储桶
        </button>
      </div>

      <!-- 存储桶列表 -->
      <div class="buckets-section">
        <div v-if="bucketsLoading" class="loading">加载存储桶中...</div>
        <div v-else-if="buckets.length === 0" class="empty">
          该服务下暂无存储桶
        </div>
        <div v-else class="buckets-list">
          <div 
            v-for="bucket in buckets" 
            :key="bucket.id" 
            class="bucket-item"
            :class="{ active: selectedBucket?.id === bucket.id }"
            @click="selectBucket(bucket)"
          >
            <div class="bucket-info">
              <h4>{{ bucket.name }}</h4>
              <p v-if="bucket.description" class="bucket-desc">{{ bucket.description }}</p>
              <div class="bucket-meta">
                <span class="file-count">{{ bucket.fileCount }} 个文件</span>
                <span class="time">{{ formatDate(bucket.createTime) }}</span>
              </div>
            </div>
            <div class="bucket-actions">
              <button @click.stop="selectBucket(bucket)" class="btn-link">查看文件夹</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 文件夹列表 -->
      <div v-if="selectedBucket && !selectedFolder" class="folders-section">
        <h3>{{ selectedBucket.name }} - 文件夹列表</h3>
        <div v-if="foldersLoading" class="loading">加载文件夹中...</div>
        <div v-else-if="folders.length === 0" class="empty">
          该存储桶中暂无文件夹
        </div>
        <div v-else class="folders-list">
          <div 
            v-for="folder in folders" 
            :key="folder" 
            class="folder-item"
            @click="selectFolder(folder)"
          >
            <div class="folder-icon">📁</div>
            <div class="folder-info">
              <h4>{{ folder }}</h4>
            </div>
          </div>
        </div>
      </div>

      <!-- 文件列表 -->
      <div v-if="selectedFolder" class="files-section">
        <div class="breadcrumb">
          <button @click="selectedFolder = null; files = []" class="btn-link">← 返回文件夹列表</button>
          <span class="breadcrumb-text">{{ selectedBucket?.name }} / {{ selectedFolder }}</span>
        </div>
        <h3>文件列表</h3>
        <div v-if="filesLoading" class="loading">加载文件中...</div>
        <div v-else-if="files.length === 0" class="empty">
          该文件夹中暂无文件
        </div>
        <div v-else>
          <table class="files-table">
            <thead>
              <tr>
                <th>文件名</th>
                <th>大小</th>
                <th>最后修改时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in files" :key="file.key">
                <td class="file-name">{{ getFileName(file.key) }}</td>
                <td>{{ formatFileSize(file.size) }}</td>
                <td>{{ formatDate(file.lastModified) }}</td>
                <td>
                  <div class="file-actions">
                    <a v-if="file.url" :href="file.url" target="_blank" class="btn-link">预览</a>
                    <a v-if="file.downloadUrl" :href="file.downloadUrl" class="btn-link">下载</a>
                    <span v-if="!file.url && !file.downloadUrl" class="text-gray">无法访问</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 创建服务对话框 -->
    <div v-if="showCreateServiceDialog" class="dialog-overlay" @click="showCreateServiceDialog = false">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h3>创建服务</h3>
          <button @click="showCreateServiceDialog = false" class="btn-close">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>服务名称 *</label>
            <input 
              v-model="newService.name" 
              type="text" 
              placeholder="例如: blog, market, admin"
              class="input"
            />
          </div>
          <div class="form-group">
            <label>服务描述</label>
            <textarea 
              v-model="newService.description" 
              placeholder="可选的服务描述"
              class="textarea"
              rows="3"
            ></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button @click="showCreateServiceDialog = false" class="btn-secondary">取消</button>
          <button @click="createService" :disabled="!newService.name" class="btn-primary">创建</button>
        </div>
      </div>
    </div>

    <!-- 创建存储桶对话框 -->
    <div v-if="showCreateBucketDialog" class="dialog-overlay" @click="showCreateBucketDialog = false">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h3>创建存储桶</h3>
          <button @click="showCreateBucketDialog = false" class="btn-close">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>存储桶名称 *</label>
            <input 
              v-model="newBucket.name" 
              type="text" 
              placeholder="例如: images, videos, documents"
              class="input"
            />
          </div>
          <div class="form-group">
            <label>存储桶描述</label>
            <textarea 
              v-model="newBucket.description" 
              placeholder="可选的存储桶描述"
              class="textarea"
              rows="3"
            ></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button @click="showCreateBucketDialog = false" class="btn-secondary">取消</button>
          <button @click="createBucket" :disabled="!newBucket.name" class="btn-primary">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { Service, Bucket } from '@/types/api'

const loading = ref(false)
const bucketsLoading = ref(false)
const filesLoading = ref(false)

const services = ref<Service[]>([])
const selectedService = ref<Service | null>(null)

const buckets = ref<Bucket[]>([])
const selectedBucket = ref<Bucket | null>(null)

const folders = ref<string[]>([])
const selectedFolder = ref<string | null>(null)
const foldersLoading = ref(false)

const files = ref<any[]>([])

const showCreateServiceDialog = ref(false)
const showCreateBucketDialog = ref(false)

const newService = ref({
  name: '',
  description: ''
})

const newBucket = ref({
  name: '',
  description: ''
})

onMounted(() => {
  loadServices()
})

async function loadServices() {
  loading.value = true
  try {
    const response = await adminApi.listServices()
    if (response.success && Array.isArray(response.data)) {
      services.value = response.data
    }
  } catch (error: any) {
    console.error('加载服务列表失败:', error)
    alert('加载服务列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

async function selectService(service: Service) {
  selectedService.value = service
  selectedBucket.value = null
  selectedFolder.value = null
  folders.value = []
  files.value = []
  await loadBuckets(service.id)
}

async function loadBuckets(serviceId: string) {
  bucketsLoading.value = true
  try {
    const response = await adminApi.getBucketsByService(serviceId)
    if (response.success && Array.isArray(response.data)) {
      buckets.value = response.data
    }
  } catch (error: any) {
    console.error('加载存储桶列表失败:', error)
    alert('加载存储桶列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    bucketsLoading.value = false
  }
}

async function selectBucket(bucket: Bucket) {
  selectedBucket.value = bucket
  selectedFolder.value = null
  files.value = []
  await loadFolders(bucket)
}

async function loadFolders(bucket: Bucket) {
  if (!bucket) return
  
  foldersLoading.value = true
  try {
    const response = await adminApi.listFolders(bucket.name)
    if (response.success && response.data) {
      folders.value = response.data.folders || []
    }
  } catch (error: any) {
    console.error('加载文件夹列表失败:', error)
    alert('加载文件夹列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    foldersLoading.value = false
  }
}

async function selectFolder(folder: string) {
  selectedFolder.value = folder
  await loadFilesInFolder(folder)
}

async function loadFilesInFolder(folder: string) {
  if (!selectedBucket.value) return
  
  filesLoading.value = true
  try {
    const response = await adminApi.listFilesInFolder(selectedBucket.value.name, folder)
    if (response.success && response.data) {
      files.value = response.data.files || []
    }
  } catch (error: any) {
    console.error('加载文件列表失败:', error)
    alert('加载文件列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    filesLoading.value = false
  }
}

async function createService() {
  if (!newService.value.name.trim()) {
    alert('请输入服务名称')
    return
  }

  try {
    const response = await adminApi.createService({
      name: newService.value.name.trim(),
      description: newService.value.description.trim() || undefined
    })
    
    if (response.success) {
      alert('服务创建成功')
      showCreateServiceDialog.value = false
      newService.value = { name: '', description: '' }
      await loadServices()
    } else {
      alert('创建失败: ' + response.message)
    }
  } catch (error: any) {
    console.error('创建服务失败:', error)
    alert('创建服务失败: ' + (error.response?.data?.message || error.message))
  }
}

async function createBucket() {
  if (!newBucket.value.name.trim() || !selectedService.value) {
    alert('请输入存储桶名称')
    return
  }

  try {
    const response = await adminApi.createBucketInService(selectedService.value.id, {
      bucketName: newBucket.value.name.trim(),
      description: newBucket.value.description.trim() || undefined
    })
    
    if (response.success) {
      alert('存储桶创建成功')
      showCreateBucketDialog.value = false
      newBucket.value = { name: '', description: '' }
      await loadBuckets(selectedService.value.id)
    } else {
      alert('创建失败: ' + response.message)
    }
  } catch (error: any) {
    console.error('创建存储桶失败:', error)
    alert('创建存储桶失败: ' + (error.response?.data?.message || error.message))
  }
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function getFileName(key: string): string {
  const parts = key.split('/')
  return parts[parts.length - 1]
}
</script>

<style scoped>
.service-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
}

.services-container {
  margin-bottom: 40px;
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.service-card {
  background: white;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.service-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.service-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.service-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.service-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.badge {
  background: #e0e7ff;
  color: #3b82f6;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.service-desc {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 10px;
}

.service-footer {
  color: #9ca3af;
  font-size: 12px;
}

.detail-container {
  background: white;
  border-radius: 8px;
  padding: 24px;
  border: 1px solid #e5e7eb;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #e5e7eb;
}

.detail-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
}

.buckets-section {
  margin-bottom: 30px;
}

.buckets-list {
  display: grid;
  gap: 16px;
}

.bucket-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.bucket-item:hover {
  background: #f3f4f6;
  border-color: #3b82f6;
}

.bucket-item.active {
  background: #eff6ff;
  border-color: #3b82f6;
}

.bucket-info h4 {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.bucket-desc {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 8px;
}

.bucket-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #9ca3af;
}

.file-count {
  color: #3b82f6;
  font-weight: 500;
}

.folders-section {
  margin-top: 30px;
}

.folders-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.folders-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.folder-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.folder-item:hover {
  background: #f3f4f6;
  border-color: #3b82f6;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.folder-icon {
  font-size: 32px;
}

.folder-info h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.files-section {
  margin-top: 30px;
}

.files-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.breadcrumb-text {
  color: #6b7280;
  font-size: 14px;
}

.files-table {
  width: 100%;
  border-collapse: collapse;
}

.files-table th {
  background: #f9fafb;
  padding: 12px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 2px solid #e5e7eb;
}

.files-table td {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.file-name {
  font-weight: 500;
  color: #1a1a1a;
}

.file-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.page-info {
  color: #6b7280;
  font-size: 14px;
}

.btn-primary, .btn-secondary, .btn-link, .btn-pagination {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-secondary:hover {
  background: #e5e7eb;
}

.btn-link {
  background: transparent;
  color: #3b82f6;
  padding: 4px 8px;
  text-decoration: none;
  border-radius: 4px;
  transition: all 0.2s;
}

.btn-link:hover {
  background: #eff6ff;
  color: #2563eb;
}

.btn-pagination {
  background: #f3f4f6;
  color: #374151;
}

.btn-pagination:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.icon {
  font-size: 18px;
  margin-right: 4px;
}

.loading, .empty {
  text-align: center;
  padding: 40px;
  color: #9ca3af;
}

/* 对话框样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: white;
  border-radius: 8px;
  width: 500px;
  max-width: 90%;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.dialog-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.btn-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #9ca3af;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.btn-close:hover {
  background: #f3f4f6;
  color: #374151;
}

.dialog-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #374151;
  font-size: 14px;
}

.input, .textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
}

.input:focus, .textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

.textarea {
  resize: vertical;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
}

.time {
  color: #9ca3af;
  font-size: 12px;
}

.text-gray {
  color: #9ca3af;
  font-size: 14px;
}
</style>
