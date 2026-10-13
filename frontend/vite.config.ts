import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * 开发时前端跑在 5199，后端（Spring Boot）跑在 8080，用 proxy 打通：
 *   npm run dev   → 只起前端，另开一个终端跑 ./mvnw spring-boot:run
 *   npm run full  → 前后端一起起（见 scripts/dev.sh）
 *
 * 打包后不需要代理：前端产物被复制进 jar 的 static/，由 Spring Boot 同源托管。
 * 后端地址可用环境变量覆盖，例如 .env.local 里写 BACKEND_URL=http://127.0.0.1:9000
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const backend = env.BACKEND_URL || 'http://127.0.0.1:8080'

  return {
    plugins: [react()],
    base: './',
    server: {
      port: 5199,
      open: true,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
        // 发音 mp3 由 Spring Boot 从 jar 内的 static/audio 提供
        '/audio': { target: backend, changeOrigin: true },
      },
    },
    build: { outDir: 'dist', assetsInlineLimit: 0 },
  }
})
