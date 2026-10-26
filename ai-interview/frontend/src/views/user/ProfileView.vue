<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Refresh, User, ArrowRight, Cpu } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { errorMessage, isBizError } from '@/api/http'
import { updateMe } from '@/api/auth'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABELS } from '@/utils/constants'
import { formatNumber } from '@/utils/format'

/**
 * 个人中心（F4-6，计划 §5.12）：
 * 展示脱敏账号信息 + 修改 realName/major + 退出登录。
 * 不展示密码散列；用户名/角色只读；保存成功后同步 auth store 与 localStorage。
 */
const router = useRouter()
const auth = useAuthStore()

const saving = ref(false)
const error = ref('')
const errorTrace = ref('')

const form = reactive({
  realName: '',
  major: '',
})

const user = computed(() => auth.user)
const roleLabel = computed(() =>
  user.value ? (ROLE_LABELS[user.value.role] ?? user.value.role) : '—',
)
const statusLabel = computed(() => (user.value?.status === 1 ? '启用' : '禁用'))
const initial = computed(() => {
  const name = user.value?.realName || user.value?.username || '?'
  return name.slice(0, 1).toUpperCase()
})
const identity = computed(() => `${user.value?.username ?? '—'} · ${roleLabel.value}`)

const realNameValid = computed(() => form.realName.trim().length <= 50)
const majorValid = computed(() => form.major.trim().length <= 100)
const valid = computed(() => realNameValid.value && majorValid.value)
const dirty = computed(() => {
  if (!user.value) return false
  return (
    form.realName.trim() !== (user.value.realName ?? '') ||
    form.major.trim() !== (user.value.major ?? '')
  )
})
const canSave = computed(() => dirty.value && valid.value && !saving.value)

function syncForm(): void {
  form.realName = user.value?.realName ?? ''
  form.major = user.value?.major ?? ''
}

async function save(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  errorTrace.value = ''
  try {
    const updated = await updateMe({
      realName: form.realName.trim(),
      major: form.major.trim(),
    })
    auth.applyUser(updated)
    syncForm()
    ElMessage.success('资料已保存')
  } catch (cause) {
    error.value = errorMessage(cause, '资料保存失败，请稍后重试')
    errorTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  } finally {
    saving.value = false
  }
}

function reset(): void {
  syncForm()
  error.value = ''
  errorTrace.value = ''
}

async function logout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录，确认退出当前账号？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}

watch(user, syncForm, { immediate: true })
</script>

<template>
  <div class="profile-page" data-testid="profile-page">
    <AppPageHeader
      title="个人中心"
      description="查看账号信息、维护个人资料；用户名与角色由管理员维护，密码修改暂未开放。"
    >
      <template #actions>
        <el-button :icon="Refresh" :disabled="saving" data-testid="profile-reset" @click="reset">
          恢复
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!canSave"
          data-testid="profile-save"
          @click="save"
        >
          保存资料
        </el-button>
      </template>
    </AppPageHeader>

    <router-link class="profile-ai-entry" :to="{ name: 'profile-ai-config' }" data-testid="profile-ai-config-entry">
      <GlassPanel class="profile-ai-entry__panel" hoverable :padded="false">
        <span class="profile-ai-entry__icon" aria-hidden="true">
          <el-icon><Cpu /></el-icon>
        </span>
        <span class="profile-ai-entry__body">
          <strong>AI 服务配置</strong>
          <small>接入你自己的 OpenAI 兼容大模型，面试即时使用（可选）</small>
        </span>
        <el-icon class="profile-ai-entry__arrow"><ArrowRight /></el-icon>
      </GlassPanel>
    </router-link>

    <div class="profile-grid">
      <GlassPanel class="account-card" tone="strong" :padded="false" data-testid="profile-account">
        <div class="account-card__hero">
          <span class="account-card__avatar" aria-hidden="true">{{ initial }}</span>
          <div class="account-card__identity">
            <h2 data-testid="profile-username">{{ user?.username || '—' }}</h2>
            <p>{{ identity }}</p>
          </div>
        </div>

        <dl class="account-card__facts">
          <div>
            <dt>真实姓名</dt>
            <dd>{{ user?.realName || '未填写' }}</dd>
          </div>
          <div>
            <dt>专业</dt>
            <dd>{{ user?.major || '未填写' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ roleLabel }}</dd>
          </div>
          <div>
            <dt>账号状态</dt>
            <dd>
              <el-tag
                :type="user?.status === 1 ? 'success' : 'danger'"
                effect="plain"
                round
                size="small"
              >
                {{ statusLabel }}
              </el-tag>
            </dd>
          </div>
          <div>
            <dt>用户 ID</dt>
            <dd>{{ formatNumber(user?.id) }}</dd>
          </div>
        </dl>

        <p class="account-card__privacy">
          <el-icon><Lock /></el-icon>
          页面只展示脱敏账号信息，不会展示密码或密码散列。
        </p>
      </GlassPanel>

      <GlassPanel class="profile-form-card" :padded="false">
        <header class="profile-panel-head">
          <div>
            <h2>编辑资料</h2>
            <p>真实姓名最多 50 字，专业最多 100 字；留空表示不填写。</p>
          </div>
          <span class="profile-panel-head__state" :class="{ 'is-dirty': dirty }">
            {{ dirty ? '有未保存修改' : '资料已同步' }}
          </span>
        </header>

        <el-form class="profile-form" label-width="88px" data-testid="profile-form" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              :model-value="user?.username ?? ''"
              disabled
              aria-label="用户名（不可修改）"
            />
          </el-form-item>

          <el-form-item label="真实姓名" :error="realNameValid ? '' : '真实姓名不能超过 50 字'">
            <el-input
              v-model="form.realName"
              maxlength="50"
              show-word-limit
              clearable
              placeholder="请输入真实姓名"
              data-testid="profile-realname"
            />
          </el-form-item>

          <el-form-item label="专业" :error="majorValid ? '' : '专业不能超过 100 字'">
            <el-input
              v-model="form.major"
              maxlength="100"
              show-word-limit
              clearable
              placeholder="如：软件工程"
              data-testid="profile-major"
            />
          </el-form-item>

          <el-form-item label="角色">
            <el-input :model-value="roleLabel" disabled aria-label="角色（不可修改）" />
          </el-form-item>

          <p v-if="error" class="profile-form__error" role="alert">
            <el-icon><User /></el-icon>
            {{ error }}
            <span v-if="errorTrace" class="profile-form__trace">traceId: {{ errorTrace }}</span>
          </p>

          <div class="profile-form__actions">
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!canSave"
              data-testid="profile-save-bottom"
              @click="save"
            >
              保存资料
            </el-button>
            <el-button :disabled="!dirty || saving" @click="reset">放弃修改</el-button>
            <span class="profile-form__hint">保存成功后右上角与账号卡会立即更新。</span>
          </div>
        </el-form>

        <footer class="profile-danger">
          <div>
            <h3>退出登录</h3>
            <p>退出后本机 token 立即清除，需要重新登录才能继续使用。</p>
          </div>
          <el-button type="danger" plain data-testid="profile-logout" @click="logout">
            退出登录
          </el-button>
        </footer>
      </GlassPanel>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.profile-ai-entry {
  text-decoration: none;
  color: inherit;
}

.profile-ai-entry__panel {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
}

.profile-ai-entry__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #7c3aed, #2563eb);
  color: #fff;
  font-size: 18px;
}

.profile-ai-entry__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.profile-ai-entry__body strong {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.profile-ai-entry__body small {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.profile-ai-entry__arrow {
  flex: none;
  margin-left: auto;
  color: var(--color-text-tertiary);
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.2fr);
  gap: var(--space-4);
  align-items: start;
}

.account-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5);
}

.account-card__hero {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.account-card__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 58px;
  height: 58px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 18px;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.22);
  color: #fff;
  font-size: var(--font-size-xl);
  font-weight: 800;
}

.account-card__identity h2 {
  font-size: var(--font-size-lg);
  font-weight: 800;
  line-height: 1.3;
}

.account-card__identity p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.account-card__facts {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
}

.account-card__facts > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px dashed rgba(148, 163, 184, 0.24);
}

.account-card__facts dt {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.account-card__facts dd {
  margin: 0;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-align: right;
}

.account-card__privacy {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
}

.profile-form-card {
  min-width: 0;
}

.profile-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.profile-panel-head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.profile-panel-head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.profile-panel-head__state {
  flex: none;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(22, 163, 74, 0.1);
  color: var(--color-success);
  font-size: 11px;
  font-weight: 600;
}

.profile-panel-head__state.is-dirty {
  background: rgba(217, 119, 6, 0.12);
  color: var(--color-warning);
}

.profile-form {
  padding: var(--space-4) var(--space-5);
}

.profile-form__error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(220, 38, 38, 0.24);
  border-radius: var(--radius-md);
  background: rgba(254, 242, 242, 0.74);
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.profile-form__trace {
  color: var(--color-text-tertiary);
}

.profile-form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.profile-form__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.profile-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.46);
  background: rgba(254, 242, 242, 0.32);
}

.profile-danger h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.profile-danger p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

@media (max-width: 980px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .profile-panel-head,
  .profile-form,
  .profile-danger {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .profile-form {
    --el-form-label-width: 76px;
  }

  .profile-danger {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
