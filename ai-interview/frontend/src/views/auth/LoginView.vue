<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { BizError } from '@/api/http'
import TraceIdText from '@/components/common/TraceIdText.vue'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types/auth'
import { ERROR_CODE_MESSAGES, STORAGE_KEYS } from '@/utils/constants'
import { resolveLoginTarget } from '@/utils/navigation'
import { readRaw, removeRaw, writeRaw } from '@/utils/storage'

interface LoginForm {
  username: string
  password: string
}

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive<LoginForm>({ username: '', password: '' })
const remember = ref(false)
const loading = ref(false)
const formError = ref('')
const traceId = ref('')
const cooldown = ref(0)
let cooldownTimer: number | null = null

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  const remembered = readRaw(STORAGE_KEYS.rememberUsername)
  const presetUsername = typeof route.query.username === 'string' ? route.query.username : ''
  if (presetUsername) {
    form.username = presetUsername
    remember.value = true
  } else if (remembered) {
    form.username = remembered
    remember.value = true
  }
})

onBeforeUnmount(() => {
  if (cooldownTimer != null) {
    window.clearInterval(cooldownTimer)
    cooldownTimer = null
  }
})

function startCooldown(seconds: number): void {
  cooldown.value = seconds
  if (cooldownTimer != null) window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      cooldown.value = 0
      if (cooldownTimer != null) {
        window.clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }
  }, 1000)
}

function fillDemo(username: string, password: string): void {
  form.username = username
  form.password = password
  formError.value = ''
}

function handleError(error: unknown): void {
  if (!(error instanceof BizError)) {
    formError.value = '登录失败，请稍后重试'
    return
  }
  traceId.value = error.traceId ?? ''
  switch (error.code) {
    case 10002:
      formError.value = '用户名或密码错误，请重新输入'
      break
    case 10003:
      formError.value = '账号已被禁用，请联系管理员'
      void ElMessageBox.alert('该账号已被管理员禁用，请联系管理员处理后再登录。', '无法登录', {
        type: 'warning',
        confirmButtonText: '我知道了',
      }).catch(() => undefined)
      break
    case 42901:
      startCooldown(60)
      formError.value = `登录尝试过于频繁，请 ${cooldown.value} 秒后再试`
      break
    case 40001:
      formError.value = error.message || ERROR_CODE_MESSAGES[40001]
      break
    case 50001:
      formError.value = '服务器暂时不可用，请稍后重试'
      break
    default:
      formError.value = error.message || ERROR_CODE_MESSAGES[-1]
  }
}

async function handleSubmit(): Promise<void> {
  if (loading.value || cooldown.value > 0) return
  formError.value = ''
  traceId.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await auth.login({ username: form.username.trim(), password: form.password })
    if (remember.value) {
      writeRaw(STORAGE_KEYS.rememberUsername, form.username.trim())
    } else {
      removeRaw(STORAGE_KEYS.rememberUsername)
    }
    ElMessage.success(`欢迎回来，${user.realName || user.username}`)
    const target = resolveLoginTarget(route.query.redirect, user.role as Role)
    await router.replace(target)
  } catch (error) {
    handleError(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-form">
    <header class="auth-form__header">
      <p class="auth-form__eyebrow">ACCOUNT LOGIN</p>
      <h2 class="auth-form__title">欢迎回来</h2>
      <p class="auth-form__subtitle">登录后继续你的模拟面试与能力提升计划。</p>
    </header>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      class="auth-form__body"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :prefix-icon="User"
          placeholder="请输入用户名"
          autocomplete="username"
          maxlength="50"
          clearable
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="请输入密码"
          autocomplete="current-password"
          maxlength="32"
        />
      </el-form-item>

      <div class="auth-form__row">
        <el-checkbox v-model="remember">记住用户名</el-checkbox>
        <span class="auth-form__hint">不勾选“记住密码”，仅保存用户名</span>
      </div>

      <div v-if="formError" class="auth-form__error" role="alert">
        <span>{{ formError }}</span>
      </div>
      <TraceIdText v-if="traceId" :trace-id="traceId" class="auth-form__trace" />

      <el-button
        type="primary"
        native-type="submit"
        size="large"
        class="auth-form__submit"
        :loading="loading"
        :disabled="cooldown > 0"
      >
        {{ cooldown > 0 ? `请等待 ${cooldown}s` : loading ? '登录中…' : '登录' }}
      </el-button>
    </el-form>

    <div class="auth-form__switch">
      还没有账号？
      <router-link :to="{ name: 'register' }">立即注册</router-link>
    </div>

    <div class="auth-form__demo">
      <p class="auth-form__demo-title">演示账号</p>
      <div class="auth-form__demo-list">
        <button type="button" class="demo-account" @click="fillDemo('demo01', '123456')">
          <span class="demo-account__role">学生</span>
          <span class="demo-account__name">demo01 / 123456</span>
        </button>
        <button type="button" class="demo-account" @click="fillDemo('admin', 'Admin@123456')">
          <span class="demo-account__role demo-account__role--admin">管理员</span>
          <span class="demo-account__name">admin / Admin@123456</span>
        </button>
      </div>
      <p class="auth-form__demo-note">点击可填入表单，不会自动提交。</p>
    </div>
  </div>
</template>

<style scoped>
.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.auth-form__eyebrow {
  margin-bottom: var(--space-2);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.auth-form__title {
  color: var(--color-text);
  font-size: var(--font-size-2xl);
  font-weight: 700;
  line-height: 1.3;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.6);
}

.auth-form__subtitle {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.auth-form__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.auth-form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: calc(-1 * var(--space-1)) 0 var(--space-2);
}

.auth-form__hint {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.auth-form__error {
  margin-bottom: var(--space-2);
  padding: 10px 12px;
  border: 1px solid rgba(220, 38, 38, 0.24);
  border-radius: 10px;
  background: rgba(254, 242, 242, 0.72);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.72) inset;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
  line-height: 1.5;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.auth-form__trace {
  align-self: flex-start;
  width: fit-content;
  margin-bottom: var(--space-2);
}

.auth-form__submit {
  width: 100%;
  margin-top: var(--space-1);
}

.auth-form__switch {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  text-align: center;
}

.auth-form__switch a {
  margin-left: 4px;
  font-weight: 600;
}

.auth-form__demo {
  padding: var(--space-3) var(--space-4);
  border: 1px dashed rgba(255, 255, 255, 0.82);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.32);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.62) inset;
  backdrop-filter: blur(10px) saturate(140%);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
}

.auth-form__demo-title {
  margin-bottom: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.auth-form__demo-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.demo-account {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 8px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.65) inset;
  color: var(--color-text);
  font-size: var(--font-size-xs);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    background var(--transition-fast);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.demo-account:hover {
  border-color: rgba(37, 99, 235, 0.5);
  background: rgba(255, 255, 255, 0.72);
  box-shadow:
    0 0 0 2px var(--color-primary-soft),
    0 1px 0 rgba(255, 255, 255, 0.8) inset;
}

.demo-account__role {
  flex: none;
  padding: 1px 7px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-pill);
  background: rgba(37, 99, 235, 0.12);
  color: var(--color-primary);
  font-weight: 600;
}

.demo-account__role--admin {
  background: rgba(245, 158, 11, 0.16);
  color: #92400e;
}

.demo-account__name {
  font-family: var(--font-mono);
}

.auth-form__demo-note {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 11px;
}
</style>
