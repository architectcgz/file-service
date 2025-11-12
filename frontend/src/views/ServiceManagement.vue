<template>
  <div class="page-container">
    <!-- 顶部导航栏 -->
    <nav class="top-nav">
      <div class="nav-content">
        <div class="nav-left">
          <h1 class="nav-title">文件服务管理后台</h1>
        </div>
        <div class="nav-right">
          <router-link to="/test-upload" class="nav-link">
            测试上传
          </router-link>
          <button @click="handleLogout" class="btn-logout">
            登出
          </button>
        </div>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="service-management">
      <!-- 面包屑导航 -->
      <div v-if="currentView !== 'services'" class="breadcrumb">
        <button @click="goToServices" class="breadcrumb-item">服务列表</button>
        <span v-if="selectedService" class="breadcrumb-separator">/</span>
        <button v-if="selectedService && currentView !== 'buckets'" @click="goToBuckets" class="breadcrumb-item">
          {{ selectedService.name }}
        </button>
        <span v-if="selectedService && currentView === 'buckets'" class="breadcrumb-current">
          {{ selectedService.name }}
        </span>
        <span v-if="selectedBucket" class="breadcrumb-separator">/</span>
        <button v-if="selectedBucket && currentView !== 'folders'" @click="goToFolders" class="breadcrumb-item">
          {{ selectedBucket.name }}
        </button>
        <span v-if="selectedBucket && currentView === 'folders'" class="breadcrumb-current">
          {{ selectedBucket.name }}
        </span>
        <span v-if="selectedFolder" class="breadcrumb-separator">/</span>
        <span v-if="selectedFolder" class="breadcrumb-current">
          {{ selectedFolder }}
        </span>
      </div>

      <!-- 页面标题和操作按钮 -->
      <div class="header">
        <div class="header-left">
          <button v-if="currentView !== 'services'" @click="goBack" class="btn-back">← 返回</button>
          <h1>
            <span v-if="currentView === 'services'">服务列表</span>
            <span v-else-if="currentView === 'buckets'">存储桶列表</span>
            <span v-else-if="currentView === 'folders'">文件夹列表</span>
            <span v-else-if="currentView === 'files'">文件列表</span>
          </h1>
        </div>
        <button v-if="currentView === 'services'" @click="showCreateServiceDialog = true" class="btn-primary">
          <span class="icon">+</span>
          创建服务
        </button>
        <button v-if="currentView === 'buckets'" @click="showCreateBucketDialog = true" class="btn-primary">
          <span class="icon">+</span>
          创建存储桶
        </button>
      </div>

    <!-- 服务列表视图 -->
    <div v-if="currentView === 'services'" class="view-container">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="services.length === 0" class="empty">
        暂无服务，请创建一个服务
      </div>
      <div v-else class="grid-list">
        <div 
          v-for="service in services" 
          :key="service.id" 
          class="grid-item"
          @click="selectService(service)"
        >
          <div class="item-icon">📦</div>
          <div class="item-content">
            <h3 class="item-title">{{ service.name }}</h3>
            <p v-if="service.description" class="item-desc">{{ service.description }}</p>
            <div class="item-meta">
              <span class="meta-badge">{{ service.bucketCount }} 个存储桶</span>
              <span class="meta-time">{{ formatDate(service.createTime) }}</span>
            </div>
          </div>
          <div class="item-arrow">→</div>
        </div>
      </div>
    </div>

    <!-- 存储桶列表视图 -->
    <div v-if="currentView === 'buckets'" class="view-container">
      <div v-if="bucketsLoading" class="loading">加载中...</div>
      <div v-else-if="buckets.length === 0" class="empty">
        该服务下暂无存储桶
      </div>
      <div v-else class="grid-list">
        <div 
          v-for="bucket in buckets" 
          :key="bucket.id" 
          class="grid-item"
          @click="selectBucket(bucket)"
        >
          <div class="item-icon">🗂️</div>
          <div class="item-content">
            <h3 class="item-title">{{ bucket.name }}</h3>
            <p v-if="bucket.description" class="item-desc">{{ bucket.description }}</p>
            <div class="item-meta">
              <span class="meta-badge">{{ bucket.fileCount }} 个文件</span>
              <span class="meta-time">{{ formatDate(bucket.createTime) }}</span>
            </div>
          </div>
          <div class="item-arrow">→</div>
        </div>
      </div>
    </div>

    <!-- 文件夹列表视图 -->
    <div v-if="currentView === 'folders'" class="view-container">
      <div v-if="foldersLoading" class="loading">加载中...</div>
      <div v-else-if="folders.length === 0" class="empty">
        该存储桶中暂无文件夹
      </div>
      <div v-else class="grid-list">
        <div 
          v-for="folder in folders" 
          :key="folder" 
          class="grid-item"
          @click="selectFolder(folder)"
        >
          <div class="item-icon">📁</div>
          <div class="item-content">
            <h3 class="item-title">{{ folder }}</h3>
          </div>
          <div class="item-arrow">→</div>
        </div>
      </div>
    </div>

    <!-- 文件列表视图 -->
    <div v-if="currentView === 'files'" class="view-container">
      <div v-if="filesLoading" class="loading">加载中...</div>
      <div v-else-if="files.length === 0" class="empty">
        该文件夹中暂无文件
      </div>
      <div v-else>
        <div class="files-stats">
          共 {{ totalFiles }} 个文件，当前显示第 {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, totalFiles) }} 个
        </div>
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
        
        <!-- 分页控件 -->
        <div v-if="currentPage > 1 || hasMoreFiles" class="pagination">
          <button 
            @click="changePage(currentPage - 1)" 
            :disabled="!canGoPrev"
            class="pagination-btn"
          >
            ‹ 上一页
          </button>
          
          <div class="pagination-info">
            <span class="page-label">第</span>
            <span class="current-page">{{ currentPage }}</span>
            <span class="page-label">页</span>
            <span v-if="hasMoreFiles" class="more-indicator">（还有更多）</span>
          </div>
          
          <button 
            @click="changePage(currentPage + 1)" 
            :disabled="!canGoNext"
            class="pagination-btn"
          >
            下一页 ›
          </button>
        </div>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminApi } from '@/api/admin'
import type { Service, Bucket } from '@/types/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const bucketsLoading = ref(false)
const filesLoading = ref(false)

// 导航层级：'services' | 'buckets' | 'folders' | 'files'
const currentView = ref<'services' | 'buckets' | 'folders' | 'files'>('services')

const services = ref<Service[]>([])
const selectedService = ref<Service | null>(null)

const buckets = ref<Bucket[]>([])
const selectedBucket = ref<Bucket | null>(null)

const folders = ref<string[]>([])
const selectedFolder = ref<string | null>(null)
const foldersLoading = ref(false)

const files = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalFiles = ref(0)
const continuationToken = ref<string | undefined>(undefined)
const hasMoreFiles = ref(false)
const pageTokens = ref<Map<number, string>>(new Map()) // 存储每页的token

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

onMounted(async () => {
  await loadServices()
  await initializeFromRoute()
})

// 监听路由变化
watch(() => route.params, async () => {
  await initializeFromRoute()
}, { deep: true })

// 根据路由参数初始化视图
async function initializeFromRoute() {
  const { serviceName, bucketName, folderName } = route.params
  
  if (folderName && typeof folderName === 'string') {
    // 文件列表视图
    await loadFromRoute(serviceName as string, bucketName as string, folderName)
  } else if (bucketName && typeof bucketName === 'string') {
    // 文件夹列表视图
    await loadFromRoute(serviceName as string, bucketName)
  } else if (serviceName && typeof serviceName === 'string') {
    // 存储桶列表视图
    await loadFromRoute(serviceName)
  } else {
    // 服务列表视图
    currentView.value = 'services'
  }
}

// 根据路由加载数据
async function loadFromRoute(serviceName: string, bucketName?: string, folderName?: string) {
  if (!services.value.length) {
    await loadServices()
  }
  
  // 查找服务
  const service = services.value.find(s => s.name === serviceName)
  if (!service) {
    router.push('/')
    return
  }
  
  selectedService.value = service
  
  if (bucketName) {
    currentView.value = 'buckets'
    await loadBuckets(service.id)
    
    // 查找存储桶
    const bucket = buckets.value.find(b => b.name === bucketName)
    if (!bucket) {
      router.push(`/services/${serviceName}`)
      return
    }
    
    selectedBucket.value = bucket
    
    if (folderName) {
      currentView.value = 'folders'
      await loadFolders(bucket)
      
      // 设置文件夹并加载文件
      selectedFolder.value = folderName
      currentView.value = 'files'
      await loadFilesInFolder(folderName, 1)
    } else {
      currentView.value = 'folders'
      await loadFolders(bucket)
    }
  } else {
    currentView.value = 'buckets'
    await loadBuckets(service.id)
  }
}

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
  router.push(`/services/${service.name}`)
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
  if (selectedService.value) {
    router.push(`/services/${selectedService.value.name}/${bucket.name}`)
  }
}

async function loadFolders(bucket: Bucket) {
  if (!bucket) return
  
  foldersLoading.value = true
  try {
    const response = await adminApi.listFolders(bucket.name)
    console.log('loadFolders response:', response)
    console.log('currentView:', currentView.value)
    if (response.success && response.data) {
      folders.value = response.data.folders || []
      console.log('folders.value 已更新:', folders.value)
    }
  } catch (error: any) {
    console.error('加载文件夹列表失败:', error)
    alert('加载文件夹列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    foldersLoading.value = false
    console.log('foldersLoading 设置为 false, folders:', folders.value, 'currentView:', currentView.value)
  }
}

async function selectFolder(folder: string) {
  if (selectedService.value && selectedBucket.value) {
    router.push(`/services/${selectedService.value.name}/${selectedBucket.value.name}/${folder}`)
  }
}

function goBack() {
  router.back()
}

function goToServices() {
  router.push('/')
}

function goToBuckets() {
  if (selectedService.value) {
    router.push(`/services/${selectedService.value.name}`)
  }
}

function goToFolders() {
  if (selectedService.value && selectedBucket.value) {
    router.push(`/services/${selectedService.value.name}/${selectedBucket.value.name}`)
  }
}

async function loadFilesInFolder(folder: string, page: number = 1) {
  if (!selectedBucket.value) return
  
  // 如果是第一页，重置分页状态
  if (page === 1) {
    currentPage.value = 1
    pageTokens.value.clear()
    continuationToken.value = undefined
  }
  
  filesLoading.value = true
  try {
    // 获取该页的 token
    const token = page > 1 ? pageTokens.value.get(page) : undefined
    
    const response = await adminApi.listFilesInFolder(
      selectedBucket.value.name, 
      folder, 
      pageSize.value,
      token
    )
    
    if (response.success && response.data) {
      files.value = response.data.files || []
      hasMoreFiles.value = response.data.isTruncated || false
      
      // 存储下一页的 token
      if (response.data.nextContinuationToken) {
        pageTokens.value.set(page + 1, response.data.nextContinuationToken)
      }
      
      currentPage.value = page
      
      // 估算总文件数（用于显示）
      if (hasMoreFiles.value) {
        totalFiles.value = page * pageSize.value + 1 // 至少还有1个
      } else {
        totalFiles.value = (page - 1) * pageSize.value + files.value.length
      }
    }
  } catch (error: any) {
    console.error('加载文件列表失败:', error)
    alert('加载文件列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    filesLoading.value = false
  }
}

function changePage(page: number) {
  if (!selectedFolder.value) return
  if (page < 1) return
  if (page > currentPage.value && !hasMoreFiles.value) return // 已经是最后一页
  loadFilesInFolder(selectedFolder.value, page)
}

const canGoPrev = computed(() => currentPage.value > 1)
const canGoNext = computed(() => hasMoreFiles.value)

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

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
}

.top-nav {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.nav-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 64px;
}

.nav-left {
  display: flex;
  align-items: center;
}

.nav-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-link {
  padding: 8px 16px;
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #eff6ff;
  color: #2563eb;
}

.btn-logout {
  padding: 8px 16px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-logout:hover {
  background: #dc2626;
}

.service-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  margin-bottom: 16px;
  font-size: 14px;
}

.breadcrumb-item {
  background: none;
  border: none;
  color: #3b82f6;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}

.breadcrumb-item:hover {
  background: #eff6ff;
  color: #2563eb;
}

.breadcrumb-separator {
  color: #9ca3af;
}

.breadcrumb-current {
  color: #1a1a1a;
  font-weight: 500;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.btn-back {
  padding: 8px 16px;
  background: #f3f4f6;
  color: #374151;
  border: none;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-back:hover {
  background: #e5e7eb;
  color: #1f2937;
}

.header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
}

.view-container {
  margin-top: 20px;
}

.grid-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.grid-item {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  position: relative;
  overflow: hidden;
}

.grid-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05) 0%, rgba(147, 197, 253, 0.05) 100%);
  opacity: 0;
  transition: opacity 0.3s;
}

.grid-item:hover {
  border-color: #60a5fa;
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.15), 0 2px 6px rgba(0, 0, 0, 0.05);
  transform: translateY(-2px) scale(1.01);
}

.grid-item:hover::before {
  opacity: 1;
}

.item-icon {
  font-size: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.12);
  transition: all 0.3s;
}

.grid-item:hover .item-icon {
  transform: scale(1.08) rotate(3deg);
  box-shadow: 0 3px 10px rgba(59, 130, 246, 0.2);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 6px 0;
  word-break: break-word;
  letter-spacing: -0.01em;
}

.item-desc {
  color: #6b7280;
  font-size: 13px;
  margin: 0 0 6px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.meta-badge {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  color: #1e40af;
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  box-shadow: 0 1px 2px rgba(59, 130, 246, 0.08);
}

.meta-time {
  color: #9ca3af;
}

.item-arrow {
  font-size: 20px;
  color: #cbd5e1;
  flex-shrink: 0;
  transition: all 0.3s;
}

.grid-item:hover .item-arrow {
  color: #3b82f6;
  transform: translateX(3px);
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

.files-stats {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}

.files-table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.files-table th {
  background: #f9fafb;
  padding: 12px;
  text-align: left;
  font-weight: 600;
  font-size: 14px;
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
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding: 20px 0;
}

.pagination-btn {
  padding: 8px 16px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #475569;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.pagination-btn:hover:not(:disabled) {
  background: #f1f5f9;
  border-color: #3b82f6;
  color: #3b82f6;
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  background: #f8fafc;
  border-radius: 8px;
  font-weight: 600;
  color: #1e293b;
}

.pagination-info .current-page {
  color: #3b82f6;
  font-size: 16px;
}

.pagination-info .separator {
  color: #cbd5e1;
  font-size: 14px;
}

.pagination-info .total-pages {
  color: #64748b;
  font-size: 14px;
}

.pagination-info .page-label {
  color: #64748b;
  font-size: 14px;
  font-weight: 400;
}

.pagination-info .more-indicator {
  color: #059669;
  font-size: 12px;
  font-weight: 500;
  margin-left: 8px;
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

.loading, .loading {
  text-align: center;
  padding: 60px;
  color: #64748b;
  font-size: 16px;
  position: relative;
}

.loading::before {
  content: '';
  display: inline-block;
  width: 40px;
  height: 40px;
  border: 4px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty {
  text-align: center;
  padding: 80px 20px;
  color: #94a3b8;
  font-size: 16px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 2px dashed #cbd5e1;
}

.empty::before {
  content: '📋';
  display: block;
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
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
