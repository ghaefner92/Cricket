import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/geocode': { target: 'https://imiq-public.et.uni-magdeburg.de', changeOrigin: true },
      '/api/dyconet': {
        target: 'http://127.0.0.1:8077',
        changeOrigin: true,
        rewrite: (path) =>
          path === '/api/dyconet/health' ? '/health' : path,
      },
    },
  },
})