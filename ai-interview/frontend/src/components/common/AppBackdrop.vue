<script setup lang="ts">
/**
 * 全屏液态玻璃背景：柔和渐变 + 缓慢漂移的光斑 + 细网格 + 颗粒。
 * 仅装饰，不接收指针事件；放在 App 根部、所有内容之下。
 * 性能：光斑使用 transform 动画 + blur，数量固定为 4，移动端缩小；
 *       prefers-reduced-motion 下停止动画。
 */
</script>

<template>
  <div class="app-backdrop" aria-hidden="true">
    <span class="app-backdrop__base" />
    <span class="app-backdrop__orb app-backdrop__orb--blue" />
    <span class="app-backdrop__orb app-backdrop__orb--violet" />
    <span class="app-backdrop__orb app-backdrop__orb--cyan" />
    <span class="app-backdrop__orb app-backdrop__orb--amber" />
    <span class="app-backdrop__grid" />
    <span class="app-backdrop__grain" />
  </div>
</template>

<style scoped>
.app-backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: var(--color-bg);
}

.app-backdrop__base {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(1100px 760px at 8% 4%, rgba(191, 219, 254, 0.9), transparent 62%),
    radial-gradient(900px 680px at 92% 8%, rgba(221, 214, 254, 0.82), transparent 58%),
    radial-gradient(1000px 860px at 50% 108%, rgba(204, 251, 241, 0.78), transparent 60%),
    linear-gradient(135deg, #eef4ff 0%, #f8fafc 46%, #eef2ff 100%);
}

.app-backdrop__orb {
  position: absolute;
  width: 46vw;
  height: 46vw;
  min-width: 320px;
  min-height: 320px;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.52;
  transform: translate3d(0, 0, 0);
  animation: orb-drift 26s ease-in-out infinite alternate;
  will-change: transform;
}

.app-backdrop__orb--blue {
  top: -12%;
  left: -8%;
  background: radial-gradient(
    circle at 35% 35%,
    rgba(96, 165, 250, 0.95),
    rgba(37, 99, 235, 0.22) 62%,
    transparent 72%
  );
}

.app-backdrop__orb--violet {
  top: 4%;
  right: -12%;
  width: 40vw;
  height: 40vw;
  background: radial-gradient(
    circle at 45% 45%,
    rgba(196, 181, 253, 0.95),
    rgba(139, 92, 246, 0.2) 60%,
    transparent 72%
  );
  animation-delay: -8s;
  animation-duration: 30s;
}

.app-backdrop__orb--cyan {
  bottom: -18%;
  left: 22%;
  width: 42vw;
  height: 42vw;
  background: radial-gradient(
    circle at 50% 50%,
    rgba(103, 232, 249, 0.8),
    rgba(14, 165, 233, 0.18) 60%,
    transparent 72%
  );
  animation-delay: -14s;
  animation-duration: 34s;
}

.app-backdrop__orb--amber {
  right: 12%;
  bottom: -8%;
  width: 26vw;
  height: 26vw;
  background: radial-gradient(
    circle at 50% 50%,
    rgba(253, 230, 138, 0.5),
    rgba(245, 158, 11, 0.08) 60%,
    transparent 72%
  );
  opacity: 0.36;
  animation-delay: -20s;
  animation-duration: 38s;
}

.app-backdrop__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at 50% 42%, #000 18%, transparent 82%);
  opacity: 0.55;
}

.app-backdrop__grain {
  position: absolute;
  inset: 0;
  opacity: 0.045;
  mix-blend-mode: soft-light;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E");
}

@keyframes orb-drift {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(3vw, 2.5vh, 0) scale(1.06);
  }
  100% {
    transform: translate3d(-2vw, 4vh, 0) scale(0.98);
  }
}

@media (max-width: 768px) {
  .app-backdrop__orb {
    width: 70vw;
    height: 70vw;
    filter: blur(56px);
  }

  .app-backdrop__orb--violet {
    width: 64vw;
    height: 64vw;
  }

  .app-backdrop__orb--cyan {
    width: 68vw;
    height: 68vw;
  }

  .app-backdrop__orb--amber {
    width: 44vw;
    height: 44vw;
  }

  .app-backdrop__grid {
    background-size: 36px 36px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-backdrop__orb {
    animation: none;
  }
}
</style>
