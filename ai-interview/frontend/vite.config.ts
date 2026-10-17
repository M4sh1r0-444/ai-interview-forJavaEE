import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// Element Plus 组件按需注册；样式在 main.ts 一次性引入，避免组件级样式重复打包。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'
  const wsTarget = proxyTarget.replace(/^http/, 'ws')

  return {
    plugins: [
      vue(),
      Components({
        dts: 'src/components.d.ts',
        resolvers: [ElementPlusResolver({ importStyle: false })],
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      // 计划 §3.3：固定 5173，端口被占时直接失败，避免代理指向错误进程。
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
        '/ws': {
          target: wsTarget,
          changeOrigin: true,
          ws: true,
        },
      },
    },
    build: {
      target: 'es2020',
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
          },
        },
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          // Vite 5 + Dart Sass 的 legacy JS API 已废弃；显式走 modern compiler。
          api: 'modern-compiler',
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.spec.ts'],
      css: false,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'json-summary', 'html'],
        include: ['src/api/**/*.ts', 'src/stores/**/*.ts', 'src/utils/**/*.ts'],
        exclude: ['src/**/__tests__/**', 'src/**/*.d.ts'],
        reportsDirectory: './coverage',
      },
    },
  }
})
