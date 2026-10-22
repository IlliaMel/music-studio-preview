FROM node:24-slim AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS build
COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

FROM node:24-slim AS production-dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

FROM node:24-slim AS runtime
ENV NODE_ENV=production \
    HOST=0.0.0.0
WORKDIR /app
# The code stays owned by root, so the running process cannot rewrite it; it only writes /data.
COPY package.json ./
COPY --from=production-dependencies /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
# dist/core/db/database.js resolves ../../../drizzle relative to its own module URL.
COPY drizzle ./drizzle
# No /data in the image: without a mounted volume the startup write check fails instead of
# keeping paid audio on the container's temporary filesystem.
USER node
EXPOSE 8787
CMD ["node", "dist/main.js"]
