import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export default function SessionPage() {
  const { user, loading, refresh } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (loading) return
    if (!user) {
      navigate('/login', { replace: true })
    } else if (user.status !== 'MOBILE_VALIDATED') {
      navigate('/register', { replace: true })
    }
  }, [user, loading, navigate])

  async function handleLogout() {
    await fetch('/logout', { method: 'POST' })
    await refresh()
    navigate('/login')
  }

  if (loading) return <div className="page"><p>Loading...</p></div>
  if (!user) return null

  return (
    <div className="page">
      <h1>Session</h1>
      <dl className="session-info">
        <dt>Name</dt>
        <dd>{user.firstName} {user.lastName}</dd>
        <dt>Email</dt>
        <dd>{user.email}</dd>
        <dt>Status</dt>
        <dd>{user.status}</dd>
      </dl>
      <div className="links">
        <button onClick={handleLogout}>Logout</button>
      </div>
    </div>
  )
}
