FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
COPY scripts ./scripts
RUN npm ci
COPY public ./public
COPY src ./src
COPY config-overrides.js tsconfig.json ./
ENV REACT_APP_API_URL=/api
ENV REACT_APP_WS_URL=/ws
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
