import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = "Bearer " + token
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

export function getApiError(error, fallback = 'Không thể kết nối máy chủ.') {
  const payload = error?.response?.data?.payload
  if (payload) {
    try {
      const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload
      return parsed.message || parsed.error || fallback
    } catch {
      return fallback
    }
  }
  return error?.response?.data?.message || fallback
}

export default api
