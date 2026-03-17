<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="mb-6 px-6 pt-6">
      <h1 class="text-2xl font-bold text-gray-800">服务管理</h1>
      <p class="text-gray-600 mt-1">管理文件存储服务、存储桶、文件夹和文件</p>
    </div>

    <!-- 主内容区 -->
    <div class="service-management px-6">
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
      <div v-else>
        <table class="files-table">
          <thead>
            <tr>
              <th>服务名称</th>
              <th>描述</th>
              <th>存储桶数量</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="service in services" :key="service.id" @click="selectService(service)" class="clickable-row">
              <td class="file-name">
                <div class="name-with-icon">
                  <span class="table-icon">📦</span>
                  <span>{{ service.name }}</span>
                </div>
              </td>
              <td class="description-cell">{{ service.description || '-' }}</td>
              <td>{{ service.bucketCount }} 个</td>
              <td>{{ formatDate(service.createTime) }}</td>
              <td>
                <div class="file-actions">
                  <button 
                    @click="deleteService(service, $event)" 
                    class="btn-link btn-link-delete"
                    title="删除服务"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 存储桶列表视图 -->
    <div v-if="currentView === 'buckets'" class="view-container">
      <div v-if="bucketsLoading" class="loading">加载中...</div>
      <div v-else-if="buckets.length === 0" class="empty">
        该服务下暂无存储桶
      </div>
      <div v-else>
        <table class="files-table">
          <thead>
            <tr>
              <th>存储桶名称</th>
              <th>描述</th>
              <th>文件数量</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="bucket in buckets" :key="bucket.id" @click="selectBucket(bucket)" class="clickable-row">
              <td class="file-name">
                <div class="name-with-icon">
                  <span class="table-icon">🗂️</span>
                  <span>{{ bucket.name }}</span>
                </div>
              </td>
              <td class="description-cell">{{ bucket.description || '-' }}</td>
              <td>{{ bucket.fileCount }} 个</td>
              <td>{{ formatDate(bucket.createTime) }}</td>
              <td>
                <div class="file-actions">
                  <button 
                    @click="deleteBucket(bucket, $event)" 
                    class="btn-link btn-link-delete"
                    title="删除存储桶"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 文件夹列表视图 -->
    <div v-if="currentView === 'folders'" class="view-container">
      <!-- 操作按钮区 -->
      <div class="action-bar">
        <button @click="showCreateFolderDialog = true" class="btn-action">
          <span class="icon">+</span>
          新建目录
        </button>
        <button @click="showUploadDialog = true" class="btn-action btn-primary">
          <span class="icon">↑</span>
          上传文件
        </button>
      </div>
      
      <div v-if="foldersLoading" class="loading">加载中...</div>
      <div v-else-if="folders.length === 0 && rootFiles.length === 0" class="empty">
        该存储桶中暂无内容
      </div>
      <div v-else>
        <table class="files-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>大小</th>
              <th>最后修改时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <!-- 文件夹行 -->
            <tr v-for="folder in folders" :key="'folder-' + folder" @click="selectFolder(folder)" class="clickable-row">
              <td class="file-name">
                <div class="name-with-icon">
                  <span class="table-icon">📁</span>
                  <span>{{ folder }}</span>
                </div>
              </td>
              <td>文件夹</td>
              <td>-</td>
              <td>-</td>
              <td>
                <div class="file-actions">
                  <button 
                    @click="deleteFolder(folder, $event)" 
                    class="btn-link btn-link-delete"
                    title="删除文件夹"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
            <!-- 文件行 -->
            <tr v-for="file in rootFiles" :key="'file-' + file.key">
              <td class="file-name">
                <div class="name-with-icon">
                  <span class="table-icon">📄</span>
                  <span>{{ getFileName(file.key) }}</span>
                </div>
              </td>
              <td>文件</td>
              <td>{{ formatFileSize(file.size) }}</td>
              <td>{{ formatDate(file.lastModified) }}</td>
              <td>
                <div class="file-actions">
                  <a v-if="file.url" :href="file.url" target="_blank" class="btn-link">预览</a>
                  <a v-if="file.downloadUrl" :href="file.downloadUrl" class="btn-link" @click.stop>下载</a>
                  <button @click="deleteRootFile(file, $event)" class="btn-link btn-link-delete" title="删除文件">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 文件列表视图 -->
    <div v-if="currentView === 'files'" class="view-container">
      <!-- 操作按钮区 -->
      <div class="action-bar">
        <button @click="showCreateFolderDialog = true" class="btn-action">
          <span class="icon">+</span>
          新建目录
        </button>
        <button @click="showUploadDialog = true" class="btn-action btn-primary">
          <span class="icon">↑</span>
          上传文件
        </button>
      </div>
      
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
                  <button @click="deleteFile(file)" class="btn-link btn-link-delete" title="删除文件">删除</button>
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
          
          <!-- 页码按钮 -->
          <div class="pagination-numbers">
            <button
              v-for="(page, index) in displayPages"
              :key="index"
              @click="page > 0 ? changePage(page) : null"
              :class="[
                'pagination-number',
                { 'active': page === currentPage },
                { 'ellipsis': page === -1 }
              ]"
              :disabled="page === -1"
            >
              {{ page === -1 ? '...' : page }}
            </button>
          </div>
          
          <button 
            @click="changePage(currentPage + 1)" 
            :disabled="!canGoNext"
            class="pagination-btn"
          >
            下一页 ›
          </button>
          
          <!-- 跳转输入框 -->
          <div class="pagination-jump">
            <span class="jump-label">跳转到</span>
            <input 
              v-model.number="jumpToPage" 
              type="number" 
              min="1"
              class="jump-input"
              @keyup.enter="handleJumpToPage"
              placeholder="页"
            />
            <button @click="handleJumpToPage" class="jump-btn">GO</button>
          </div>
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

    <!-- 创建文件夹对话框 -->
    <div v-if="showCreateFolderDialog" class="dialog-overlay">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h3>新建目录</h3>
          <button @click="showCreateFolderDialog = false" class="btn-close">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>目录名称 *</label>
            <input 
              v-model="newFolder" 
              type="text" 
              placeholder="例如: images, documents"
              class="input"
            />
            <small class="form-hint">注意：在S3中，文件夹通过上传文件自动创建</small>
          </div>
        </div>
        <div class="dialog-footer">
          <button @click="showCreateFolderDialog = false" class="btn-secondary">取消</button>
          <button @click="createFolder" :disabled="!newFolder" class="btn-primary">确定</button>
        </div>
      </div>
    </div>

    <!-- 上传文件对话框 -->
    <div v-if="showUploadDialog" class="dialog-overlay" @click="showUploadDialog = false">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h3>上传文件</h3>
          <button @click="showUploadDialog = false" class="btn-close">×</button>
        </div>
        <div class="dialog-body">
          <div class="upload-info">
            <div><strong>服务：</strong>{{ selectedService?.name }}</div>
            <div><strong>存储桶：</strong>{{ selectedBucket?.name }}</div>
          </div>
          
          <div class="file-upload-area">
            <input 
              type="file" 
              multiple 
              @change="handleFileSelect" 
              id="fileInput"
              style="display: none"
            />
            <label for="fileInput" class="upload-label">
              <div class="upload-icon">📁</div>
              <div class="upload-text">点击选择文件或拖拽文件到这里</div>
              <div class="upload-hint">支持批量上传</div>
            </label>
          </div>
          
          <div v-if="uploadingFiles.length > 0" class="file-list">
            <h4>待上传文件 ({{ uploadingFiles.length }})</h4>
            <div v-for="file in uploadingFiles" :key="file.name" class="file-item">
              <span class="file-name">{{ file.name }}</span>
              <span class="file-size">{{ formatFileSize(file.size) }}</span>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button @click="showUploadDialog = false" class="btn-secondary">取消</button>
          <button @click="startUpload" :disabled="uploadingFiles.length === 0" class="btn-primary">
            上传 {{ uploadingFiles.length > 0 ? `(${uploadingFiles.length}个文件)` : '' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { Service, Bucket } from '@/types/api'

const router = useRouter()
const route = useRoute()

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
const rootFiles = ref<any[]>([])
const selectedFolder = ref<string | null>(null)
const foldersLoading = ref(false)

const files = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalFiles = ref(0)
const continuationToken = ref<string | undefined>(undefined)
const hasMoreFiles = ref(false)
const pageTokens = ref<Map<number, string>>(new Map()) // 存储每页的token
const jumpToPage = ref<number | ''>('') // 跳转页码输入
const maxKnownPage = ref(1) // 已知的最大页码

const showCreateServiceDialog = ref(false)
const showCreateBucketDialog = ref(false)
const showCreateFolderDialog = ref(false)
const showUploadDialog = ref(false)

const newService = ref({
  name: '',
  description: ''
})

const newBucket = ref({
  name: '',
  description: ''
})

const newFolder = ref('')
const uploadingFiles = ref<File[]>([])
const uploadProgress = ref<{ [key: string]: number }>({})
const uploadingCount = ref(0)

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
  if (!bucket || !selectedService.value) return
  
  foldersLoading.value = true
  try {
    const response = await adminApi.listFolders(selectedService.value.name, bucket.name)
    console.log('loadFolders response:', response)
    console.log('currentView:', currentView.value)
    if (response.success && response.data) {
      folders.value = response.data.folders || []
      rootFiles.value = response.data.files || []
      console.log('folders.value 已更新:', folders.value)
      console.log('rootFiles.value 已更新:', rootFiles.value)
    }
  } catch (error: any) {
    console.error('加载文件夹列表失败:', error)
    alert('加载文件夹列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    foldersLoading.value = false
    console.log('foldersLoading 设置为 false, folders:', folders.value, 'rootFiles:', rootFiles.value, 'currentView:', currentView.value)
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
  if (!selectedBucket.value || !selectedService.value) return
  
  // 如果是第一页，重置分页状态
  if (page === 1) {
    currentPage.value = 1
    pageTokens.value.clear()
    continuationToken.value = undefined
    maxKnownPage.value = 1
  }
  
  filesLoading.value = true
  try {
    // 获取该页的 token
    const token = page > 1 ? pageTokens.value.get(page) : undefined
    
    const response = await adminApi.listFilesInFolder(
      selectedService.value.name,
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
      
      // 更新已知最大页码
      if (page > maxKnownPage.value) {
        maxKnownPage.value = page
      }
      
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

// 生成显示的页码数组
const displayPages = computed(() => {
  const pages: number[] = []
  const current = currentPage.value
  const max = hasMoreFiles.value ? maxKnownPage.value + 1 : maxKnownPage.value
  
  // 总是显示第1页
  pages.push(1)
  
  // 计算显示范围
  let start = Math.max(2, current - 2)
  let end = Math.min(max, current + 2)
  
  // 如果当前页靠前，多显示后面的页码
  if (current <= 3) {
    end = Math.min(max, 7)
  }
  
  // 如果当前页靠后，多显示前面的页码
  if (current >= max - 2) {
    start = Math.max(2, max - 6)
  }
  
  // 添加省略号和中间页码
  if (start > 2) {
    pages.push(-1) // -1 表示省略号
  }
  
  for (let i = start; i <= end; i++) {
    if (i !== 1 && i <= max) {
      pages.push(i)
    }
  }
  
  // 添加省略号和最后页码
  if (end < max) {
    if (end < max - 1) {
      pages.push(-1) // 省略号
    }
    pages.push(max)
  }
  
  return pages
})

function handleJumpToPage() {
  const page = typeof jumpToPage.value === 'number' ? jumpToPage.value : parseInt(jumpToPage.value as string)
  if (isNaN(page) || page < 1) {
    alert('请输入有效的页码')
    return
  }
  
  // 检查是否可以跳转到该页
  if (page > maxKnownPage.value && !hasMoreFiles.value) {
    alert(`最多只有 ${maxKnownPage.value} 页`)
    return
  }
  
  if (page > maxKnownPage.value + 1 && hasMoreFiles.value) {
    alert(`请先浏览到第 ${maxKnownPage.value + 1} 页`)
    return
  }
  
  changePage(page)
  jumpToPage.value = ''
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
      console.log('服务创建成功:', newService.value.name)
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
      console.log('存储桶创建成功:', newBucket.value.name)
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

async function createFolder() {
  if (!newFolder.value.trim()) {
    alert('请输入文件夹名称')
    return
  }

  if (!selectedBucket.value || !selectedService.value) {
    alert('请先选择存储桶')
    return
  }

  try {
    // 创建一个包含少量内容的占位文件，避免空文件导致的UnexpectedContent错误
    const content = '# This is a placeholder file to create the folder structure\n'
    const blob = new Blob([content], { type: 'text/plain' })
    const file = new File([blob], '.keep', { type: 'text/plain' })
    
    const { uploadApi } = await import('@/api/upload')
    const folderName = newFolder.value.trim()
    
    console.log('创建文件夹:', folderName, '在存储桶:', selectedBucket.value.name)
    
    // 计算文件哈希
    const fileHash = await calculateFileHash(file)
    
    // 1. 获取上传签名
    const signatureResponse = await uploadApi.getDirectUploadSignature({
      fileName: '.keep',
      fileType: 'text/plain',
      bucket: selectedBucket.value.name,
      folder: folderName,
      service: selectedService.value.name,
      fileHash: fileHash,
      fileSize: file.size
    })
    
    if (!signatureResponse.success) {
      throw new Error(signatureResponse.message || '获取上传签名失败')
    }
    
    // 2. 根据needUpload决定是否上传
    if (signatureResponse.needUpload && signatureResponse.signature) {
      // 需要上传新文件
      await uploadApi.uploadFileWithSignature(file, signatureResponse.signature)
      
      // 3. 记录上传
      await uploadApi.recordDirectUpload({
        fileHash: signatureResponse.fileHash || fileHash,
        fileKey: signatureResponse.fileKey || '',
        fileUrl: signatureResponse.fileUrl || '',
        originalFileName: '.keep',
        fileSize: file.size,
        contentType: 'text/plain',
        bucketName: selectedBucket.value.name,
        service: selectedService.value.name
      })
      console.log('文件夹创建成功 - 新建占位文件')
    } else {
      // 文件已存在，文件夹已经存在
      console.log('文件夹已存在 - 使用现有占位文件')
      // 可以选择显示提示信息，但不是错误
      if (signatureResponse.message) {
        console.info('提示:', signatureResponse.message)
      }
    }
    showCreateFolderDialog.value = false
    newFolder.value = ''
    
    // 重新加载文件夹列表
    if (selectedBucket.value) {
      await loadFolders(selectedBucket.value)
    }
  } catch (error: any) {
    console.error('创建文件夹失败:', error)
    alert('创建文件夹失败: ' + (error.response?.data?.message || error.message))
  }
}

function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    uploadingFiles.value = Array.from(target.files)
  }
}

// 计算文件SHA256哈希值
async function calculateFileHash(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = async (event) => {
      try {
        const arrayBuffer = event.target?.result as ArrayBuffer
        const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer)
        const hashArray = Array.from(new Uint8Array(hashBuffer))
        const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
        resolve(hashHex)
      } catch (error) {
        reject(error)
      }
    }
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsArrayBuffer(file)
  })
}

async function startUpload() {
  if (!selectedService.value || !selectedBucket.value || uploadingFiles.value.length === 0) {
    alert('请选择要上传的文件')
    return
  }
  
  uploadingCount.value = uploadingFiles.value.length
  const { uploadApi } = await import('@/api/upload')
  
  let successCount = 0
  let existingCount = 0
  
  for (const file of uploadingFiles.value) {
    try {
      console.log('上传文件:', file.name)
      
      // 1. 计算文件哈希值
      const fileHash = await calculateFileHash(file)
      console.log('文件哈希值:', fileHash)
      
      // 2. 获取上传签名
      // 如果在文件列表视图中，使用当前文件夹；否则上传到根目录
      const targetFolder = currentView.value === 'files' && selectedFolder.value ? selectedFolder.value : ''
      const signatureResponse = await uploadApi.getDirectUploadSignature({
        fileName: file.name,
        fileType: file.type,
        bucket: selectedBucket.value.name,
        folder: targetFolder,
        service: selectedService.value.name,
        fileHash: fileHash,
        fileSize: file.size
      })
      
      if (!signatureResponse.success) {
        throw new Error(signatureResponse.message || '获取上传签名失败')
      }
      
      // 3. 上传文件
      if (signatureResponse.needUpload && signatureResponse.signature) {
        await uploadApi.uploadFileWithSignature(file, signatureResponse.signature)
        
        // 4. 记录上传
        await uploadApi.recordDirectUpload({
          fileHash: signatureResponse.fileHash || '',
          fileKey: signatureResponse.fileKey || '',
          fileUrl: signatureResponse.fileUrl || '',
          originalFileName: file.name,
          fileSize: file.size,
          contentType: file.type,
          bucketName: selectedBucket.value.name,
          service: selectedService.value.name
        })
        
        console.log('文件上传成功:', file.name)
        uploadProgress.value[file.name] = 100
        successCount++
      } else if (!signatureResponse.needUpload) {
        // 文件已存在，无需上传
        console.log('文件已存在，无需上传:', file.name)
        uploadProgress.value[file.name] = 100
        
        // 记录文件已存在的信息，但不立即显示提示
        const message = signatureResponse.message || '文件已存在，无需重复上传'
        console.log(`${file.name}: ${message}`)
        
        existingCount++
      }
    } catch (error: any) {
      console.error('上传失败:', file.name, error)
      alert(`上传 ${file.name} 失败: ` + (error.response?.data?.message || error.message))
    }
  }
  
  // 上传完成，关闭对话框并刷新
  setTimeout(() => {
    const totalProcessed = successCount + existingCount
    if (totalProcessed > 0) {
      let message = ''
      if (successCount > 0 && existingCount > 0) {
        message = `处理完成！新上传 ${successCount} 个文件，${existingCount} 个文件已存在`
      } else if (successCount > 0) {
        message = `成功上传 ${successCount} 个文件`
      } else if (existingCount > 0) {
        message = `${existingCount} 个文件已存在，无需重复上传`
      }
      alert(message)
    }
    
    showUploadDialog.value = false
    uploadingFiles.value = []
    uploadProgress.value = {}
    uploadingCount.value = 0
    
    // 根据当前视图刷新对应的列表
    if (currentView.value === 'files' && selectedFolder.value) {
      // 如果在文件列表视图中，刷新当前文件夹的文件列表
      loadFilesInFolder(selectedFolder.value, currentPage.value)
    } else if (currentView.value === 'folders' && selectedBucket.value) {
      // 如果在文件夹视图中，刷新文件夹列表（包括根目录文件）
      loadFolders(selectedBucket.value)
    }
  }, 500)
}

// 删除服务
async function deleteService(service: Service, event: Event) {
  event.stopPropagation()
  
  if (!confirm(`确定要删除服务 "${service.name}" 吗？\n\n注意：只有服务下没有存储桶时才能删除。`)) {
    return
  }
  
  try {
    const { adminApi } = await import('@/api/admin')
    const response = await adminApi.deleteService(service.id)
    
    if (response.success) {
      alert(response.message)
      await loadServices()
    } else {
      alert(response.message)
    }
  } catch (error: any) {
    console.error('删除服务失败:', error)
    alert('删除服务失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除存储桶
async function deleteBucket(bucket: Bucket, event: Event) {
  event.stopPropagation()
  
  if (!confirm(`确定要删除存储桶 "${bucket.name}" 吗？\n\n注意：只有存储桶下没有文件时才能删除。`)) {
    return
  }
  
  try {
    const { adminApi } = await import('@/api/admin')
    const response = await adminApi.deleteBucket(bucket.id)
    
    if (response.success) {
      alert(response.message)
      if (selectedService.value) {
        await loadBuckets(selectedService.value.id)
      }
    } else {
      alert(response.message)
    }
  } catch (error: any) {
    console.error('删除存储桶失败:', error)
    alert('删除存储桶失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除文件夹
async function deleteFolder(folderName: string, event: Event) {
  event.stopPropagation()
  
  if (!confirm(`确定要删除文件夹 "${folderName}" 吗？\n\n注意：只有文件夹下没有文件时才能删除。`)) {
    return
  }
  
  try {
    if (!selectedBucket.value) {
      alert('请先选择存储桶')
      return
    }
    
    const { adminApi } = await import('@/api/admin')
    const response = await adminApi.deleteFolder(selectedBucket.value.id, folderName)
    
    if (response.success) {
      alert(response.message)
      if (selectedBucket.value) {
        await loadFolders(selectedBucket.value)
      }
    } else {
      alert(response.message)
    }
  } catch (error: any) {
    console.error('删除文件夹失败:', error)
    alert('删除文件夹失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除文件
async function deleteFile(file: any) {
  if (!confirm(`确定要删除文件 "${getFileName(file.key)}" 吗？\n\n此操作不可恢复！`)) {
    return
  }
  
  try {
    if (!selectedBucket.value) {
      alert('请先选择存储桶')
      return
    }
    
    const { adminApi } = await import('@/api/admin')
    const response = await adminApi.deleteFile(file.key, selectedBucket.value.name)
    
    if (response.success) {
      alert(response.message || '文件删除成功')
      // 重新加载当前文件夹的文件列表
      if (selectedFolder.value) {
        await loadFilesInFolder(selectedFolder.value, currentPage.value)
      }
    } else {
      alert(response.message)
    }
  } catch (error: any) {
    console.error('删除文件失败:', error)
    alert('删除文件失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除根目录文件
async function deleteRootFile(file: any, event: Event) {
  event.stopPropagation()
  
  if (!confirm(`确定要删除文件 "${getFileName(file.key)}" 吗？\n\n此操作不可恢复！`)) {
    return
  }
  
  try {
    if (!selectedBucket.value) {
      alert('请先选择存储桶')
      return
    }
    
    const { adminApi } = await import('@/api/admin')
    const response = await adminApi.deleteFile(file.key, selectedBucket.value.name)
    
    if (response.success) {
      alert(response.message || '文件删除成功')
      // 重新加载文件夹列表（包含根目录文件）
      if (selectedBucket.value) {
        await loadFolders(selectedBucket.value)
      }
    } else {
      alert(response.message)
    }
  } catch (error: any) {
    console.error('删除文件失败:', error)
    alert('删除文件失败: ' + (error.response?.data?.message || error.message))
  }
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

.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.btn-action {
  padding: 10px 20px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #475569;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-action:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.btn-action.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border-color: #3b82f6;
  color: white;
}

.btn-action.btn-primary:hover {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.view-container {
  margin-top: 20px;
}

.grid-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: stretch;
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
  flex: 0 0 auto;
  min-width: 260px;
  max-width: 320px;
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

.item-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-right: 8px;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 10;
}

.grid-item:hover .item-actions {
  opacity: 1;
}

.btn-delete {
  padding: 6px 10px;
  background: #fee2e2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #dc2626;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.btn-delete:hover {
  background: #fecaca;
  border-color: #fca5a5;
  transform: scale(1.1);
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.2);
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

/* 文件项样式 */
.grid-item.file-item {
  cursor: default;
}

.grid-item.file-item:hover {
  transform: translateY(-1px) scale(1.005);
}

.grid-item.file-item .item-actions {
  opacity: 1;
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-link {
  padding: 4px 12px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  color: #3b82f6;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  font-weight: 500;
}

.btn-link:hover {
  background: #dbeafe;
  border-color: #93c5fd;
  transform: scale(1.05);
}

.btn-link-delete {
  background: #fee2e2;
  border-color: #fecaca;
  color: #dc2626;
}

.btn-link-delete:hover {
  background: #fecaca;
  border-color: #fca5a5;
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

.root-files-section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #e2e8f0;
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

.files-table tbody tr {
  transition: all 0.2s;
}

.files-table tbody tr:hover {
  background: #f8fafc;
}

.clickable-row {
  cursor: pointer;
}

.clickable-row:hover {
  background: #eff6ff !important;
}

.name-with-icon {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-icon {
  font-size: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.description-cell {
  color: #6b7280;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  flex-wrap: wrap;
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

.pagination-numbers {
  display: flex;
  gap: 6px;
}

.pagination-number {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #475569;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination-number:hover:not(:disabled):not(.active) {
  background: #f1f5f9;
  border-color: #94a3b8;
}

.pagination-number.active {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border-color: #3b82f6;
  color: white;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.pagination-number.ellipsis {
  border: none;
  background: transparent;
  cursor: default;
  color: #94a3b8;
}

.pagination-number:disabled {
  cursor: default;
}

.pagination-jump {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
  padding-left: 16px;
  border-left: 1px solid #e2e8f0;
}

.jump-label {
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}

.jump-input {
  width: 60px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 14px;
  text-align: center;
  transition: all 0.2s;
}

.jump-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.jump-input::placeholder {
  color: #cbd5e1;
}

/* 移除number输入框的上下箭头 */
.jump-input::-webkit-inner-spin-button,
.jump-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.jump-input[type=number] {
  -moz-appearance: textfield;
}

.jump-btn {
  height: 36px;
  padding: 0 16px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  border-radius: 6px;
  color: white;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.jump-btn:hover {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
  transform: translateY(-1px);
}

.jump-btn:active {
  transform: translateY(0);
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

.btn-link-delete {
  color: #ef4444;
  border: none;
}

.btn-link-delete:hover {
  background: #fee2e2;
  color: #dc2626;
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
  padding: 16px 24px;
  background: #f9fafb;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid #e5e7eb;
}

.form-hint {
  display: block;
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
  font-style: italic;
}

.dialog-message {
  margin-bottom: 16px;
  color: #475569;
  line-height: 1.6;
}

.upload-info {
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border-left: 3px solid #3b82f6;
}

.upload-info div {
  margin: 8px 0;
  color: #1e293b;
}

.file-upload-area {
  margin: 20px 0;
}

.upload-label {
  display: block;
  padding: 40px 20px;
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-label:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

.upload-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.upload-text {
  font-size: 16px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 14px;
  color: #64748b;
}

.file-list {
  margin-top: 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.file-list h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: white;
  border-radius: 6px;
  margin-bottom: 8px;
}

.file-item:last-child {
  margin-bottom: 0;
}

.file-item .file-name {
  flex: 1;
  font-size: 14px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item .file-size {
  font-size: 12px;
  color: #64748b;
  margin-left: 12px;
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
