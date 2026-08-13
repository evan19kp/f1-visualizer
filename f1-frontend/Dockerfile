# syntax=docker/dockerfile:1

FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite bakes these into the bundle at build time.
ARG VITE_SESSION_KEY=9161
ARG VITE_API_URL=
ARG VITE_WS_URL=/ws
ENV VITE_SESSION_KEY=${VITE_SESSION_KEY} \
    VITE_API_URL=${VITE_API_URL} \
    VITE_WS_URL=${VITE_WS_URL}

RUN npm run build

FROM nginx:1.27-alpine

# Official image substitutes ${API_BACKEND} from /etc/nginx/templates/*.template at startup.
ENV API_BACKEND=http://localhost:8080

COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
