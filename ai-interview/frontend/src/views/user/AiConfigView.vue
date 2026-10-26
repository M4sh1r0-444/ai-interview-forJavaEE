<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { errorMessage, isBizError } from '@/api/http'
import { deleteAiConfig, getAiConfig, saveAiConfig, testAiConfig } from '@/api/aiConfig'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import type { AiConfigTestVO, AiConfigVO } from '@/types/aiConfig'

/**
 * 个人中心 · AI 服务配置（V9）：
 * 用户保存自己的 OpenAI 兼容服务（接口地址 / API Key / 模型名），保存后
 * 面试的出题/追问/评分/报告即时走个人服务；未配置或密钥不可用时回退平台默认。
 * API Key 只回掩码，更新时留空即保留已保存值。
 */
const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const errorTrace = ref('')
const config = ref<AiConfigVO | null>(null)
const testResult = ref<AiConfigTestVO | null>(null)

const form = reactive({
  apiUrl: '',
  apiKey: '',
  model: '',
})

const hasSaved = computed(() => config.value !== null)
/** 密文解密失败（加密密钥已轮换）：需重新输入 API Key 保存 */
const keyBroken = computed(() => !!config.value?.hasApiKey && !config.value?.apiKeyMasked)

const apiUrlValid = computed(() => /^https?:\/\/\S+\.\S+/.test(form.apiUrl.trim()) && form.apiUrl.trim().length <= 500)
const apiKeyValid = computed(() => form.apiKey.length <= 200)
const modelValid = computed(() => form.model.trim().length >= 1 && form.model.trim().length <= 100)
const valid = computed(() => apiUrlValid.value && apiKeyValid.value && modelValid.value)
const dirty = computed(
  () =>
    !hasSaved.value ||
    form.apiUrl.trim() !== config.value?.apiUrl ||
    form.model.trim() !== config.value?.model ||
    form.apiKey.trim() !== '',
)
const canSave = computed(() => dirty.value && valid.value && !saving.value && !loading.value)
const canTest = computed(
  () =>
    apiUrlValid.value &&
    modelValid.value &&
    !testing.value &&
    (form.apiKey.trim() !== '' || (!!config.value?.hasApiKey && !keyBroken.value)),
)

const statusTag = computed(() => {
  if (!hasSaved.value) {
    return { type: 'info' as const, text: '未配置 · 使用平台默认服务' }
  }
  return keyBroken.value
    ? { type: 'warning' as const, text: '已保存但密钥不可用' }
    : { type: 'success' as const, text: '已启用个人 AI 服务' }
})

function syncForm(): void {
  form.apiUrl = config.value?.apiUrl ?? ''
  form.model = config.value?.model ?? ''
  form.apiKey = ''
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 未配置时后端 data 缺省（拦截器返回 undefined），统一归一为 null
    config.value = (await getAiConfig()) ?? null
    syncForm()
  } catch (cause) {
    error.value = errorMessage(cause, 'AI 配置加载失败，请刷新重试')
    errorTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  errorTrace.value = ''
  testResult.value = null
  try {
    config.value = await saveAiConfig({
      apiUrl: form.apiUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
    })
    form.apiKey = ''
    ElMessage.success('AI 服务配置已保存，面试将即时使用')
  } catch (cause) {
    error.value = errorMessage(cause, '保存失败，请稍后重试')
    errorTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  } finally {
    saving.value = false
  }
}

async function runTest(): Promise<void> {
  if (!canTest.value) return
  testing.value = true
  error.value = ''
  errorTrace.value = ''
  testResult.value = null
  try {
    testResult.value = await testAiConfig({
      apiUrl: form.apiUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
    })
  } catch (cause) {
    error.value = errorMessage(cause, '测试请求失败，请稍后重试')
    errorTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  } finally {
    testing.value = false
  }
}

async function clearConfig(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '清除后将恢复使用平台默认 AI 服务，确认清除个人配置？',
      '清除配置',
      {
        type: 'warning',
        confirmButtonText: '清除配置',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  error.value = ''
  errorTrace.value = ''
  testResult.value = null
  try {
    await deleteAiConfig()
    config.value = null
    syncForm()
    ElMessage.success('已清除，恢复使用平台默认 AI 服务')
  } catch (cause) {
    error.value = errorMessage(cause, '清除失败，请稍后重试')
    errorTrace.value = isBizError(cause) ? (cause.traceId ?? '') : ''
  }
}

function reset(): void {
  syncForm()
  error.value = ''
  errorTrace.value = ''
  testResult.value = null
}

onMounted(load)
</script>

<template>
  <div class="ai-config-page" data-testid="ai-config-page">
    <AppPageHeader
      title="AI 服务配置"
      description="配置你自己的 OpenAI 兼容大模型服务；保存后面试的出题、追问、评分与报告即时改用该服务，未配置时使用平台默认。"
      back-to="/profile"
      show-back
    >
      <template #actions>
        <el-button :loading="testing" :disabled="!canTest" data-testid="ai-config-test" @click="runTest">
          测试连通性
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!canSave"
          data-testid="ai-config-save"
          @click="save"
        >
          保存配置
        </el-button>
      </template>
    </AppPageHeader>

    <GlassPanel class="ai-config-card" :padded="false" v-loading="loading">
      <header class="ai-config-head">
        <div>
          <h2>个人 AI 服务</h2>
          <p>OpenAI 兼容接口（如通义千问兼容模式、DeepSeek、本地 Ollama 网关等）。</p>
        </div>
        <el-tag :type="statusTag.type" effect="plain" round size="small" data-testid="ai-config-status">
          {{ statusTag.text }}
        </el-tag>
      </header>

      <dl v-if="hasSaved" class="ai-config-summary" data-testid="ai-config-summary">
        <div>
          <dt>模型</dt>
          <dd>{{ config?.model || '—' }}</dd>
        </div>
        <div>
          <dt>API Key</dt>
          <dd>
            <template v-if="keyBroken">
              <el-icon class="ai-config-summary__warn"><Warning /></el-icon>
              无法解密，请重新保存
            </template>
            <template v-else>{{ config?.apiKeyMasked }}</template>
          </dd>
        </div>
        <div class="ai-config-summary__url">
          <dt>接口地址</dt>
          <dd>{{ config?.apiUrl }}</dd>
        </div>
      </dl>

      <el-form class="ai-config-form" label-width="88px" data-testid="ai-config-form" @submit.prevent>
        <el-form-item label="接口地址" :error="apiUrlValid ? '' : '需为 http(s):// 开头的完整接口地址'">
          <el-input
            v-model="form.apiUrl"
            maxlength="500"
            clearable
            placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
            data-testid="ai-config-url"
          />
        </el-form-item>

        <el-form-item
          label="API Key"
          :error="apiKeyValid ? '' : 'API Key 不能超过 200 字符'"
        >
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="config?.hasApiKey ? '已保存，留空则不修改' : 'sk-xxxxxxxxxxxxxxxx'"
            data-testid="ai-config-key"
          />
        </el-form-item>

        <el-form-item label="模型名称" :error="modelValid ? '' : '模型名不能为空且不超过 100 字符'">
          <el-input
            v-model="form.model"
            maxlength="100"
            clearable
            placeholder="qwen-plus"
            data-testid="ai-config-model"
          />
        </el-form-item>

        <p v-if="testResult" class="ai-config-test-result" :class="testResult.ok ? 'is-ok' : 'is-fail'" role="status" data-testid="ai-config-test-result">
          {{ testResult.ok ? `连通正常，耗时 ${testResult.latencyMs ?? '—'} ms` : `连通失败：${testResult.message}` }}
        </p>

        <p v-if="error" class="ai-config-form__error" role="alert">
          <el-icon><Warning /></el-icon>
          {{ error }}
          <span v-if="errorTrace" class="ai-config-form__trace">traceId: {{ errorTrace }}</span>
        </p>

        <div class="ai-config-form__actions">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!canSave"
            data-testid="ai-config-save-bottom"
            @click="save"
          >
            保存配置
          </el-button>
          <el-button :disabled="!dirty || saving" data-testid="ai-config-reset" @click="reset">
            放弃修改
          </el-button>
          <span class="ai-config-form__hint">保存后即时生效；个人服务故障时自动降级，不会中断面试。</span>
        </div>
      </el-form>

      <footer v-if="hasSaved" class="ai-config-danger">
        <div>
          <h3>清除配置</h3>
          <p>删除已保存的接口地址、API Key 与模型信息，面试恢复使用平台默认 AI 服务。</p>
        </div>
        <el-button type="danger" plain data-testid="ai-config-delete" @click="clearConfig">
          清除配置
        </el-button>
      </footer>
    </GlassPanel>
  </div>
</template>

<style scoped>
.ai-config-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
  max-width: 860px;
}

.ai-config-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.ai-config-head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.ai-config-head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.ai-config-summary {
  display: grid;
  grid-template-columns: minmax(120px, 0.5fr) minmax(140px, 0.5fr);
  gap: var(--space-2) var(--space-4);
  margin: 0;
  padding: var(--space-3) var(--space-5) 0;
}

.ai-config-summary__url {
  grid-column: 1 / -1;
}

.ai-config-summary > div {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px dashed rgba(148, 163, 184, 0.24);
}

.ai-config-summary dt {
  flex: none;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.ai-config-summary dd {
  margin: 0;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
  word-break: break-all;
}

.ai-config-summary__warn {
  color: var(--color-warning);
  vertical-align: -2px;
  margin-right: 4px;
}

.ai-config-form {
  padding: var(--space-4) var(--space-5);
}

.ai-config-test-result {
  margin: 0 0 var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.ai-config-test-result.is-ok {
  border: 1px solid rgba(22, 163, 74, 0.24);
  background: rgba(236, 253, 245, 0.8);
  color: var(--color-success);
}

.ai-config-test-result.is-fail {
  border: 1px solid rgba(220, 38, 38, 0.24);
  background: rgba(254, 242, 242, 0.74);
  color: var(--color-danger);
}

.ai-config-form__error {
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

.ai-config-form__trace {
  color: var(--color-text-tertiary);
}

.ai-config-form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.ai-config-form__hint {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.ai-config-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.46);
  background: rgba(254, 242, 242, 0.32);
}

.ai-config-danger h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.ai-config-danger p {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

@media (max-width: 640px) {
  .ai-config-head,
  .ai-config-summary,
  .ai-config-form,
  .ai-config-danger {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }

  .ai-config-form {
    --el-form-label-width: 76px;
  }

  .ai-config-summary {
    grid-template-columns: 1fr;
  }

  .ai-config-danger {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
