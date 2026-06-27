import { Outlet } from 'react-router-dom'
import strings from '../../../i18n'
import './RegisterLayout.css'


const RegisterLayout = () => {
  const t = strings.register

  return (
    <div className="register-bg">
      <div className="register-dots register-dots-tr" />
      <div className="register-dots register-dots-bl" />

      <main className="register-card">
        <span className="register-logo">LYNQ</span>
        <h1 className="register-title">{t.title}</h1>

        <Outlet />
      </main>
    </div>
  )
}

export default RegisterLayout
