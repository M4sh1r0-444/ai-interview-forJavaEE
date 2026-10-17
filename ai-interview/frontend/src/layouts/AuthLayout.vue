<script setup lang="ts">
import { ChatDotRound, DataAnalysis, TrendCharts } from '@element-plus/icons-vue'

import GlassPanel from '@/components/common/GlassPanel.vue'

const features = [
  {
    icon: ChatDotRound,
    title: '真实多轮模拟',
    description: '文字 / 语音双模式，AI 动态追问，还原真实面试节奏。',
  },
  {
    icon: DataAnalysis,
    title: '可解释的评分',
    description: '六维评分 + 逐题证据 + 亮点不足，分数有据可查。',
  },
  {
    icon: TrendCharts,
    title: '持续成长路径',
    description: '能力曲线、错题重练与个性化资源，下一场更有准备。',
  },
]
</script>

<template>
  <div class="auth-layout">
    <section class="auth-brand" aria-label="平台介绍">
      <div class="auth-brand__grid" aria-hidden="true" />
      <div class="auth-brand__content">
        <div class="auth-brand__mark" aria-hidden="true">
          <el-icon :size="26"><ChatDotRound /></el-icon>
        </div>
        <p class="auth-brand__eyebrow">Java EE 课程设计</p>
        <h1 class="auth-brand__title">
          AI 模拟面试<br />
          与能力提升平台
        </h1>
        <p class="auth-brand__subtitle">
          大模型驱动的双岗位模拟面试，从一次作答到一条可追踪的成长曲线。
        </p>

        <ul class="auth-brand__features">
          <li v-for="feature in features" :key="feature.title" class="auth-brand__feature">
            <span class="auth-brand__feature-icon">
              <el-icon :size="18"><component :is="feature.icon" /></el-icon>
            </span>
            <div>
              <strong>{{ feature.title }}</strong>
              <p>{{ feature.description }}</p>
            </div>
          </li>
        </ul>
      </div>
      <footer class="auth-brand__footer">大模型驱动 · 双岗位 · 可解释评分 · 个性化推荐</footer>
    </section>

    <section class="auth-panel">
      <GlassPanel tone="strong" :padded="false" class="auth-panel__card">
        <router-view />
      </GlassPanel>
      <p class="auth-panel__copyright">© 2026 AI 模拟面试与能力提升平台 · Java EE 课程设计</p>
    </section>
  </div>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(440px, 0.95fr);
  min-height: 100vh;
  background: transparent;
}

/* 左侧品牌区：低饱和蓝渐变 + 细网格，无外部插画（计划 §8.3） */
.auth-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: var(--space-7) clamp(32px, 6vw, 88px);
  border-right: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 0 32px 32px 0;
  background:
    radial-gradient(circle at 16% 18%, rgba(147, 197, 253, 0.32), transparent 44%),
    radial-gradient(circle at 86% 82%, rgba(34, 211, 238, 0.22), transparent 48%),
    linear-gradient(
      148deg,
      rgba(13, 33, 84, 0.94) 0%,
      rgba(30, 64, 175, 0.82) 48%,
      rgba(37, 99, 235, 0.6) 100%
    );
  box-shadow:
    inset -1px 0 0 rgba(255, 255, 255, 0.14),
    0 24px 80px rgba(15, 23, 42, 0.2);
  color: #fff;
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

/* 顶部的镜面高光，与半透明蓝色材质共同形成液态玻璃层次 */
.auth-brand::before {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.2) 0%, transparent 16%),
    radial-gradient(circle at 10% 90%, rgba(255, 255, 255, 0.1), transparent 40%);
  content: '';
  pointer-events: none;
}

.auth-brand::after {
  position: absolute;
  top: 22%;
  right: -18%;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14), transparent 66%);
  content: '';
  pointer-events: none;
}

.auth-brand__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at 50% 40%, #000 30%, transparent 85%);
  pointer-events: none;
}

.auth-brand__content {
  position: relative;
  max-width: 560px;
  margin: auto 0;
  padding: var(--space-7) 0;
}

.auth-brand__mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: var(--space-5);
  border: 1px solid rgba(255, 255, 255, 0.42);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.24), rgba(255, 255, 255, 0.08));
  box-shadow:
    0 12px 30px rgba(2, 6, 23, 0.2),
    0 1px 0 rgba(255, 255, 255, 0.35) inset;
  backdrop-filter: blur(14px) saturate(150%);
  -webkit-backdrop-filter: blur(14px) saturate(150%);
}

.auth-brand__eyebrow {
  margin-bottom: var(--space-3);
  color: rgba(219, 234, 254, 0.86);
  font-size: var(--font-size-sm);
  letter-spacing: 0.16em;
}

.auth-brand__title {
  font-size: clamp(30px, 3.4vw, 46px);
  font-weight: 700;
  line-height: 1.24;
  letter-spacing: 0.02em;
}

.auth-brand__subtitle {
  max-width: 460px;
  margin-top: var(--space-4);
  color: rgba(226, 239, 255, 0.9);
  font-size: var(--font-size-md);
  line-height: 1.7;
}

.auth-brand__features {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: var(--space-7) 0 0;
  padding: 0;
  list-style: none;
}

.auth-brand__feature {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.05));
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.2) inset;
  backdrop-filter: blur(14px) saturate(150%);
  -webkit-backdrop-filter: blur(14px) saturate(150%);
}

.auth-brand__feature-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.24) inset;
}

.auth-brand__feature strong {
  display: block;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.auth-brand__feature p {
  margin-top: 2px;
  color: rgba(219, 234, 254, 0.78);
  font-size: var(--font-size-sm);
}

.auth-brand__footer {
  position: relative;
  color: rgba(219, 234, 254, 0.62);
  font-size: var(--font-size-xs);
  letter-spacing: 0.06em;
}

/* 右侧表单区 */
.auth-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  padding: var(--space-6) var(--space-5);
  min-width: 0;
}

.auth-panel__card {
  width: 100%;
  max-width: 460px;
  padding: var(--space-6);
}

.auth-panel__copyright {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  text-align: center;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.6);
}

@media (max-width: 960px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    padding: var(--space-5) var(--space-5);
    border-right: none;
    border-radius: 0 0 28px 28px;
  }

  .auth-brand__content {
    padding: var(--space-4) 0 0;
  }

  .auth-brand__title {
    font-size: clamp(26px, 6vw, 34px);
  }

  .auth-brand__subtitle {
    font-size: var(--font-size-base);
  }

  .auth-brand__features,
  .auth-brand__footer {
    display: none;
  }

  .auth-panel {
    padding: var(--space-5) var(--space-4) var(--space-6);
    justify-content: flex-start;
  }
}

@media (max-width: 480px) {
  .auth-panel__card {
    padding: var(--space-5) var(--space-4);
  }
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .auth-brand {
    background: linear-gradient(
      148deg,
      rgba(13, 33, 84, 0.97) 0%,
      rgba(30, 64, 175, 0.92) 48%,
      rgba(37, 99, 235, 0.82) 100%
    );
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .auth-brand__mark,
  .auth-brand__feature {
    background: rgba(255, 255, 255, 0.12);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .auth-brand {
    background: linear-gradient(148deg, #0d2154 0%, #1e40af 55%, #2563eb 100%);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .auth-brand__mark,
  .auth-brand__feature {
    background: rgba(255, 255, 255, 0.12);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
