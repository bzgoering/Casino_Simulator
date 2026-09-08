# Build the static bundle, then serve it from nginx. No Node in the runtime image.

FROM node:22-alpine AS build
WORKDIR /build

# The lockfile is copied with the manifest so the build is reproducible: `npm ci` installs the
# exact resolved tree the lockfile pins and fails if the two disagree, where `npm install` is
# free to pick up newer transitive versions on any given build.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------

FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 127.0.0.1, not localhost: the image resolves localhost to ::1 first, and `listen 80` binds
# IPv4 only, so the IPv6 attempt is refused and the container reports unhealthy while it is
# serving every real request perfectly well.
HEALTHCHECK --interval=15s --timeout=4s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
