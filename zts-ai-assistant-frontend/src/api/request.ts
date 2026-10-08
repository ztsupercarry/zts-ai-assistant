import axios, { type AxiosRequestConfig } from 'axios'

/**
 * 后端接口地址前缀。
 * 开发环境建议保持默认的 `/api`（由 vite.config.ts 的 server.proxy 转发到
 * http://localhost:8123，可规避浏览器跨域限制）；生产环境可在 .env.production 中
 * 配成 http://localhost:8123/api。
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

/**
 * 全局 axios 实例：统一 baseURL 与超时时间。
 * 错误文案统一由 resolveErrorMessage 处理（不做响应拦截改写，避免丢掉 AxiosError 的
 * code / response，那样连「请求已取消」都判断不了）。
 */
export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  headers: { Accept: 'application/json' },
})

/** GET 请求，直接返回 response.data；失败时抛出可直接展示的中文错误 */
export async function get<T>(url: string, config: AxiosRequestConfig = {}): Promise<T> {
  try {
    const response = await http.get<T>(url, config)
    return response.data
  } catch (error) {
    throw new Error(resolveErrorMessage(error))
  }
}

/** POST 请求，直接返回 response.data；失败时抛出可直接展示的中文错误 */
export async function post<T>(
  url: string,
  data?: unknown,
  config: AxiosRequestConfig = {},
): Promise<T> {
  try {
    const response = await http.post<T>(url, data, config)
    return response.data
  } catch (error) {
    throw new Error(resolveErrorMessage(error))
  }
}

/**
 * 把接口相对路径拼成完整地址。
 * SSE 走原生 fetch，需要完整 URL，而 axios 的 baseURL 不会自动生效。
 */
export function resolveApiUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path
  const base = API_BASE_URL.replace(/\/+$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

/** 把各种异常（axios / fetch / 业务异常）转换成可以直接展示给用户的文案 */
export function resolveErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const detail = readMessage(error.response?.data)
    if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
    if (error.code === 'ERR_CANCELED') return '请求已取消'
    if (error.response) {
      return `请求失败（HTTP ${error.response.status}）${detail ? `：${detail}` : ''}`
    }
    return `无法连接到后端服务，请确认后端已在 http://localhost:8123 启动`
  }

  if (error instanceof Error) {
    // 原生 fetch 在网络不通 / 代理失败 / 被 CORS 拦截时只会给一句英文（如 TypeError: Failed to fetch）
    if (/failed to fetch|networkerror|load failed|fetch failed/i.test(error.message)) {
      return '无法连接到后端服务，请确认后端已在 http://localhost:8123 启动（开发环境请检查 vite 代理配置）'
    }
    return error.message
  }

  return String(error)
}

/** 从后端返回体里尽力取出 message 字段 */
function readMessage(data: unknown): string | undefined {
  if (
    data !== null &&
    typeof data === 'object' &&
    'message' in data &&
    typeof data.message === 'string'
  ) {
    return data.message
  }
  if (typeof data === 'string' && data.trim()) return data.slice(0, 200)
  return undefined
}
