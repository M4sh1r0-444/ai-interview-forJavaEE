# F6-4：前端多阶段构建（node:20 构建 dist → 初始化容器写入 frontend-dist 卷）
# 链路：frontend 容器退出前把 /opt/frontend-dist 覆盖到挂载卷
#       → nginx 等 frontend 成功退出 → 挂载同一卷返回真实 Vue 页面（修复 P9）。
FROM node:20-alpine AS build

WORKDIR /app
# 依赖层单独缓存：package*.json 不变时复用 npm ci 层
# 网络受限时可用 --build-arg NPM_REGISTRY=https://registry.npmmirror.com 覆盖源
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package*.json ./
RUN npm ci --no-audit --no-fund --registry="$NPM_REGISTRY" --replace-registry-host=always

COPY . .
RUN npm run build

# 产物初始化层：不依赖 Node 运行时，体积小；CMD 写入 compose 挂载的卷
FROM alpine:3.20
COPY --from=build /app/dist /opt/frontend-dist
# 统一可读权限：宿主机 public/ 下文件可能是 0600，Nginx worker（非 root）需可读
RUN chmod -R a+rX /opt/frontend-dist
CMD ["sh", "-c", "rm -rf /usr/share/nginx/html/* /usr/share/nginx/html/.[!.]* 2>/dev/null; cp -a /opt/frontend-dist/. /usr/share/nginx/html/"]
