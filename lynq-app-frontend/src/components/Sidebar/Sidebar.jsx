import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import strings, { activeLocale, locales, setLocale } from '../../i18n'
import useAuth from '../../hooks/useAuth'
import UserIcon from '../UserIcon/UserIcon'
import './Sidebar.css'


const LogoutIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path
      d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    />
    <path
      d="M10 8l-4 4 4 4M6 12h11"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

// Collapsible left navigation bar. The menu adapts to the user type (candidate
// vs company); each entry navigates to its section and the active route is
// highlighted. Owns its collapsed/expanded state.
const Sidebar = () => {
  const t = strings.sidebar
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(false)


  const profileImageUrl = user?.profileImageUrl ?? null
  const isCompany = user?.userType === 'COMPANY'
  const items = [
    { key: 'home', icon: '🏠', label: t.home, to: '/home' },
    { key: 'profile', icon: '👤', label: t.profile, to: '/profile' },
    ...(isCompany
      ? [
          { key: 'company', icon: '🏢', label: t.company, to: '/my-company' },
          { key: 'jobPosts', icon: '📢', label: t.jobPosts, to: '/job/mine' },
        ]
      : [
          { key: 'resume', icon: '📄', label: t.resume, to: '/my-resume' },
          { key: 'applications', icon: '📨', label: t.applications, to: '/applications' },
        ]),
  ]

  const localeCodes = Object.keys(locales)
  const nextLocale =
    localeCodes[(localeCodes.indexOf(activeLocale) + 1) % localeCodes.length]

  return (
    <aside className={`sidebar${collapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="sidebar-header">
        <span className="sidebar-avatar">
          {profileImageUrl ? (
            <img src={profileImageUrl} alt={t.avatarAlt} />
          ) : (
            <UserIcon />
          )}
        </span>
        {!collapsed && (
          <span className="sidebar-username">
            {user?.fullName ?? user?.username ?? t.fullName}
          </span>
        )}
        <button
          type="button"
          className="sidebar-toggle"
          onClick={() => setCollapsed((c) => !c)}
          aria-label={collapsed ? t.expand : t.collapse}
          aria-expanded={!collapsed}
        >
          {collapsed ? '»' : '«'}
        </button>
      </header>

      <nav className="sidebar-nav">
        {items.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`sidebar-item${pathname === item.to ? ' sidebar-item-active' : ''}`}
            aria-current={pathname === item.to ? 'page' : undefined}
            onClick={() => navigate(item.to)}
          >
            <span className="sidebar-item-icon" aria-hidden="true">
              {item.icon}
            </span>
            {!collapsed && (
              <span className="sidebar-item-label">{item.label}</span>
            )}
          </button>
        ))}
      </nav>

      <div className="sidebar-footer">
        <button
          type="button"
          className="sidebar-item sidebar-lang"
          onClick={() => setLocale(nextLocale)}
          aria-label={t.language}
          title={t.language}
        >
          <span className="sidebar-item-icon" aria-hidden="true">
            🌐
          </span>
          {!collapsed && (
            <span className="sidebar-item-label">{activeLocale.toUpperCase()}</span>
          )}
        </button>

        <button
          type="button"
          className="sidebar-item sidebar-logout"
          onClick={logout}
          aria-label={t.logout}
        >
          <span className="sidebar-item-icon">
            <LogoutIcon />
          </span>
          {!collapsed && <span className="sidebar-item-label">{t.logout}</span>}
        </button>
      </div>
    </aside>
  )
}

export default Sidebar
