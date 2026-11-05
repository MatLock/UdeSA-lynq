const ROUTE_PATHS = {
  login: '/',
  register: '/register',
  home: '/home',
  myJobPosts: '/job/mine',
  jobDetail: '/job/:jobId/details',
  editJob: '/job/:jobId/edit',
  jobCandidates: '/job/:jobId/candidates',
  applications: '/user/application',
  userProfile: '/user/:userId',
  companyDetail: '/company/:companyId',
  companyEdit: '/company/:companyId/edit',
  createJob: '/create-job',
  profile: '/profile',
  myResume: '/my-resume',
  analytics: '/analytics',
}

const segments = (path) => path.toLowerCase().split('/').filter(Boolean)

const matchesRoute = (pathname) => {
  const target = segments(pathname)
  return Object.values(ROUTE_PATHS).some((pattern) => {
    const parts = segments(pattern)
    return (
      parts.length === target.length &&
      parts.every((part, index) => part.startsWith(':') || part === target[index])
    )
  })
}

export { ROUTE_PATHS, matchesRoute }
