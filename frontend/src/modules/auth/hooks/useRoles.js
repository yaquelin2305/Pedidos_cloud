import useAuth from './useAuth'

export default function useRoles() {
  const { roles } = useAuth()
  return { roles, hasRole: (role) => roles.includes(role), hasAnyRole: (allowed) => allowed.some((role) => roles.includes(role)) }
}
