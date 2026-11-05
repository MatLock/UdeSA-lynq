import { useNavigate } from 'react-router-dom'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import LynqTitle from '../../components/LynqTitle/LynqTitle'
import { ROUTE_PATHS } from '../../routePaths'
import strings from '../../i18n'
import './NotFoundPage.css'

const NotFoundPage = () => {
  const navigate = useNavigate()
  const t = strings.notFound

  return (
    <div className="not-found-bg">
      <main className="not-found-card">
        <span className="not-found-code">404</span>
        <LynqTitle as="h1" className="not-found-title" text={t.title} />
        <p className="not-found-message">{t.message}</p>
        <button
          type="button"
          className="not-found-action"
          onClick={() => navigate(ROUTE_PATHS.login, { replace: true })}
        >
          <ArrowBackRoundedIcon sx={{ fontSize: 16 }} />
          {t.backHome}
        </button>
      </main>
    </div>
  )
}

export default NotFoundPage
