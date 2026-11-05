import { matchesRoute } from '../src/routePaths.js'

export default {
  async fetch(request, env) {
    const indexUrl = new URL('/', request.url)
    const { pathname } = new URL(request.url)

    if (matchesRoute(pathname)) {
      return env.ASSETS.fetch(new Request(indexUrl, request))
    }

    const index = await env.ASSETS.fetch(indexUrl)
    const headers = new Headers(index.headers)
    headers.set('Cache-Control', 'no-store')
    return new Response(index.body, { status: 404, headers })
  },
}
