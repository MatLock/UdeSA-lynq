import strings from '../../i18n'
import MockPage from '../../components/MockPage/MockPage.jsx'

const ProfilePage = () => {
  const t = strings.pages.profile

  return <MockPage title={t.title} subtitle={t.subtitle} />
}

export default ProfilePage
