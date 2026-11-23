import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserRound, CircleUserRound, LockKeyhole, Eye, EyeOff, AlertCircle, ArrowRight } from 'lucide-react'
import Logo from '../../components/Logo'
import api, { getApiError, parseGamePayload } from '../../plugins/api'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const handleRegister = async (event) => {
    event.preventDefault()
    if (!displayName.trim() || username.trim().length < 3 || password.length < 6) {
      setErrorMsg('Tên hiển thị không được trống, tên đăng nhập tối thiểu 3 ký tự và mật khẩu tối thiểu 6 ký tự.')
      return
    }
    setErrorMsg('')
    setLoading(true)
    try {
      const response = await api.post('/api/v1/auth/register', {
        username: username.trim(),
        password,
        displayName: displayName.trim(),
      })
      if (response.data?.type === 'ERROR') {
        const errPayload = parseGamePayload(response.data)
        setErrorMsg(errPayload.message || errPayload.error || 'Không thể tạo tài khoản.')
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
      setErrorMsg(getApiError(error, 'Không thể tạo tài khoản.'))
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
        <div className="pass-eyebrow">New Player Pass</div>
        <Logo />
        <h1>Tạo tài khoản</h1>
        <p className="muted">Gia nhập bàn cờ Monopoly Party</p>
        <div className="auth-tabs">
          <Link to="/login">Đăng nhập</Link>
          <button type="button" className="active">Đăng ký</button>
        </div>
        {errorMsg && (
          <div className="auth-error" role="alert">
            <AlertCircle size={15} />
            <span>{errorMsg}</span>
          </div>
        )}
        <form onSubmit={handleRegister}>
          <label>
            Tên hiển thị
            <div className="auth-input">
              <CircleUserRound size={16} />
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="VD: Nguyễn Văn A"
                autoFocus
              />
            </div>
          </label>
          <label>
            Tên đăng nhập
            <div className="auth-input">
              <UserRound size={16} />
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Ít nhất 3 ký tự"
                autoComplete="username"
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
                placeholder="Tối thiểu 6 ký tự"
                autoComplete="new-password"
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
            {loading ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'} <ArrowRight size={17} />
          </button>
        </form>
        <p className="auth-switch">
          Đã có thẻ người chơi? <Link to="/login">Đăng nhập</Link>
        </p>
      </section>
    </main>
  )
}
