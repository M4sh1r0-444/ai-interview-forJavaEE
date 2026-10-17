# 前端产物暂存镜像（FIX-02 方案 A）：COPY 到 /opt/frontend，由 compose 的
# 初始化容器在启动时 rm -rf /site/* && cp -a /opt/frontend/. /site/ 强制覆盖
# frontend-dist 卷——无论卷是全新空卷还是已被 Nginx 默认页初始化，均能修复。
# 正式 Vue3 前端落地后：多阶段构建（npm ci && npm run build）把 dist/ 放到 /opt/frontend/。
FROM alpine:3.20
COPY index.html /opt/frontend/index.html
