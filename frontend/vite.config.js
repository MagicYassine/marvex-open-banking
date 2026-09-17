import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/payments': 'http://localhost:8080',
      '/account': 'http://localhost:8080',
      '/webhooks': 'http://localhost:8080',
      '/institutions': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
  },
})
