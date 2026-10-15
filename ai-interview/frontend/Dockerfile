# 前端构建服务（计划 §10.1：构建期一次性容器，产物交 frontend-dist 卷给 Nginx）。
# 当前为占位静态页（真实 Vue3 前端待前后端联调阶段接入，计划 §8.3 边界说明）——
# 无需 Node 构建链，alpine 直接拷贝产物；正式前端落地后本文件替换为
# node:20 多阶段构建（npm ci && npm run build → /usr/share/nginx/html）。
FROM alpine:3.20
COPY index.html /usr/share/nginx/html/index.html
CMD ["true"]
