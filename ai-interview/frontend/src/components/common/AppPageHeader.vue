<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    backTo?: string
    showBack?: boolean
  }>(),
  {
    showBack: false,
  },
)

const router = useRouter()

function handleBack(): void {
  if (props.backTo) {
    void router.push(props.backTo)
    return
  }
  router.back()
}
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <el-button v-if="showBack" text class="page-header__back" @click="handleBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <div>
        <h1 class="page-header__title">{{ title }}</h1>
        <p v-if="description" class="page-header__description">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.page-header__main {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.page-header__back {
  flex: none;
  margin-top: 2px;
}

.page-header__title {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text);
  word-break: break-word;
}

.page-header__description {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.page-header__actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }

  .page-header__title {
    font-size: var(--font-size-xl);
  }
}
</style>
