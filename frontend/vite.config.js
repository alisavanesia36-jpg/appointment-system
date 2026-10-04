import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// HBuilderX 3.2.0+ uni-app Vue3(H5) 支持项目根目录 vite.config.js。
// 为了确保我们新增的 server.proxy 不会因为缺少 uni 插件而被 HBuilderX 忽略，
// 这里显式声明 plugins: [uni()]，并使用对象形式 defineConfig（最小兼容形态）。
// 代理效果：
//   浏览器请求:  POST http://localhost:5173/backend-api/auth/register
//   Vite 转发:   POST http://localhost:8080/auth/register
export default defineConfig({
  plugins: [uni()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    proxy: {
      '/backend-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: false,
        rewrite: (path) => path.replace(/^\/backend-api/, '')
      }
    }
  }
})
