import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserRound, LockKeyhole, Eye, EyeOff, AlertCircle, ArrowRight } from 'lucide-react'
import Logo from '../../components/Logo'
import api, { getApiError, parseGamePayload } from '../../plugins/api'

export default function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const handleLogin = async (event) => {
    event.preventDefault()
    if (!username.trim() || !password) {
      setErrorMsg('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.')
      return
    }
    setErrorMsg('')
    setLoading(true)
    try {
      const response = await api.post('/api/v1/auth/login', {
        username: username.trim(),
        password,
      })
      if (response.data?.type === 'ERROR') {
        const errPayload = parseGamePayload(response.data)
        setErrorMsg(errPayload.message || errPayload.error || 'Đăng nhập thất bại.')
        return
      }
      const payload = parseGamePayload(response.data)
      const token = payload.accessToken || payload.access_token
      if (!token) {
        setErrorMsg('Máy chủ không trả về token xác thực.')
        return
      }
      localStorage.setItem('accessToken', token)
      localStorage.setItem('currentUser', JSON.stringify(payload.user || payload))
      sessionStorage.removeItem('activeRoomId')
      navigate('/', { replace: true })
    } catch (error) {
      setErrorMsg(getApiError(error, 'Tên đăng nhập hoặc mật khẩu không đúng.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="auth-page player-pass-page">
      <div className="board-pattern" />
      <div className="auth-decoration deco-a">★</div>
      <div className="auth-decoration deco-b">●</div>
      <section className="auth-card player-pass-card">
        <div className="pass-eyebrow">Player Pass Card</div>
        <Logo />
        <h1>Chào mừng trở lại!</h1>
        <p className="muted">Đăng nhập để tham gia bàn cờ Monopoly Party</p>
        <div className="auth-tabs">
          <button type="button" className="active">Đăng nhập</button>
          <Link to="/register">Đăng ký</Link>
        </div>
        {errorMsg && (
          <div className="auth-error" role="alert">
            <AlertCircle size={15} />
            <span>{errorMsg}</span>
          </div>
        )}
        <form onSubmit={handleLogin}>
          <label>
            Tên đăng nhập
            <div className="auth-input">
              <UserRound size={16} />
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Nhập tên đăng nhập"
                autoComplete="username"
                autoFocus
              />
            </div>
          </label>
          <label>
            Mật khẩu
            <div className="auth-input">
              <LockKeyhole size={16} />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Nhập mật khẩu"
                autoComplete="current-password"
              />
              <button
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </label>
          <button type="submit" className="primary wide login-primary" disabled={loading}>
            {loading ? 'Đang đăng nhập...' : 'Vào chơi ngay'} <ArrowRight size={17} />
          </button>
        </form>
        <p className="auth-switch">
          Chưa có thẻ người chơi? <Link to="/register">Tạo tài khoản</Link>
        </p>
      </section>
    </main>
  )
}
