# 文件服务管理后台

基于 Vue 3 + TypeScript + Vite + Tailwind CSS 构建的管理后台界面。

## 功能特性

- 🔐 管理员登录/登出
- 🪣 存储桶管理（创建、删除、列表）
- 📊 存储表管理（创建）
- 📁 文件管理（删除）

## 技术栈

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Tailwind CSS

## 开发

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

应用将在 http://localhost:3000 启动。

### 构建生产版本

```bash
npm run build
```

## 配置

开发环境下的 API 请求会自动代理到 `http://localhost:5000`（后端服务地址）。

如需修改，请编辑 `vite.config.js` 中的 proxy 配置。

