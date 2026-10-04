# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Deploy (Cloudflare Workers)

The app is served from Cloudflare Workers at https://app.lynqoficial.com. `wrangler.json` defines the worker: static assets from `./dist`, the single-page-application fallback so client-side routes resolve, and `app.lynqoficial.com` as its custom domain.

```bash
npx wrangler login   # once
npm run deploy
```

`npm run deploy` builds with `--mode cloudflare`, which loads `.env.cloudflare` and bakes `LYNQ_BFF_BASE_URL=https://api.lynqoficial.com/lynq-bff` into the bundle, then runs `wrangler deploy`. The plain `npm run build` used by the Docker image and CI is unaffected. The first deploy creates the DNS record and the certificate for `app.lynqoficial.com` in the Cloudflare zone, so that hostname must not already have a record.
