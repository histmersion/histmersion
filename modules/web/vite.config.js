import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

export default defineConfig({
  plugins: [
    scalaJSPlugin({
      cwd: "../..",
      projectID: "web",
    }),
  ],
  server: {
    port: 3000,
    proxy: {
      "/api": "http://localhost:8000",
      "/web": "http://localhost:8000",
      "/article": "http://localhost:8000",
    },
  },
});
