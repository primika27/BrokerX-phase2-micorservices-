FROM node:20-alpine AS build
WORKDIR /app
COPY brokerx-ui/ ./brokerx-ui/
WORKDIR /app/brokerx-ui
RUN npm ci || npm i
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/brokerx-ui/dist/ /usr/share/nginx/html/
