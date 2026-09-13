FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html vite.config.js ./
COPY src ./src
COPY public ./public

ARG VITE_API_BASE_URL=/
ARG VITE_API_SCOPE
ARG VITE_AZURE_CLIENT_ID
ARG VITE_AZURE_AUTHORITY
ARG VITE_AZURE_KNOWN_AUTHORITY
ARG VITE_AZURE_REDIRECT_URI
ARG VITE_AZURE_POST_LOGOUT_REDIRECT_URI

RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
