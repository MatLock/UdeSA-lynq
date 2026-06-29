import strings from '../../i18n'
import MockPage from '../../components/MockPage/MockPage.jsx'

const ApplicationsPage = () => {
  const t = strings.pages.applications

  return <MockPage title={t.title} subtitle={t.subtitle} />
}

export default ApplicationsPage
