# BarberFlow — web (Next.js, App Router)
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --no-fund --no-audit
COPY . .
# NEXT_PUBLIC_* e inlined no BUILD (runtime nao altera o bundle do
# browser): a URL da API entra via build-arg (compose passa o host
# mapeado, ex. 8081 quando a 8080 esta ocupada no host)
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_DEFAULT_SLUG
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_DEFAULT_SLUG=${NEXT_PUBLIC_DEFAULT_SLUG}
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next ./.next
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
EXPOSE 3000
CMD ["npm", "run", "start"]
