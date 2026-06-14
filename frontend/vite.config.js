import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue({
    template: {
      compilerOptions: {
        isCustomElement: (tag) => tag === 'model-viewer'
      }
    }
  })],
  base: '/',
  build: {
    rollupOptions: {
      output: {
        chunkFileNames: (chunkInfo) => {
          const name = chunkInfo.name.replace(/[^a-zA-Z0-9_-]/g, '_');
          return `assets/${name}-[hash].js`;
        },
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          const name = assetInfo.name ? assetInfo.name.replace(/[^a-zA-Z0-9._-]/g, '_') : '[name]';
          return `assets/${name}-[hash].[ext]`;
        }
      }
    }
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',   // 本地后端
        changeOrigin: true,
        timeout: 30000
      }
    }
  }
})
