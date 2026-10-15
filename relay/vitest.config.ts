import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [cloudflareTest(async () => ({
    wrangler: { configPath: "./wrangler.jsonc" },
    miniflare: { bindings: {
      TEST_MIGRATIONS: await readD1Migrations("./migrations"),
      CAPABILITY_PEPPER: "synthetic-test-only-pepper-0000000000000000",
      RECOVERY_EPOCH: "test-epoch-000000000000000000000000"
    } }
  }))],
  test: { testTimeout: 30000 }
});
