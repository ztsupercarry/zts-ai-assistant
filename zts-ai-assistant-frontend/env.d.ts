/// <reference types="vite/client" />

/** 打开 Vite 的 env 严格校验：VITE_ 开头变量名写错时会直接报类型错误 */
interface ViteTypeOptions {
  strictImportMetaEnv: unknown
}

interface ImportMetaEnv {
  /** 后端接口地址前缀，例如 /api（开发代理）或 http://localhost:8123/api */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
