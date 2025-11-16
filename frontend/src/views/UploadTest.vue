<template>
  <div class="upload-test-container">
    <h1>文件上传测试</h1>

    <!-- 测试场景选择 -->
    <div class="test-scenarios">
      <h2>测试场景</h2>
      <div class="scenario-buttons">
        <button @click="activeTab = 'basic'" :class="{ active: activeTab === 'basic' }">
          基础上传
        </button>
        <button @click="activeTab = 'concurrent'" :class="{ active: activeTab === 'concurrent' }">
          并发上传（同一文件）
        </button>
        <button @click="activeTab = 'dedup'" :class="{ active: activeTab === 'dedup' }">
          去重测试
        </button>
        <button @click="activeTab = 'retry'" :class="{ active: activeTab === 'retry' }">
          失败重试
        </button>
        <button @click="activeTab = 'sync'" :class="{ active: activeTab === 'sync' }">
          数据同步
        </button>
      </div>
    </div>

    <!-- 基础上传 -->
    <div v-if="activeTab === 'basic'" class="test-panel">
      <h3>基础上传测试</h3>
      <p class="description">测试单个文件上传功能</p>
      
      <input type="file" @change="handleFileSelect" ref="fileInput" />
      <button @click="uploadFile" :disabled="!selectedFile || uploading" class="upload-btn">
        {{ uploading ? '上传中...' : '开始上传' }}
      </button>

      <div v-if="uploadResult" class="result">
        <h4>上传结果：</h4>
        <pre>{{ JSON.stringify(uploadResult, null, 2) }}</pre>
        <img v-if="uploadResult.fileUrl && isImage(uploadResult.fileUrl)" 
             :src="uploadResult.fileUrl" 
             alt="上传的图片" 
             class="preview-image" />
      </div>
    </div>

    <!-- 并发上传测试 -->
    <div v-if="activeTab === 'concurrent'" class="test-panel">
      <h3>并发上传测试（同一文件）</h3>
      <p class="description">
        选择一个文件，模拟多个用户同时上传相同文件。<br>
        预期：第一个上传成功，其他请求等待或直接返回已存在的文件。
      </p>
      
      <input type="file" @change="handleFileSelect" />
      <div class="concurrent-controls">
        <label>
          并发数量：
          <input type="number" v-model.number="concurrentCount" min="2" max="10" />
        </label>
        <button @click="testConcurrentUpload" :disabled="!selectedFile || uploading" class="upload-btn">
          开始并发测试
        </button>
      </div>

      <div v-if="concurrentResults.length > 0" class="result">
        <h4>并发测试结果：</h4>
        <div v-for="(result, index) in concurrentResults" :key="index" class="concurrent-result-item">
          <strong>请求 {{ index + 1 }}:</strong>
          <span :class="result.success ? 'success' : 'error'">
            {{ result.success ? '✓ 成功' : '✗ 失败' }}
          </span>
          <span class="time">耗时: {{ result.duration }}ms</span>
          <div v-if="result.message" class="message">{{ result.message }}</div>
        </div>
      </div>
    </div>

    <!-- 去重测试 -->
    <div v-if="activeTab === 'dedup'" class="test-panel">
      <h3>文件去重测试</h3>
      <p class="description">
        上传同一个文件两次，测试去重功能。<br>
        预期：第二次上传会直接返回已存在的文件URL，不会重复上传。
      </p>
      
      <input type="file" @change="handleFileSelect" />
      <button @click="testDeduplication" :disabled="!selectedFile || uploading" class="upload-btn">
        测试去重（上传2次）
      </button>

      <div v-if="dedupResults.length > 0" class="result">
        <h4>去重测试结果：</h4>
        <div v-for="(result, index) in dedupResults" :key="index" class="dedup-result-item">
          <strong>第 {{ index + 1 }} 次上传:</strong>
          <div>文件哈希: {{ result.fileHash }}</div>
          <div>是否需要上传: {{ result.needUpload ? '是' : '否（已存在）' }}</div>
          <div>文件URL: {{ result.fileUrl }}</div>
          <div class="time">耗时: {{ result.duration }}ms</div>
        </div>
      </div>
    </div>

    <!-- 失败重试测试 -->
    <div v-if="activeTab === 'retry'" class="test-panel">
      <h3>上传失败重试测试</h3>
      <p class="description">
        模拟上传失败场景（不调用record接口），然后重新上传测试重试机制。
      </p>
      
      <input type="file" @change="handleFileSelect" />
      <div class="retry-controls">
        <button @click="testFailedUpload" :disabled="!selectedFile || uploading" class="upload-btn">
          1. 模拟上传失败
        </button>
        <button @click="testRetryUpload" :disabled="!selectedFile || !failedHash" class="upload-btn">
          2. 重试上传
        </button>
      </div>

      <div v-if="retryResult" class="result">
        <h4>重试测试结果：</h4>
        <pre>{{ JSON.stringify(retryResult, null, 2) }}</pre>
      </div>
    </div>

    <!-- 数据同步 -->
    <div v-if="activeTab === 'sync'" class="test-panel">
      <h3>RustFS 与数据库同步</h3>
      <p class="description">
        将 RustFS 中已有的文件同步到数据库，或清理数据库中的孤儿记录。
      </p>
      
      <div class="sync-controls">
        <div class="input-group">
          <label>Bucket 名称：</label>
          <input type="text" v-model="syncBucket" placeholder="例如: test-default" />
        </div>

        <div class="sync-buttons">
          <button @click="getSyncStatus" :disabled="!syncBucket || syncing" class="sync-btn">
            查看同步状态
          </button>
          <button @click="syncRustFSToDb" :disabled="!syncBucket || syncing" class="sync-btn primary">
            同步 RustFS → 数据库
          </button>
          <button @click="cleanOrphanedRecords" :disabled="!syncBucket || syncing" class="sync-btn warning">
            清理孤儿记录
          </button>
          <button @click="fullSync" :disabled="!syncBucket || syncing" class="sync-btn success">
            完整同步
          </button>
        </div>
      </div>

      <!-- 同步状态 -->
      <div v-if="syncStatus" class="result sync-status">
        <h4>同步状态：</h4>
        <div class="status-grid">
          <div class="status-item">
            <span class="label">Bucket:</span>
            <span class="value">{{ syncStatus.bucketName }}</span>
          </div>
          <div class="status-item">
            <span class="label">RustFS 文件数:</span>
            <span class="value">{{ syncStatus.rustFSFileCount }}</span>
          </div>
          <div class="status-item">
            <span class="label">数据库记录数:</span>
            <span class="value">{{ syncStatus.databaseRecordCount }}</span>
          </div>
          <div class="status-item">
            <span class="label">缺失记录:</span>
            <span class="value" :class="syncStatus.missingRecordCount > 0 ? 'warning' : ''">
              {{ syncStatus.missingRecordCount }}
            </span>
          </div>
          <div class="status-item">
            <span class="label">孤儿记录:</span>
            <span class="value" :class="syncStatus.orphanedRecordCount > 0 ? 'warning' : ''">
              {{ syncStatus.orphanedRecordCount }}
            </span>
          </div>
          <div class="status-item">
            <span class="label">需要同步:</span>
            <span class="value" :class="syncStatus.needSync ? 'error' : 'success'">
              {{ syncStatus.needSync ? '是' : '否' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 同步结果 -->
      <div v-if="syncResult" class="result">
        <h4>同步结果：</h4>
        <div class="sync-result-summary" :class="syncResult.success ? 'success' : 'error'">
          <strong>{{ syncResult.success ? '✓ 成功' : '✗ 失败' }}</strong>
          <p>{{ syncResult.message }}</p>
        </div>
        
        <div v-if="syncResult.data" class="sync-result-details">
          <div v-if="syncResult.data.totalFiles !== undefined" class="detail-row">
            <span>处理文件/记录总数:</span>
            <span>{{ syncResult.data.totalFiles || syncResult.data.totalRecords || 0 }}</span>
          </div>
          <div v-if="syncResult.data.syncedFiles !== undefined" class="detail-row success">
            <span>成功同步/清理:</span>
            <span>{{ syncResult.data.syncedFiles || syncResult.data.cleanedRecords || 0 }}</span>
          </div>
          <div v-if="syncResult.data.skippedFiles !== undefined" class="detail-row">
            <span>跳过:</span>
            <span>{{ syncResult.data.skippedFiles || syncResult.data.skippedRecords || 0 }}</span>
          </div>
          <div v-if="syncResult.data.failedFiles !== undefined && syncResult.data.failedFiles > 0" class="detail-row error">
            <span>失败:</span>
            <span>{{ syncResult.data.failedFiles || syncResult.data.failedRecords || 0 }}</span>
          </div>
          
          <!-- 完整同步结果 -->
          <div v-if="syncResult.data.sync" class="full-sync-section">
            <h5>1. RustFS → 数据库</h5>
            <div class="detail-row"><span>总文件数:</span><span>{{ syncResult.data.sync.totalFiles }}</span></div>
            <div class="detail-row success"><span>同步成功:</span><span>{{ syncResult.data.sync.syncedFiles }}</span></div>
            <div class="detail-row"><span>跳过:</span><span>{{ syncResult.data.sync.skippedFiles }}</span></div>
          </div>
          <div v-if="syncResult.data.clean" class="full-sync-section">
            <h5>2. 清理孤儿记录</h5>
            <div class="detail-row"><span>总记录数:</span><span>{{ syncResult.data.clean.totalRecords }}</span></div>
            <div class="detail-row success"><span>清理成功:</span><span>{{ syncResult.data.clean.cleanedRecords }}</span></div>
            <div class="detail-row"><span>跳过:</span><span>{{ syncResult.data.clean.skippedRecords }}</span></div>
          </div>
        </div>

        <!-- 详细日志 -->
        <div v-if="syncResult.data && syncResult.data.details && syncResult.data.details.length > 0" class="sync-details-log">
          <h5>详细日志 (最近10条):</h5>
          <div class="log-list">
            <div v-for="(detail, index) in syncResult.data.details.slice(0, 10)" :key="index" class="log-line">
              {{ detail }}
            </div>
            <div v-if="syncResult.data.details.length > 10" class="log-more">
              ... 还有 {{ syncResult.data.details.length - 10 }} 条日志
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 日志输出 -->
    <div class="logs">
      <h3>操作日志</h3>
      <button @click="logs = []" class="clear-btn">清空日志</button>
      <div class="log-content">
        <div v-for="(log, index) in logs" :key="index" class="log-item" :class="log.type">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import CryptoJS from 'crypto-js'
import axios from 'axios'

const activeTab = ref('basic')
const selectedFile = ref(null)
const uploading = ref(false)
const uploadResult = ref(null)
const concurrentResults = ref([])
const concurrentCount = ref(3)
const dedupResults = ref([])
const retryResult = ref(null)
const failedHash = ref(null)
const logs = ref([])
const fileInput = ref(null)

// 同步相关
const syncBucket = ref('test-default')
const syncing = ref(false)
const syncStatus = ref(null)
const syncResult = ref(null)

// 配置 axios
const api = axios.create({
  baseURL: 'http://localhost:5003/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 添加日志
function addLog(message, type = 'info') {
  const time = new Date().toLocaleTimeString()
  logs.value.unshift({ time, message, type })
  console.log(`[${type}] ${message}`)
}

// 文件选择
function handleFileSelect(event) {
  selectedFile.value = event.target.files[0]
  uploadResult.value = null
  concurrentResults.value = []
  dedupResults.value = []
  retryResult.value = null
  failedHash.value = null
  addLog(`已选择文件: ${selectedFile.value.name} (${(selectedFile.value.size / 1024).toFixed(2)} KB)`)
}

// 计算文件SHA256哈希
async function calculateFileHash(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const wordArray = CryptoJS.lib.WordArray.create(e.target.result)
      const hash = CryptoJS.SHA256(wordArray).toString()
      resolve(hash)
    }
    reader.readAsArrayBuffer(file)
  })
}

// 判断是否为图片
function isImage(url) {
  return /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(url)
}

// 基础上传
async function uploadFile() {
  if (!selectedFile.value) return

  uploading.value = true
  uploadResult.value = null
  const startTime = Date.now()

  try {
    addLog('开始计算文件哈希...', 'info')
    const fileHash = await calculateFileHash(selectedFile.value)
    addLog(`文件哈希: ${fileHash}`, 'info')

    // 1. 获取上传签名
    addLog('获取上传签名...', 'info')
    const signatureResponse = await api.post('/upload/direct-signature', {
      fileName: selectedFile.value.name,
      fileType: selectedFile.value.type,
      fileHash: fileHash,
      bucket: 'default',
      service: 'test',
      folder: 'test-uploads'
    })

    const signatureData = signatureResponse.data
    
    if (!signatureData.needUpload) {
      addLog('文件已存在，无需重复上传（去重成功）', 'success')
      uploadResult.value = {
        fileHash,
        fileUrl: signatureData.existingFileUrl,
        needUpload: false,
        duration: Date.now() - startTime
      }
      return
    }

    addLog('开始上传到 RustFS...', 'info')

    // 2. 直传到 RustFS
    const formData = new FormData()
    formData.append('key', signatureData.fileKey)
    formData.append('policy', signatureData.signature.policy)
    formData.append('x-amz-algorithm', 'AWS4-HMAC-SHA256')
    formData.append('x-amz-credential', signatureData.signature.fields['x-amz-credential'])
    formData.append('x-amz-date', signatureData.signature.fields['x-amz-date'])
    formData.append('x-amz-signature', signatureData.signature.signature)
    formData.append('file', selectedFile.value)

    await axios.post(signatureData.signature.url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    addLog('RustFS 上传成功，通知后端记录...', 'success')

    // 3. 通知后端记录
    const recordResponse = await api.post('/upload/record-direct-upload', {
      fileHash: fileHash,
      fileKey: signatureData.fileKey,
      fileUrl: signatureData.fileUrl,
      originalFileName: selectedFile.value.name,
      fileSize: selectedFile.value.size,
      contentType: selectedFile.value.type,
      bucketName: signatureData.bucketName,  // 使用后端返回的bucket名称
      service: 'test'
    })

    const recordData = recordResponse.data
    
    if (recordData.success) {
      addLog('上传完成！', 'success')
      uploadResult.value = {
        fileHash,
        fileUrl: signatureData.fileUrl,
        fileKey: signatureData.fileKey,
        needUpload: true,
        duration: Date.now() - startTime
      }
    } else {
      throw new Error(recordData.message || '记录文件失败')
    }

  } catch (error) {
    addLog(`上传失败: ${error.message}`, 'error')
    uploadResult.value = { error: error.message }
  } finally {
    uploading.value = false
  }
}

// 并发上传测试
async function testConcurrentUpload() {
  if (!selectedFile.value) return

  uploading.value = true
  concurrentResults.value = []
  addLog(`开始并发上传测试，并发数: ${concurrentCount.value}`, 'info')

  const fileHash = await calculateFileHash(selectedFile.value)
  addLog(`文件哈希: ${fileHash}`, 'info')

  // 创建多个并发上传任务
  const uploadTasks = []
  for (let i = 0; i < concurrentCount.value; i++) {
    uploadTasks.push(performUpload(i + 1, fileHash))
  }

  // 等待所有任务完成
  const results = await Promise.allSettled(uploadTasks)
  concurrentResults.value = results.map((result, index) => {
    if (result.status === 'fulfilled') {
      return result.value
    } else {
      return {
        index: index + 1,
        success: false,
        message: result.reason.message,
        duration: 0
      }
    }
  })

  uploading.value = false
  addLog('并发测试完成', 'success')
}

async function performUpload(index, fileHash) {
  const startTime = Date.now()
  
  try {
    // 获取签名
    const signatureResponse = await api.post('/upload/direct-signature', {
      fileName: selectedFile.value.name,
      fileType: selectedFile.value.type,
      fileHash: fileHash,
      bucket: 'default',
      service: 'test',
      folder: 'concurrent-test'
    })

    const signatureData = signatureResponse.data
    const duration = Date.now() - startTime

    if (!signatureData.needUpload) {
      return {
        index,
        success: true,
        message: '文件已存在（去重）',
        duration
      }
    }

    // 上传到 RustFS
    const formData = new FormData()
    formData.append('key', signatureData.fileKey)
    formData.append('policy', signatureData.signature.policy)
    formData.append('x-amz-algorithm', 'AWS4-HMAC-SHA256')
    formData.append('x-amz-credential', signatureData.signature.fields['x-amz-credential'])
    formData.append('x-amz-date', signatureData.signature.fields['x-amz-date'])
    formData.append('x-amz-signature', signatureData.signature.signature)
    formData.append('file', selectedFile.value)

    await axios.post(signatureData.signature.url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    // 记录
    await api.post('/upload/record-direct-upload', {
      fileHash: fileHash,
      fileKey: signatureData.fileKey,
      fileUrl: signatureData.fileUrl,
      originalFileName: selectedFile.value.name,
      fileSize: selectedFile.value.size,
      contentType: selectedFile.value.type,
      bucketName: signatureData.bucketName,  // 使用后端返回的bucket名称
      service: 'test'
    })

    return {
      index,
      success: true,
      message: '上传成功',
      duration: Date.now() - startTime
    }

  } catch (error) {
    return {
      index,
      success: false,
      message: error.message,
      duration: Date.now() - startTime
    }
  }
}

// 去重测试
async function testDeduplication() {
  if (!selectedFile.value) return

  uploading.value = true
  dedupResults.value = []
  addLog('开始去重测试...', 'info')

  const fileHash = await calculateFileHash(selectedFile.value)

  // 第一次上传
  addLog('第1次上传...', 'info')
  const result1 = await performFullUpload(fileHash)
  dedupResults.value.push(result1)

  // 等待1秒
  await new Promise(resolve => setTimeout(resolve, 1000))

  // 第二次上传（应该被去重）
  addLog('第2次上传（测试去重）...', 'info')
  const result2 = await performFullUpload(fileHash)
  dedupResults.value.push(result2)

  uploading.value = false
  addLog('去重测试完成', 'success')
}

async function performFullUpload(fileHash) {
  const startTime = Date.now()

  const signatureResponse = await api.post('/upload/direct-signature', {
    fileName: selectedFile.value.name,
    fileType: selectedFile.value.type,
    fileHash: fileHash,
    bucket: 'default',
    service: 'test',
    folder: 'dedup-test'
  })

  const signatureData = signatureResponse.data

  if (!signatureData.needUpload) {
    return {
      fileHash,
      needUpload: false,
      fileUrl: signatureData.existingFileUrl,
      duration: Date.now() - startTime
    }
  }

  // 上传
  const formData = new FormData()
  formData.append('key', signatureData.fileKey)
  formData.append('policy', signatureData.signature.policy)
  formData.append('x-amz-algorithm', 'AWS4-HMAC-SHA256')
  formData.append('x-amz-credential', signatureData.signature.fields['x-amz-credential'])
  formData.append('x-amz-date', signatureData.signature.fields['x-amz-date'])
  formData.append('x-amz-signature', signatureData.signature.signature)
  formData.append('file', selectedFile.value)

  await axios.post(signatureData.signature.url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

  // 记录
  await api.post('/upload/record-direct-upload', {
    fileHash: fileHash,
    fileKey: signatureData.fileKey,
    fileUrl: signatureData.fileUrl,
    originalFileName: selectedFile.value.name,
    fileSize: selectedFile.value.size,
    contentType: selectedFile.value.type,
    bucketName: signatureData.bucketName,  // 使用后端返回的bucket名称
    service: 'test'
  })

  return {
    fileHash,
    needUpload: true,
    fileUrl: signatureData.fileUrl,
    duration: Date.now() - startTime
  }
}

// 模拟上传失败
async function testFailedUpload() {
  if (!selectedFile.value) return

  uploading.value = true
  addLog('模拟上传失败（不调用record接口）...', 'info')

  try {
    const fileHash = await calculateFileHash(selectedFile.value)
    failedHash.value = fileHash

    // 只获取签名，不上传也不记录
    const signatureResponse = await api.post('/upload/direct-signature', {
      fileName: selectedFile.value.name,
      fileType: selectedFile.value.type,
      fileHash: fileHash,
      bucket: 'default',
      service: 'test',
      folder: 'retry-test'
    })

    const signatureData = signatureResponse.data
    
    addLog('已创建数据库记录（状态=Uploading），但未完成上传', 'warning')
    addLog('现在可以点击"重试上传"按钮测试重试机制', 'info')
    
    retryResult.value = {
      step: 'failed',
      fileHash,
      message: '模拟失败：数据库记录已创建但未上传文件'
    }

  } catch (error) {
    addLog(`操作失败: ${error.message}`, 'error')
  } finally {
    uploading.value = false
  }
}

// 重试上传
async function testRetryUpload() {
  if (!selectedFile.value || !failedHash.value) return

  uploading.value = true
  addLog('开始重试上传...', 'info')

  try {
    // 等待5秒，模拟超时
    addLog('等待5秒后重试（测试超时清理机制）...', 'info')
    await new Promise(resolve => setTimeout(resolve, 5000))

    // 重新上传
    const result = await performFullUpload(failedHash.value)
    
    addLog('重试上传成功！', 'success')
    retryResult.value = {
      step: 'retry',
      ...result,
      message: '重试成功：系统检测到超时记录并允许重新上传'
    }

  } catch (error) {
    addLog(`重试失败: ${error.message}`, 'error')
  } finally {
    uploading.value = false
  }
}

// ========== 同步相关方法 ==========

// 获取同步状态
async function getSyncStatus() {
  if (!syncBucket.value) {
    addLog('请输入 Bucket 名称', 'error')
    return
  }

  syncing.value = true
  syncResult.value = null
  addLog(`查询 ${syncBucket.value} 的同步状态...`, 'info')

  try {
    const response = await api.get('/admin/sync/status', {
      params: { bucketName: syncBucket.value }
    })

    if (response.data.success) {
      syncStatus.value = response.data.data
      addLog('同步状态查询成功', 'success')
      
      if (syncStatus.value.needSync) {
        addLog(`发现 ${syncStatus.value.missingRecordCount} 个缺失记录，${syncStatus.value.orphanedRecordCount} 个孤儿记录`, 'warning')
      } else {
        addLog('数据库与 RustFS 已同步', 'success')
      }
    }
  } catch (error) {
    const message = error.response?.data?.message || error.message
    addLog(`查询失败: ${message}`, 'error')
  } finally {
    syncing.value = false
  }
}

// 同步 RustFS 到数据库
async function syncRustFSToDb() {
  if (!syncBucket.value) {
    addLog('请输入 Bucket 名称', 'error')
    return
  }

  syncing.value = true
  syncStatus.value = null
  addLog(`开始同步 ${syncBucket.value} 到数据库...`, 'info')

  try {
    const response = await api.post('/admin/sync/rustfs-to-db', null, {
      params: { bucketName: syncBucket.value }
    })

    syncResult.value = response.data
    
    if (response.data.success) {
      addLog(`同步成功！已同步 ${response.data.data.syncedFiles} 个文件`, 'success')
    } else {
      addLog(`同步失败: ${response.data.message}`, 'error')
    }
  } catch (error) {
    const message = error.response?.data?.message || error.message
    addLog(`同步失败: ${message}`, 'error')
    syncResult.value = {
      success: false,
      message: `同步失败: ${message}`
    }
  } finally {
    syncing.value = false
  }
}

// 清理孤儿记录
async function cleanOrphanedRecords() {
  if (!syncBucket.value) {
    addLog('请输入 Bucket 名称', 'error')
    return
  }

  syncing.value = true
  syncStatus.value = null
  addLog(`开始清理 ${syncBucket.value} 的孤儿记录...`, 'info')

  try {
    const response = await api.post('/admin/sync/clean-orphaned', null, {
      params: { bucketName: syncBucket.value }
    })

    syncResult.value = response.data
    
    if (response.data.success) {
      addLog(`清理成功！已清理 ${response.data.data.cleanedRecords} 条孤儿记录`, 'success')
    } else {
      addLog(`清理失败: ${response.data.message}`, 'error')
    }
  } catch (error) {
    const message = error.response?.data?.message || error.message
    addLog(`清理失败: ${message}`, 'error')
    syncResult.value = {
      success: false,
      message: `清理失败: ${message}`
    }
  } finally {
    syncing.value = false
  }
}

// 完整同步
async function fullSync() {
  if (!syncBucket.value) {
    addLog('请输入 Bucket 名称', 'error')
    return
  }

  syncing.value = true
  syncStatus.value = null
  addLog(`开始完整同步 ${syncBucket.value}...`, 'info')

  try {
    const response = await api.post('/admin/sync/full-sync', null, {
      params: { bucketName: syncBucket.value }
    })

    syncResult.value = response.data
    
    if (response.data.success) {
      const syncData = response.data.data.sync
      const cleanData = response.data.data.clean
      addLog(`完整同步成功！`, 'success')
      addLog(`- 同步文件: ${syncData.syncedFiles}/${syncData.totalFiles}`, 'success')
      addLog(`- 清理记录: ${cleanData.cleanedRecords}/${cleanData.totalRecords}`, 'success')
    } else {
      addLog(`完整同步失败: ${response.data.message}`, 'error')
    }
  } catch (error) {
    const message = error.response?.data?.message || error.message
    addLog(`完整同步失败: ${message}`, 'error')
    syncResult.value = {
      success: false,
      message: `完整同步失败: ${message}`
    }
  } finally {
    syncing.value = false
  }
}
</script>

<style scoped>
.upload-test-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

h1 {
  color: #333;
  border-bottom: 2px solid #4CAF50;
  padding-bottom: 10px;
}

h2 {
  color: #555;
  margin-top: 30px;
}

h3 {
  color: #666;
  margin-bottom: 10px;
}

.description {
  color: #777;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 20px;
  background: #f5f5f5;
  padding: 10px;
  border-left: 3px solid #2196F3;
}

.test-scenarios {
  margin-bottom: 30px;
}

.scenario-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.scenario-buttons button {
  padding: 10px 20px;
  border: 2px solid #ddd;
  background: white;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.3s;
}

.scenario-buttons button.active {
  background: #4CAF50;
  color: white;
  border-color: #4CAF50;
}

.scenario-buttons button:hover {
  border-color: #4CAF50;
}

.test-panel {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  margin-bottom: 30px;
}

input[type="file"] {
  margin: 10px 0;
  padding: 10px;
  border: 2px dashed #ddd;
  border-radius: 4px;
  width: 100%;
  cursor: pointer;
}

input[type="number"] {
  padding: 5px 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  width: 80px;
}

.upload-btn {
  background: #4CAF50;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  margin: 10px 5px;
  transition: background 0.3s;
}

.upload-btn:hover:not(:disabled) {
  background: #45a049;
}

.upload-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.clear-btn {
  background: #f44336;
  color: white;
  padding: 5px 15px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  margin-bottom: 10px;
}

.concurrent-controls,
.retry-controls {
  display: flex;
  align-items: center;
  gap: 15px;
  margin: 15px 0;
}

.result {
  margin-top: 20px;
  padding: 15px;
  background: #f9f9f9;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.result h4 {
  margin-top: 0;
  color: #333;
}

.result pre {
  background: #2d2d2d;
  color: #f8f8f2;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 13px;
}

.preview-image {
  max-width: 300px;
  max-height: 300px;
  margin-top: 15px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.concurrent-result-item,
.dedup-result-item {
  padding: 10px;
  margin: 10px 0;
  background: white;
  border-left: 3px solid #2196F3;
  border-radius: 4px;
}

.success {
  color: #4CAF50;
  font-weight: bold;
}

.error {
  color: #f44336;
  font-weight: bold;
}

.time {
  color: #999;
  font-size: 12px;
  margin-left: 10px;
}

.message {
  color: #666;
  font-size: 13px;
  margin-top: 5px;
}

.logs {
  margin-top: 30px;
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.log-content {
  max-height: 400px;
  overflow-y: auto;
  background: #1e1e1e;
  padding: 15px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.log-item {
  padding: 5px 0;
  border-bottom: 1px solid #333;
}

.log-item.info {
  color: #61dafb;
}

.log-item.success {
  color: #4CAF50;
}

.log-item.error {
  color: #f44336;
}

.log-item.warning {
  color: #ff9800;
}

.log-time {
  color: #888;
  margin-right: 10px;
}

.log-message {
  color: inherit;
}

/* 同步相关样式 */
.sync-controls {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin: 20px 0;
}

.input-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input-group label {
  min-width: 120px;
  font-weight: bold;
  color: #333;
}

.input-group input[type="text"],
.input-group input[type="password"] {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.sync-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.sync-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
  background: #2196F3;
  color: white;
}

.sync-btn.primary {
  background: #4CAF50;
}

.sync-btn.warning {
  background: #ff9800;
}

.sync-btn.success {
  background: #2196F3;
}

.sync-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}

.sync-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  transform: none;
}

.sync-status {
  background: #f5f5f5;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-top: 10px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  background: white;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.status-item .label {
  font-weight: bold;
  color: #666;
}

.status-item .value {
  font-weight: bold;
  color: #333;
}

.status-item .value.warning {
  color: #ff9800;
}

.status-item .value.error {
  color: #f44336;
}

.status-item .value.success {
  color: #4CAF50;
}

.sync-result-summary {
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 15px;
}

.sync-result-summary.success {
  background: #e8f5e9;
  border: 1px solid #4CAF50;
}

.sync-result-summary.error {
  background: #ffebee;
  border: 1px solid #f44336;
}

.sync-result-details {
  background: white;
  padding: 15px;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row.success {
  color: #4CAF50;
}

.detail-row.error {
  color: #f44336;
}

.full-sync-section {
  margin-top: 15px;
  padding: 10px;
  background: #f9f9f9;
  border-radius: 4px;
}

.full-sync-section h5 {
  margin: 0 0 10px 0;
  color: #333;
}

.sync-details-log {
  margin-top: 15px;
  padding: 15px;
  background: #f5f5f5;
  border-radius: 4px;
}

.sync-details-log h5 {
  margin-top: 0;
  color: #333;
}

.log-list {
  max-height: 200px;
  overflow-y: auto;
  background: white;
  padding: 10px;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.log-line {
  padding: 5px 0;
  font-size: 13px;
  font-family: monospace;
  color: #333;
  border-bottom: 1px solid #f0f0f0;
}

.log-line:last-child {
  border-bottom: none;
}

.log-more {
  padding: 10px;
  text-align: center;
  color: #666;
  font-style: italic;
}
</style>
