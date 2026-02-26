import { useState, useEffect, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from './AuthContext'

function isRegistrationComplete(status: string) {
  return status === 'MOBILE_VALIDATED'
}

export default function LoginPage() {
  const { user, loading, refresh } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    if (loading) return
    if (user) {
      if (isRegistrationComplete(user.status)) {
        navigate('/session', { replace: true })
      } else {
        navigate('/register', { replace: true })
      }
    }
  }, [user, loading, navigate])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')

    const res = await fetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })

    const data = await res.json()

    if (res.ok) {
      await refresh()
      if (isRegistrationComplete(data.status)) {
        navigate('/session')
      } else {
        navigate('/register')
      }
    } else {
      setError(data.error || 'Login failed')
    }
  }

  if (loading) return <div className="page"><p>Loading...</p></div>
  if (user) return null

  return (
    <div className="page">
      <h1>Login</h1>
      <form className="form" onSubmit={handleSubmit}>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="error">{error}</div>}
        <button type="submit">Sign In</button>
      </form>
      <div className="links">
        <Link to="/register">Register</Link>
        <Link to="/forgot-password">Forgot Password</Link>
      </div>
    </div>
  )
}
