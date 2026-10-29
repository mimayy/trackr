# =============================================================================
# Trackr@Uni-Ace front end
#
# Builds the static bundle and serves it with nginx. Used by docker compose for
# local development; the free production deployment uses Vercel instead, which
# builds from the same package.json.
# =============================================================================

# ---- Build ------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
# `npm ci` when a lockfile is present, `npm install` on a fresh clone.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .

# Vite bakes these into the bundle at build time, so they must be build args
# rather than runtime environment variables. They contain no secrets.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_MAP_TILE_URL
ARG VITE_MAP_ATTRIBUTION
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_MAP_TILE_URL=$VITE_MAP_TILE_URL
ENV VITE_MAP_ATTRIBUTION=$VITE_MAP_ATTRIBUTION

RUN npm run build

# ---- Serve ------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
