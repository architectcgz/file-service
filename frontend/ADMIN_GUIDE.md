# 签名管理系统前端使用指南

## 📋 页面概览

### 1. 管理员登录页 (`/login`)
- **路径**: `http://localhost:3000/login`
- **功能**: 管理员身份验证
- **默认凭据**:
  - 用户名: `admin`
  - 密码: `admin`

### 2. 签名管理后台 (`/admin`)
- **路径**: `http://localhost:3000/admin`
- **功能**: 完整的签名管理系统
- **要求**: 需要登录

### 3. 上传测试页
- 直传签名测试: `http://localhost:3000/`
- API上传测试: `http://localhost:3000/api-upload-test`

## 🚀 快速开始

### 1. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问: `http://localhost:3000`

### 2. 登录管理后台

1. 点击任意测试页面右上角的 **🔐 管理后台** 按钮
2. 或直接访问 `http://localhost:3000/login`
3. 输入默认凭据登录
4. 自动跳转到签名管理页面

## 📊 功能说明

### 仪表盘 (Dashboard)

**统计卡片**:
- 📊 总签名数 - 所有签名总数
- ✅ 活跃签名 - 当前有效的签名数
- ⏰ 已过期 - 过期但未清理的签名
- ❌ 已撤销 - 手动撤销的签名

**今日统计**:
- 今日颁发 - 今天颁发的签名数量
- 今日使用 - 今天使用的签名次数

**按服务统计**:
- 显示各个调用方服务的签名数量

### 签名列表 (List)

**筛选功能**:
- 服务名称 - 模糊搜索
- 状态筛选 - active/expired/revoked
- 操作类型 - upload/download/delete/*

**批量操作**:
- ✅ 全选/取消全选
- 🗑️ 批量撤销
- 🧹 清理过期签名

**单个操作**:
- 👁️ 查看详情
- ❌ 撤销签名

**详情弹窗**:
- 基本信息（ID、Token、状态）
- 调用方信息（服务名称、服务ID、IP）
- 权限配置（操作类型、文件类型、大小限制）
- 使用情况（使用次数、最后使用时间）
- 时间信息（创建、过期、撤销时间）

### 颁发签名 (Issue)

**必填字段**:
- 调用方服务名称
- 允许的操作类型
- 有效期（分钟）

**可选字段**:
- 调用方服务ID
- 允许的文件类型（逗号分隔）
- 最大文件大小（字节）
- 最大使用次数（0=无限制）
- 备注信息

**快速预设**:
- 📷 图片上传 - image类型，10MB，60分钟
- 📄 文档上传 - document类型，50MB，120分钟
- 🔒 一次性上传 - 仅使用1次，30分钟

**成功响应**:
- 显示颁发成功消息
- 展示签名Token
- 提供复制按钮
- 3秒后自动重置表单

## 🎯 使用场景

### 场景1: 为博客系统颁发上传签名

1. 进入"颁发签名"标签
2. 填写:
   - 服务名称: `blog-api`
   - 操作类型: `upload`
   - 文件类型: `image`
   - 最大大小: `10485760` (10MB)
   - 有效期: `60` 分钟
   - 备注: `博客封面图上传`
3. 点击"颁发签名"
4. 复制生成的Token给博客后端

### 场景2: 创建一次性文件上传签名

1. 点击"一次性上传"快速预设
2. 修改服务名称为实际服务
3. 点击"颁发签名"
4. Token仅可使用1次

### 场景3: 撤销泄露的签名

**方法1 - 单个撤销**:
1. 进入"签名列表"
2. 找到目标签名
3. 点击"撤销"
4. 输入撤销原因
5. 确认撤销

**方法2 - 批量撤销**:
1. 勾选多个签名
2. 点击"批量撤销"
3. 输入撤销原因
4. 确认撤销

### 场景4: 定期清理过期签名

1. 进入"签名列表"
2. 点击"清理过期签名"
3. 系统自动清理所有过期但未标记的签名
4. 显示清理数量

## 🔐 安全建议

### 登录安全
- ✅ 首次登录后立即修改密码
- ✅ 使用强密码
- ✅ 定期更换密码
- ✅ 不要分享登录凭据

### 签名管理
- ✅ 为不同服务创建专属签名
- ✅ 设置合理的过期时间
- ✅ 敏感操作使用一次性签名
- ✅ 定期审查活跃签名
- ✅ 及时撤销可疑签名

### 监控建议
- 📊 每日查看统计信息
- 🔍 检查异常使用模式
- 🧹 每周清理过期签名
- 📝 保留撤销记录

## 🛠️ 常见问题

### Q: 登录后自动跳转回登录页？
**A**: 检查Session是否正常，查看浏览器控制台错误信息。确保后端Session中间件已启用。

### Q: 无法颁发签名？
**A**: 
1. 确认已登录
2. 检查必填字段
3. 查看浏览器控制台网络请求
4. 确认后端服务正常运行

### Q: 签名列表为空？
**A**: 
1. 先颁发几个测试签名
2. 检查筛选条件是否过于严格
3. 确认数据库连接正常

### Q: 如何修改密码？
**A**: 
1. 使用后端API生成新密码哈希:
   ```bash
   curl -X POST http://localhost:5003/api/admin/generate-password-hash \
     -H "Content-Type: application/json" \
     -d '{"password":"new-password"}'
   ```
2. 更新 `appsettings.Development.json` 中的 `AdminPasswordHash`
3. 重启后端服务

## 📱 响应式设计

- ✅ 支持桌面端（最佳体验）
- ✅ 支持平板（md断点）
- ✅ 支持手机（基本功能）

## 🎨 UI组件

### 已创建的组件
- `StatCard.vue` - 统计卡片
- `SignatureList.vue` - 签名列表
- `SignatureDetailModal.vue` - 详情弹窗
- `IssueSignature.vue` - 颁发签名表单

### 页面
- `SignatureManagement.vue` - 主管理页面
- `Login.vue` - 登录页面（已存在）

## 🔄 状态管理

使用 Pinia Store (`stores/auth.ts`):
- `isLoggedIn` - 登录状态
- `username` - 用户名
- `login()` - 登录方法
- `logout()` - 登出方法
- `checkStatus()` - 检查登录状态

## 🌐 API调用

所有API调用通过 `api/admin.ts`:
- `login()` - 登录
- `logout()` - 登出
- `issueSignature()` - 颁发签名
- `getSignatures()` - 获取签名列表
- `revokeSignature()` - 撤销签名
- `batchRevokeSignatures()` - 批量撤销
- `cleanExpiredSignatures()` - 清理过期
- `getSignatureStatistics()` - 获取统计

## 📝 开发说明

### 文件结构
```
frontend/
├── src/
│   ├── views/
│   │   ├── SignatureManagement.vue  # 主管理页
│   │   ├── Login.vue                # 登录页
│   │   ├── UploadTest.vue           # 上传测试
│   │   └── ApiUploadTest.vue        # API上传测试
│   ├── components/
│   │   ├── StatCard.vue             # 统计卡片
│   │   ├── SignatureList.vue        # 签名列表
│   │   ├── SignatureDetailModal.vue # 详情弹窗
│   │   └── IssueSignature.vue       # 颁发表单
│   ├── stores/
│   │   └── auth.ts                  # 认证状态
│   ├── api/
│   │   └── admin.ts                 # API封装
│   └── router/
│       └── index.ts                 # 路由配置
└── ADMIN_GUIDE.md                   # 本文档
```

### 路由配置
- `/` - 直传签名测试
- `/api-upload-test` - API上传测试
- `/login` - 登录页
- `/admin` - 管理后台（需认证）

### 添加新功能
1. 在 `api/admin.ts` 添加API方法
2. 创建新组件或页面
3. 在路由中注册（如需要）
4. 添加导航链接

## 🎉 完成！

现在您可以：
1. 登录管理后台
2. 查看签名统计
3. 颁发新签名
4. 管理现有签名
5. 清理过期数据

享受使用签名管理系统！
