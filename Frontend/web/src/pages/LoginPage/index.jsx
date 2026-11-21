import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserRound, LockKeyhole, Eye, EyeOff, AlertCircle, ArrowRight } from 'lucide-react'
import Logo from '../../components/Logo'

export default function LoginPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('hoangvu')
  const [password, setPassword] = useState('monopoly')
  const [showPassword, setShowPassword] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const handleLogin = () => {
    if (!name.trim() || !password) {
      setErrorMsg('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.')
      return
    }
    setErrorMsg('')
    navigate('/')
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
        <p className="muted">Sẵn sàng đốt cháy vòng quay may mắn?</p>
        <div className="auth-tabs"><button className="active">Đăng nhập</button><Link to="/register">Đăng ký</Link></div>
        {errorMsg && <div className="auth-error"><AlertCircle size={15}/>{errorMsg}</div>}
        <label>Tên đăng nhập hoặc email<div className="auth-input"><UserRound size={16}/><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Nhập tên đăng nhập" /></div></label>
        <label>Mật khẩu<div className="auth-input"><LockKeyhole size={16}/><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Nhập mật khẩu" /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label="Hiện mật khẩu">{showPassword ? <EyeOff size={16}/> : <Eye size={16}/>}</button></div></label>
        <div className="auth-options"><label className="check"><input type="checkbox" defaultChecked /> Ghi nhớ đăng nhập</label><button className="link">Quên mật khẩu?</button></div>
        <button className="primary wide login-primary" onClick={handleLogin}>Vào chơi ngay <ArrowRight size={17}/></button>
        <div className="auth-divider"><span>hoặc</span></div>
        <button className="google" onClick={() => navigate('/')}><b>G</b> Tiếp tục với Google</button>
        <p className="auth-switch">Chưa có thẻ người chơi? <Link to="/register">Tạo tài khoản</Link></p>
      </section>
    </main>
  )
}
