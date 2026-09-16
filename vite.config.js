import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 前后端分离开发配置：
//  - npm run dev 时，/api 与 /ws 由 Vite 代理到 Spring Boot（8080），与生产环境 nginx 行为一致；
//  - npm run build 后产物 dist/ 交给 nginx 托管（html 目录），由 nginx 完成 /api、/ws 反代。
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false
  }
});
