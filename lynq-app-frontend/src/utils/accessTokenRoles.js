import { jwtDecode } from 'jwt-decode'

const CANDIDATE = 'R_CANDIDATE'
const COMPANY = 'R_COMPANY'

const rolesOf = (accessToken) => {
  if (!accessToken) return []
  try {
    const { roles } = jwtDecode(accessToken)
    return Array.isArray(roles) ? roles : []
  } catch {
    return []
  }
}

const has = (accessToken, role) => rolesOf(accessToken).includes(role)

export default { CANDIDATE, COMPANY, rolesOf, has }
