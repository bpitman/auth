import { useState, useEffect, type FormEvent, type ChangeEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from './AuthContext'

function validatePassword(password: string): string[] {
  const errors: string[] = []
  if (password.length < 10) errors.push('must be at least 10 characters')
  if (!/[A-Z]/.test(password)) errors.push('must contain at least one uppercase letter')
  if (!/[a-z]/.test(password)) errors.push('must contain at least one lowercase letter')
  if (!/[0-9]/.test(password)) errors.push('must contain at least one digit')
  if (!/[^A-Za-z0-9]/.test(password)) errors.push('must contain at least one special character')
  return errors
}

function isAtLeast16(dateStr: string): boolean {
  const birth = new Date(dateStr + 'T00:00:00')
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--
  return age >= 16
}

function formatPhone(digits: string): string {
  if (digits.length <= 3) return `(${digits}`
  if (digits.length <= 6) return `(${digits.slice(0, 3)}) ${digits.slice(3)}`
  return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6, 10)}`
}

function parsePhoneDigits(value: string): string {
  return value.replace(/\D/g, '').slice(0, 10)
}

function toE164(digits: string): string {
  return `+1${digits}`
}

export default function RegisterPage() {
  const { user, loading, refresh } = useAuth()
  const navigate = useNavigate()

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<string[]>([])
  const [phoneDigits, setPhoneDigits] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [isUpdate, setIsUpdate] = useState(false)
  const [showForm, setShowForm] = useState(true)
  const [codeSent, setCodeSent] = useState(false)

  const status = user?.status ?? null

  useEffect(() => {
    if (user && status === 'REGISTERED') {
      setFirstName(user.firstName)
      setLastName(user.lastName)
      setBirthDate(user.birthDate)
      setEmail(user.email)
    }
  }, [user, status])

  useEffect(() => {
    if (!loading && user && status === 'AUTHENTICATED') {
      navigate('/session', { replace: true })
    }
  }, [loading, user, status, navigate])

  // If we land on MOBILE_PENDING, go straight to code entry
  useEffect(() => {
    if (user && status === 'MOBILE_PENDING') {
      setCodeSent(true)
    }
  }, [user, status])

  if (loading) return <div className="page"><p>Loading...</p></div>

  // Step 1: Registration form (no user or REGISTERED status)
  if (!user || status === 'REGISTERED') {
    if (user && status === 'REGISTERED' && !showForm) {
      return (
        <div className="page">
          <h1>Check Your Email</h1>
          <p>We sent a verification email to <strong>{user.email}</strong>. Please check your inbox and click the link to verify your account.</p>
          <div className="links">
            <button onClick={() => { setIsUpdate(true); setShowForm(true) }}>Back to Update Info</button>
            <button onClick={handleLogout}>Logout</button>
          </div>
        </div>
      )
    }

    return (
      <div className="page">
        <h1>{isUpdate ? 'Update Info' : 'Register'}</h1>
        <form className="form" onSubmit={isUpdate ? handleUpdate : handleRegister}>
          <input
            type="text"
            placeholder="First Name"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            required
          />
          <input
            type="text"
            placeholder="Last Name"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            required
          />
          <input
            type="date"
            placeholder="Birth Date"
            value={birthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            required
          />
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
          <input
            type="password"
            placeholder="Confirm Password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
          {errors.length > 0 && (
            <div className="error">
              <ul>
                {errors.map((err, i) => <li key={i}>{err}</li>)}
              </ul>
            </div>
          )}
          <button type="submit">{isUpdate ? 'Update' : 'Register'}</button>
        </form>
        <div className="links">
          {!user && <Link to="/login">Already have an account? Sign in</Link>}
          {user && <button onClick={() => { setShowForm(false); setIsUpdate(false) }}>Cancel</button>}
        </div>
      </div>
    )
  }

  // Step 2: Email validated → add mobile number or verify code
  if (status === 'EMAIL_VALIDATED' || status === 'MOBILE_PENDING') {
    if (codeSent) {
      return (
        <div className="page">
          <h1>Verify Mobile</h1>
          <p>Enter the 6-digit code sent to your phone.</p>
          <form className="form" onSubmit={handleValidateMobile}>
            <input
              type="text"
              placeholder="Verification Code"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value)}
              maxLength={6}
              pattern="[0-9]{6}"
              required
            />
            {errors.length > 0 && (
              <div className="error">
                <ul>
                  {errors.map((err, i) => <li key={i}>{err}</li>)}
                </ul>
              </div>
            )}
            <button type="submit">Verify</button>
          </form>
          <div className="links">
            <button onClick={handleResendCode}>Resend Code</button>
            <button onClick={handleBackToPhone}>Change Phone Number</button>
            <button onClick={handleLogout}>Logout</button>
          </div>
        </div>
      )
    }

    return (
      <div className="page">
        <h1>Add Mobile Number</h1>
        <p>Your email is verified. Now add your mobile number for two-factor authentication.</p>
        <form className="form" onSubmit={handleAddMobile}>
          <div className="phone-input">
            <span className="phone-prefix">+1</span>
            <input
              type="tel"
              placeholder="(555) 123-4567"
              value={phoneDigits ? formatPhone(phoneDigits) : ''}
              onChange={handlePhoneChange}
              required
            />
          </div>
          {errors.length > 0 && (
            <div className="error">
              <ul>
                {errors.map((err, i) => <li key={i}>{err}</li>)}
              </ul>
            </div>
          )}
          <button type="submit">Send Verification Code</button>
        </form>
        <div className="links">
          <button onClick={handleLogout}>Logout</button>
        </div>
      </div>
    )
  }

  return null

  // --- Handlers ---

  function handlePhoneChange(e: ChangeEvent<HTMLInputElement>) {
    const digits = parsePhoneDigits(e.target.value)
    setPhoneDigits(digits)
  }

  async function handleRegister(e: FormEvent) {
    e.preventDefault()
    setErrors([])

    if (!isAtLeast16(birthDate)) {
      setErrors(['must be at least 16 years old'])
      return
    }
    if (password !== confirmPassword) {
      setErrors(['passwords do not match'])
      return
    }
    const passwordErrors = validatePassword(password)
    if (passwordErrors.length > 0) {
      setErrors(passwordErrors)
      return
    }

    const res = await fetch('/register', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ firstName, lastName, email, password, birthDate }),
    })

    const data = await res.json()
    if (res.ok) {
      await refresh()
      setShowForm(false)
    } else if (data.errors) {
      setErrors(data.errors)
    } else {
      setErrors([data.error || 'Registration failed'])
    }
  }

  async function handleUpdate(e: FormEvent) {
    e.preventDefault()
    setErrors([])

    if (!isAtLeast16(birthDate)) {
      setErrors(['must be at least 16 years old'])
      return
    }
    if (password !== confirmPassword) {
      setErrors(['passwords do not match'])
      return
    }
    const passwordErrors = validatePassword(password)
    if (passwordErrors.length > 0) {
      setErrors(passwordErrors)
      return
    }

    const res = await fetch('/update-profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: user!.userId, firstName, lastName, email, password, birthDate }),
    })

    const data = await res.json()
    if (res.ok) {
      await refresh()
      setShowForm(false)
      setIsUpdate(false)
    } else if (data.errors) {
      setErrors(data.errors)
    } else {
      setErrors([data.error || 'Update failed'])
    }
  }

  async function handleAddMobile(e: FormEvent) {
    e.preventDefault()
    setErrors([])

    if (phoneDigits.length !== 10) {
      setErrors(['please enter a complete 10-digit phone number'])
      return
    }

    const res = await fetch('/add-mobile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: user!.userId, phoneNumber: toE164(phoneDigits) }),
    })

    const data = await res.json()
    if (res.ok) {
      await refresh()
      setCodeSent(true)
      setOtpCode('')
      setErrors([])
    } else {
      setErrors([data.error || 'Failed to add mobile'])
    }
  }

  async function handleResendCode() {
    setErrors([])
    const res = await fetch('/add-mobile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: user!.userId, phoneNumber: toE164(phoneDigits) }),
    })

    const data = await res.json()
    if (res.ok) {
      setOtpCode('')
      setErrors([])
    } else {
      setErrors([data.error || 'Failed to resend code'])
    }
  }

  function handleBackToPhone() {
    setCodeSent(false)
    setOtpCode('')
    setErrors([])
  }

  async function handleValidateMobile(e: FormEvent) {
    e.preventDefault()
    setErrors([])

    const res = await fetch('/validate-mobile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: user!.userId, code: otpCode }),
    })

    const data = await res.json()
    if (res.ok) {
      await refresh()
      navigate('/session')
    } else {
      setErrors([data.error || 'Invalid code'])
    }
  }

  async function handleLogout() {
    await fetch('/logout', { method: 'POST' })
    await refresh()
    navigate('/login')
  }
}
