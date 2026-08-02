# syntax=docker/dockerfile:1
#
# Multi-stage build for an Angular app in this workspace (shared lib +
# client-portal/admin-portal) -> static files served by Nginx. Mirrors the
# root Dockerfile's "one generic pattern, parameterized by build arg"
# convention for the Java services, but scoped to `frontend/` as its own
# build context — the Angular workspace has no dependency on the Java
# modules, so there's no need to build from the full repo root.
#
# Build:
#   docker build -f frontend/Dockerfile --build-arg PROJECT=client-portal -t client-portal frontend
#   docker build -f frontend/Dockerfile --build-arg PROJECT=admin-portal -t admin-portal frontend

ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

FROM ${NODE_IMAGE} AS build
ARG PROJECT
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npx ng build shared && npx ng build ${PROJECT}

FROM ${NGINX_IMAGE}
ARG PROJECT
COPY --from=build /workspace/dist/${PROJECT}/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
