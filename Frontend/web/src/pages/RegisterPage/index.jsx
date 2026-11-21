import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserRound, CircleUserRound, LockKeyhole, Eye, EyeOff, AlertCircle, ArrowRight } from 'lucide-react'
import Logo from '../../components/Logo'
import api, { getApiError } from '../../plugins/api'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('Hoàng Vũ')
  const [name, setName] = useState('hoangvu')
  const [password, setPassword] = useState('monopoly')
  const [showPassword, setShowPassword] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const handleRegister = async () => {
    if (!displayName.trim() || !name.trim() || password.length < 6) {
      setErrorMsg('Vui lòng nhập tên hiển thị, tên đăng nhập và mật khẩu tối thiểu 6 ký tự.')
      return
    }
    setErrorMsg('')
    try {
      let response
      try {
        response = await api.post('/api/v1/auth/register', { username: name.trim(), password, displayName: displayName.trim() })
      } catch {
        response = await api.post('/api/auth/register', { username: name.trim(), password, displayName: displayName.trim() })
      }
      const payload = response.data?.payload ? JSON.parse(response.data.payload) : response.data
      const token = payload.accessToken || payload.access_token
      if (token) localStorage.setItem('accessToken', token)
      localStorage.setItem('currentUser', JSON.stringify(payload.user || payload))
      navigate('/')
    } catch (error) {
      setErrorMsg(getApiError(error, 'Không thể tạo tài khoản.'))
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
        <div className="auth-tabs"><Link to="/login">Đăng nhập</Link><button className="active">Đăng ký</button></div>
        {errorMsg && <div className="auth-error"><AlertCircle size={15}/>{errorMsg}</div>}
        <label>Tên hiển thị<div className="auth-input"><CircleUserRound size={16}/><input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Tên hiển thị" /></div></label>
        <label>Tên đăng nhập<div className="auth-input"><UserRound size={16}/><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Tên đăng nhập" /></div></label>
        <label>Mật khẩu<div className="auth-input"><LockKeyhole size={16}/><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tối thiểu 6 ký tự" /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label="Hiện mật khẩu">{showPassword ? <EyeOff size={16}/> : <Eye size={16}/>}</button></div></label>
        <button className="primary wide login-primary" onClick={handleRegister}>Tạo tài khoản <ArrowRight size={17}/></button>
        <p className="auth-switch">Đã có thẻ người chơi? <Link to="/login">Đăng nhập</Link></p>
      </section>
    </main>
  )
}
