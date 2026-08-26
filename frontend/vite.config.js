import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    // In development the browser talks to Vite, which forwards API calls to Spring Boot.
    // This keeps the frontend on a single origin so no CORS preflight is involved locally.
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.js'],
    coverage: {
      include: ['src/**/*.js'],
    },
  },
});
