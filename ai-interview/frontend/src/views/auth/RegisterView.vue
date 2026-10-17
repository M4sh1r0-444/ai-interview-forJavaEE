<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Postcard, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { register } from '@/api/auth'
import { BizError } from '@/api/http'
import TraceIdText from '@/components/common/TraceIdText.vue'
import { ERROR_CODE_MESSAGES } from '@/utils/constants'

interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  realName: string
  major: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  major: '',
})
const loading = ref(false)
const formError = ref('')
const usernameError = ref('')
const traceId = ref('')

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 50, message: '用户名长度需在 4~50 之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6~32 之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
  realName: [{ max: 50, message: '真实姓名不能超过 50 字', trigger: 'blur' }],
  major: [{ max: 100, message: '专业不能超过 100 字', trigger: 'blur' }],
}

function clearUsernameError(): void {
  usernameError.value = ''
}

function handleError(error: unknown): void {
  if (!(error instanceof BizError)) {
    formError.value = '注册失败，请稍后重试'
    return
  }
  traceId.value = error.traceId ?? ''
  if (error.code === 10001) {
    usernameError.value = '用户名已存在，请更换后重试'
    return
  }
  if (error.code === 40001) {
    formError.value = error.message || ERROR_CODE_MESSAGES[40001]
    return
  }
  if (error.code === 42901) {
    formError.value = '操作过于频繁，请稍后再试'
    return
  }
  formError.value = error.message || ERROR_CODE_MESSAGES[-1]
}

async function handleSubmit(): Promise<void> {
  if (loading.value) return
  formError.value = ''
  usernameError.value = ''
  traceId.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim() || undefined,
      major: form.major.trim() || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login', query: { username: form.username.trim() } })
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
      <p class="auth-form__eyebrow">CREATE ACCOUNT</p>
      <h2 class="auth-form__title">创建学生账号</h2>
      <p class="auth-form__subtitle">注册后即可参加模拟面试、查看报告与成长曲线。</p>
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
      <el-form-item label="用户名" prop="username" :error="usernameError">
        <el-input
          v-model="form.username"
          :prefix-icon="User"
          placeholder="4~50 位，登录使用"
          autocomplete="username"
          maxlength="50"
          clearable
          @input="clearUsernameError"
        />
      </el-form-item>

      <div class="auth-form__grid">
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="6~32 位"
            autocomplete="new-password"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="再次输入密码"
            autocomplete="new-password"
            maxlength="32"
          />
        </el-form-item>
      </div>

      <el-form-item label="真实姓名（选填）" prop="realName">
        <el-input
          v-model="form.realName"
          :prefix-icon="Postcard"
          placeholder="用于报告与问候展示"
          maxlength="50"
        />
      </el-form-item>

      <el-form-item label="专业（选填）" prop="major">
        <el-input v-model="form.major" placeholder="如：软件工程" maxlength="100" />
      </el-form-item>

      <div v-if="formError" class="auth-form__error" role="alert">{{ formError }}</div>
      <TraceIdText v-if="traceId" :trace-id="traceId" class="auth-form__trace" />

      <el-button
        type="primary"
        native-type="submit"
        size="large"
        class="auth-form__submit"
        :loading="loading"
      >
        {{ loading ? '注册中…' : '注册' }}
      </el-button>
    </el-form>

    <div class="auth-form__switch">
      已有账号？
      <router-link :to="{ name: 'login' }">返回登录</router-link>
    </div>

    <p class="auth-form__notice">
      注册即表示你知晓：面试回答与音频仅本人（及管理员）可见，仅用于课程设计演示。
    </p>
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

.auth-form__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
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

.auth-form__notice {
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.65;
  text-align: center;
}

@media (max-width: 480px) {
  .auth-form__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
