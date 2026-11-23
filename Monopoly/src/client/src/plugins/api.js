import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('currentUser')
    }
    return Promise.reject(error)
  },
)

/**
 * Safely parse a backend GameMessage (or plain object) into its payload object.
 */
export function parseGamePayload(data) {
  if (!data) return {}
  if (data.payload === undefined || data.payload === null) return data
  if (typeof data.payload === 'string') {
    const trimmed = data.payload.trim()
    if (!trimmed) return {}
    try {
      return JSON.parse(trimmed)
    } catch {
      return { message: data.payload }
    }
  }
  return data.payload
}

/**
 * Extract a user-facing error message from an Axios/GameMessage error response.
 */
export function getApiError(error, fallback = 'Không thể kết nối máy chủ.') {
  const responseData = error?.response?.data
  if (responseData) {
    const parsed = parseGamePayload(responseData)
    if (parsed && typeof parsed === 'object') {
      if (parsed.message) return parsed.message
      if (parsed.error) return parsed.error
    }
    if (responseData.message) return responseData.message
    if (responseData.error) return responseData.error
  }
  return error?.message || fallback
}

export default api
