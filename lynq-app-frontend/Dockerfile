# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app

# Vite bakes env vars at build time, so they must be present during `vite build`.
ARG LYNQ_IAM_BASE_URL
ARG LYNQ_BFF_BASE_URL
ENV LYNQ_IAM_BASE_URL=${LYNQ_IAM_BASE_URL}
ENV LYNQ_BFF_BASE_URL=${LYNQ_BFF_BASE_URL}

COPY package*.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build

# --- Serve stage ---
# The unprivileged variant runs as user nginx (101) instead of root, so it
# serves on 8080 (non-root processes can't bind ports below 1024).
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
