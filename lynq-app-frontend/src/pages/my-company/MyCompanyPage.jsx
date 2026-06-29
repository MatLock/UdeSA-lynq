import strings from '../../i18n'
import MockPage from '../../components/MockPage/MockPage.jsx'

const MyCompanyPage = () => {
  const t = strings.pages.company

  return <MockPage title={t.title} subtitle={t.subtitle} />
}

export default MyCompanyPage
