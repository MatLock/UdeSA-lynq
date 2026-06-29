import strings from '../../i18n'
import MockPage from '../../components/MockPage/MockPage.jsx'
import './HomePage.css'

const HomePage = () => {
  const t = strings.home

  return (
    <MockPage title={t.title} subtitle={t.subtitle}>
      <p className="home-placeholder">{t.placeholder}</p>
    </MockPage>
  )
}

export default HomePage
