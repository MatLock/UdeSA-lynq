import './MockPage.css'

// Lightweight placeholder page used by the not-yet-built sections. Reuses the
// auth card design (.login-bg / .login-card / .login-title from index.css) and
// flows inside the AppLayout shell next to the sidebar. Behaviour for each
// section is defined later. Optional children render extra content under the
// subtitle.
const MockPage = ({ title, subtitle, children }) => (
  <div className="login-bg mock-page">
    <div className="login-dots login-dots-tl" />
    <div className="login-dots login-dots-br" />

    <main className="login-card mock-card">
      <h1 className="login-title">{title}</h1>
      <p className="login-subtitle">{subtitle}</p>
      {children}
    </main>
  </div>
)

export default MockPage
