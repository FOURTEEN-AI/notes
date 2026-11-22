import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Local loop: the backend runs with `./gradlew bootTestRun` on port 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
