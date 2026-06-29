import strings from '../../i18n'
import MockPage from '../../components/MockPage/MockPage.jsx'

const MyJobPostsPage = () => {
  const t = strings.pages.jobPosts

  return <MockPage title={t.title} subtitle={t.subtitle} />
}

export default MyJobPostsPage
