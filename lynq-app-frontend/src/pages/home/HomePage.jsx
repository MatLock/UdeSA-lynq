import strings from '../../i18n'
import './HomePage.css'

const HomePage = () => {
  const t = strings.home

  return (
    <div className="login-bg">
      <div className="login-dots login-dots-tl" />
      <div className="login-dots login-dots-br" />

      <main className="login-card home-card">
        <h1 className="login-title">{t.title}</h1>
        <p className="login-subtitle">{t.subtitle}</p>
        <p className="home-placeholder">{t.placeholder}</p>
      </main>
    </div>
  )
}

export default HomePage
