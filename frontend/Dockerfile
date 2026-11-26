# syntax=docker/dockerfile:1

# ---- build ----
FROM node:22-slim AS build
WORKDIR /app
ENV CI=true \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    npm_config_fund=false \
    npm_config_audit=false
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime ----
FROM nginx:1.27-alpine AS runtime
LABEL org.opencontainers.image.title="finpilot-frontend" \
      org.opencontainers.image.description="FinPilot SPA served by nginx"

RUN rm -f /etc/nginx/conf.d/default.conf \
 && mkdir -p /etc/nginx/snippets
COPY nginx.conf /etc/nginx/nginx.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx

USER nginx
# Validate the config as the runtime user (also creates the /tmp temp dirs with the right owner).
RUN nginx -t -c /etc/nginx/nginx.conf
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
