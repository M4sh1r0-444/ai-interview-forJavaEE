<script setup lang="ts">
/**
 * 液态玻璃容器：F1 起所有卡片/面板的统一材质。
 * 只负责材质与圆角，内容完全由 slot 决定；不引入第三方依赖。
 * 回退：不支持 backdrop-filter 或用户要求减少透明时，降级为接近实色的白底。
 */
withDefaults(
  defineProps<{
    tone?: 'default' | 'strong' | 'subtle' | 'brand'
    hoverable?: boolean
    padded?: boolean
  }>(),
  {
    tone: 'default',
    hoverable: false,
    padded: true,
  },
)
</script>

<template>
  <div
    class="glass-panel"
    :class="[
      `glass-panel--${tone}`,
      { 'glass-panel--hoverable': hoverable, 'glass-panel--padded': padded },
    ]"
  >
    <slot />
  </div>
</template>

<style scoped>
.glass-panel {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: var(--glass-radius);
  background: var(--glass-panel-bg, var(--glass-bg));
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  transition:
    transform var(--transition-base),
    box-shadow var(--transition-base),
    background var(--transition-base);
}

/* 斜向高光：位于内容之下、面板背景之上 */
.glass-panel::before {
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background: var(--glass-sheen);
  content: '';
  pointer-events: none;
}

.glass-panel--padded {
  padding: var(--space-5);
}

.glass-panel--default {
  --glass-panel-bg: var(--glass-bg);
}

.glass-panel--strong {
  --glass-panel-bg: var(--glass-bg-strong);
  border-color: var(--glass-border-strong);
  box-shadow: var(--glass-shadow-lg);
  backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-strong)) saturate(var(--glass-saturate));
}

.glass-panel--subtle {
  --glass-panel-bg: var(--glass-bg-subtle);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur-subtle)) saturate(var(--glass-saturate));
}

.glass-panel--brand {
  --glass-panel-bg: var(--glass-bg-brand);
  border-color: var(--glass-border-brand);
  box-shadow: var(--glass-shadow-brand);
  color: #fff;
}

.glass-panel--hoverable:hover {
  transform: translateY(-2px);
  box-shadow: var(--glass-shadow-lg);
}

@media (max-width: 768px) {
  .glass-panel--padded {
    padding: var(--space-4);
  }
}

/* 不支持背景模糊时：使用接近实色的半透明白，保证可读性 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass-panel,
  .glass-panel--strong,
  .glass-panel--subtle {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .glass-panel,
  .glass-panel--strong,
  .glass-panel--subtle {
    background: var(--glass-bg-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .glass-panel::before {
    opacity: 0.35;
  }
}
</style>
