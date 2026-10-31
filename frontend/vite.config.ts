import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on 5173 proxies /api to the gateway, so the browser only sees one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
});
