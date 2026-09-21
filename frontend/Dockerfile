FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite bakes these into the JS bundle at build time - they are correct for the AWS deployment
# specifically (paths relative to Caddy's own host, not localhost). Local dev keeps its own
# absolute-URL .env, untouched by this file.
ARG VITE_API_BASE_URL=/api
ARG VITE_KEYCLOAK_URL=/keycloak
ARG VITE_KEYCLOAK_REALM=credisynch
ARG VITE_KEYCLOAK_CLIENT_ID=credisynch-web
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8082
