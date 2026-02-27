import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'

export interface UserInfo {
  userId: string
  email: string
  status: string
  firstName: string
  lastName: string
  birthDate: string
}

interface AuthContextType {
  user: UserInfo | null
  loading: boolean
  setUser: (user: UserInfo | null) => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  loading: true,
  setUser: () => {},
  refresh: async () => {},
})

export function useAuth() {
  return useContext(AuthContext)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    try {
      const res = await fetch('/me')
      if (res.ok) {
        setUser(await res.json())
      } else {
        setUser(null)
      }
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  return (
    <AuthContext.Provider value={{ user, loading, setUser, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}
