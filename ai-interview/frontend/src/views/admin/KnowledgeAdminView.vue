<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { CircleCheck, InfoFilled, MagicStick, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { adminCreateKnowledge, adminListPositions, adminRebuildKnowledge } from '@/api/admin'
import { errorMessage, isBizError } from '@/api/http'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import GlassPanel from '@/components/common/GlassPanel.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import type { KnowledgeChunk, PositionAdminVO } from '@/types/admin'
import { joinCommaList, splitCommaList } from '@/utils/admin'
import { formatDateTime } from '@/utils/format'

/**
 * 管理端知识库（F5-7，计划 §5.17）：
 * 现状后端只有「新增片段 + 重建 FULLTEXT 索引」两个接口，没有列表/编辑/删除；
 * 本页不伪造列表，明确说明能力边界，新增成功回显关键字/技能/岗位。
 */
const positions = ref<PositionAdminVO[]>([])
const positionsLoading = ref(true)
const positionsError = ref<unknown>(null)

const submitting = ref(false)
const rebuilding = ref(false)
const formError = ref('')
const rebuildMessage = ref('')
const rebuildError = ref('')
const created = ref<KnowledgeChunk | null>(null)
const formRef = ref<FormInstance>()

interface KnowledgeFormModel {
  positionId: number | null
  title: string
  content: string
  keywords: string
  skillTags: string[]
  difficulty: number | null
}

const form = reactive<KnowledgeFormModel>({
  positionId: null,
  title: '',
  content: '',
  keywords: '',
  skillTags: [],
  difficulty: null,
})

const positionsErrorText = computed(() =>
  errorMessage(positionsError.value, '岗位列表加载失败，请稍后重试'),
)
const positionsErrorCode = computed(() =>
  isBizError(positionsError.value) ? positionsError.value.code : undefined,
)
const positionsErrorTrace = computed(() =>
  isBizError(positionsError.value) ? (positionsError.value.traceId ?? '') : '',
)
const selectedPosition = computed(
  () => positions.value.find((item) => item.id === form.positionId) ?? null,
)
const skillOptions = computed(() => {
  const options = (selectedPosition.value?.skills ?? []).map((skill) => ({
    code: skill.skillCode,
    name: skill.skillName,
  }))
  for (const tag of form.skillTags) {
    if (!options.some((item) => item.code === tag)) options.push({ code: tag, name: tag })
  }
  return options
})
const keywordList = computed(() => splitCommaList(form.keywords))
const skillTagsString = computed(() => joinCommaList(form.skillTags))
const createdPositionName = computed(() => {
  const current = created.value
  if (!current) return '—'
  return (
    positions.value.find((item) => item.id === current.positionId)?.name ??
    `岗位 #${current.positionId}`
  )
})
const canSubmit = computed(
  () => positions.value.length > 0 && !submitting.value && form.positionId != null,
)

const rules: FormRules = {
  positionId: [{ required: true, message: '请选择岗位', trigger: 'change' }],
  title: [
    { required: true, message: '知识片段标题必填', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度 2~200 字', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '知识片段内容必填', trigger: 'blur' },
    { min: 10, max: 8000, message: '内容长度 10~8000 字', trigger: 'blur' },
  ],
  keywords: [
    {
      validator: (_rule, value: string, callback) => {
        const text = (value ?? '').trim()
        if (text.length < 4 || text.length > 500) {
          callback(new Error('keywords 长度 4~500 字（逗号分隔）'))
          return
        }
        if (splitCommaList(text).length === 0) {
          callback(new Error('至少填写 1 个关键词'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  skillTags: [
    {
      validator: (_rule, value: string[], callback) => {
        const tags = Array.isArray(value) ? value : []
        const joined = joinCommaList(tags)
        if (tags.length === 0) {
          callback(new Error('至少选择 1 个技能标签'))
          return
        }
        if (joined.length < 2 || joined.length > 200) {
          callback(new Error('技能标签总长度需为 2~200 字符'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

async function loadPositions(): Promise<void> {
  positionsLoading.value = true
  positionsError.value = null
  try {
    const result = await adminListPositions({ page: 1, size: 100 })
    positions.value = result.records ?? []
    if (form.positionId == null && positions.value.length > 0) {
      form.positionId = positions.value[0].id
    }
  } catch (cause) {
    positionsError.value = cause
    positions.value = []
  } finally {
    positionsLoading.value = false
  }
}

function handlePositionChange(value: number): void {
  const codes = new Set(
    (positions.value.find((item) => item.id === value)?.skills ?? []).map(
      (skill) => skill.skillCode,
    ),
  )
  form.skillTags = form.skillTags.filter((tag) => codes.has(tag))
}

function handleDifficultyChange(value: number | undefined): void {
  form.difficulty = typeof value === 'number' ? value : null
}

function resetForm(): void {
  Object.assign(form, {
    positionId: form.positionId,
    title: '',
    content: '',
    keywords: '',
    skillTags: [],
    difficulty: null,
  })
  formError.value = ''
  nextTick(() => formRef.value?.clearValidate())
}

async function submit(): Promise<void> {
  if (!formRef.value || submitting.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.positionId) {
    formError.value = '请选择岗位'
    return
  }
  const keywords = joinCommaList(splitCommaList(form.keywords))
  const skillTags = joinCommaList(form.skillTags)
  if (keywords.length < 4 || keywords.length > 500) {
    formError.value = 'keywords 长度 4~500 字（逗号分隔）'
    return
  }
  if (skillTags.length < 2 || skillTags.length > 200) {
    formError.value = '技能标签总长度需为 2~200 字符'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const chunk = await adminCreateKnowledge({
      positionId: form.positionId,
      title: form.title.trim(),
      content: form.content.trim(),
      keywords,
      skillTags,
      ...(form.difficulty != null ? { difficulty: form.difficulty } : {}),
    })
    created.value = chunk
    ElMessage.success('知识片段已新增，已进入检索词典；下一场面试即可命中')
    resetForm()
  } catch (cause) {
    formError.value = errorMessage(cause, '知识片段新增失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function rebuild(): Promise<void> {
  if (rebuilding.value) return
  try {
    await ElMessageBox.confirm(
      '重建会执行 OPTIMIZE TABLE 优化 FULLTEXT 索引，期间可能短暂锁表；确认现在重建索引？',
      '重建全文索引',
      {
        type: 'warning',
        confirmButtonText: '重建索引',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  rebuilding.value = true
  rebuildMessage.value = ''
  rebuildError.value = ''
  try {
    const result = await adminRebuildKnowledge()
    rebuildMessage.value = result.rebuilt
      ? '全文索引已重建（rebuilt: true），新增片段已可被检索。'
      : '索引重建完成，但后端未返回 rebuilt=true。'
    ElMessage.success('全文索引重建完成')
  } catch (cause) {
    rebuildError.value = errorMessage(cause, '索引重建失败，请稍后重试')
    ElMessage.error(rebuildError.value)
  } finally {
    rebuilding.value = false
  }
}

onMounted(loadPositions)
</script>

<template>
  <div class="knowledge-page admin-page" data-testid="knowledge-page">
    <AppPageHeader
      title="知识库"
      description="新增知识片段并重建 FULLTEXT 索引；片段会参与关键词检索并注入评分/出题 Prompt。"
    >
      <template #actions>
        <el-button
          :icon="RefreshRight"
          :loading="rebuilding"
          data-testid="knowledge-rebuild"
          @click="rebuild"
        >
          重建索引
        </el-button>
      </template>
    </AppPageHeader>

    <div class="admin-note">
      <el-icon :size="16" class="knowledge-page__note-icon"><InfoFilled /></el-icon>
      <div>
        <strong>RAG 链路与本页能力边界</strong>
        <div>
          学生作答 → KeywordExtractor 提取关键词 → FULLTEXT 检索 Top5 片段 → 注入评分/追问
          Prompt。当前版本后端<b>没有知识片段列表/编辑/删除接口</b>，本页只提供新增与重建索引，
          不展示假列表；如需列表能力请另行立项后端变更（D33 / R-F11）。
        </div>
      </div>
    </div>

    <LoadingSkeleton v-if="positionsLoading" type="card" />

    <ErrorState
      v-else-if="positionsError"
      title="岗位列表加载失败"
      :message="positionsErrorText"
      :code="positionsErrorCode"
      :trace-id="positionsErrorTrace"
      @retry="loadPositions"
    />

    <EmptyState
      v-else-if="positions.length === 0"
      title="还没有岗位"
      description="知识片段必须归属岗位；请先在「岗位与能力模型」创建岗位。"
      :show-action="false"
    />

    <div v-else class="knowledge-layout">
      <GlassPanel :padded="false" class="knowledge-form-panel">
        <div class="knowledge-panel-head">
          <div>
            <h2>新增知识片段</h2>
            <p>标题 2~200 字，内容 10~8000 字；关键词与技能标签用逗号分隔后落库。</p>
          </div>
          <el-tag type="info" effect="plain" round>MANUAL</el-tag>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="knowledge-form"
        >
          <div class="admin-form-grid">
            <el-form-item label="岗位" prop="positionId">
              <el-select
                v-model="form.positionId"
                placeholder="请选择岗位"
                filterable
                data-testid="knowledge-form-position"
                @change="handlePositionChange"
              >
                <el-option
                  v-for="item in positions"
                  :key="item.id"
                  :value="item.id"
                  :label="`${item.name}（${item.code}）`"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="技能标签（须对齐岗位技能）" prop="skillTags">
              <el-select
                v-model="form.skillTags"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择岗位技能（可多选）"
                data-testid="knowledge-form-skills"
              >
                <el-option
                  v-for="skill in skillOptions"
                  :key="skill.code"
                  :value="skill.code"
                  :label="`${skill.name}（${skill.code}）`"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="标题" prop="title" class="admin-form-grid__full">
              <el-input
                v-model="form.title"
                maxlength="200"
                show-word-limit
                placeholder="如：缓存穿透、击穿、雪崩的区别与对策"
                data-testid="knowledge-form-title"
              />
            </el-form-item>

            <el-form-item label="内容" prop="content" class="admin-form-grid__full">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="7"
                maxlength="8000"
                show-word-limit
                placeholder="知识片段正文（10~8000 字）；会被 FULLTEXT 检索并注入 Prompt"
                data-testid="knowledge-form-content"
              />
            </el-form-item>

            <el-form-item
              label="关键词（逗号分隔，4~500 字）"
              prop="keywords"
              class="admin-form-grid__full"
            >
              <el-input
                v-model="form.keywords"
                maxlength="500"
                show-word-limit
                placeholder="如：缓存穿透,缓存击穿,互斥锁,逻辑过期"
                data-testid="knowledge-form-keywords"
              />
              <div class="knowledge-keyword-preview">
                <span>解析出 {{ keywordList.length }} 个关键词</span>
                <el-tag
                  v-for="keyword in keywordList.slice(0, 12)"
                  :key="keyword"
                  size="small"
                  effect="plain"
                >
                  {{ keyword }}
                </el-tag>
                <span v-if="keywordList.length > 12">…</span>
              </div>
            </el-form-item>

            <el-form-item label="难度提示（可选 1~5）">
              <el-select
                :model-value="form.difficulty ?? undefined"
                clearable
                placeholder="不设置"
                data-testid="knowledge-form-difficulty"
                @change="handleDifficultyChange"
              >
                <el-option
                  v-for="level in 5"
                  :key="level"
                  :value="level"
                  :label="`难度 ${level}`"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="技能编码预览">
              <code class="knowledge-skill-preview">{{ skillTagsString || '—' }}</code>
            </el-form-item>
          </div>
        </el-form>

        <p v-if="formError" class="admin-inline-alert" role="alert">{{ formError }}</p>

        <div class="knowledge-form-footer">
          <span class="admin-dialog-footer__hint">
            保存后立即刷新检索词典；建议随后执行一次「重建索引」
          </span>
          <div>
            <el-button text @click="resetForm">清空</el-button>
            <el-button
              type="primary"
              :icon="MagicStick"
              :loading="submitting"
              :disabled="!canSubmit"
              data-testid="knowledge-submit"
              @click="submit"
            >
              新增知识片段
            </el-button>
          </div>
        </div>
      </GlassPanel>

      <aside class="knowledge-side">
        <GlassPanel v-if="created" class="knowledge-success" data-testid="knowledge-success">
          <div class="knowledge-success__head">
            <el-icon :size="20" class="knowledge-success__icon"><CircleCheck /></el-icon>
            <div>
              <h3>新增成功</h3>
              <p>已进入检索词典，下一场面试即可命中。</p>
            </div>
          </div>
          <dl class="knowledge-success__meta">
            <div>
              <dt>ID</dt>
              <dd>{{ created.id }}</dd>
            </div>
            <div>
              <dt>岗位</dt>
              <dd>{{ createdPositionName }}</dd>
            </div>
            <div>
              <dt>技能</dt>
              <dd>{{ created.skillTags || '—' }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(created.createdAt) }}</dd>
            </div>
          </dl>
          <p class="knowledge-success__title">{{ created.title }}</p>
          <p class="knowledge-success__keywords">{{ created.keywords }}</p>
        </GlassPanel>

        <GlassPanel v-else class="knowledge-side__placeholder">
          <h3>成功回显区</h3>
          <p>新增成功后，这里会展示片段 ID、岗位、技能与关键词，便于确认落库结果。</p>
        </GlassPanel>

        <GlassPanel class="knowledge-rebuild-card">
          <h3>重建全文索引</h3>
          <p>
            后端执行 <code>OPTIMIZE TABLE knowledge_chunk</code>。新增片段默认已即时生效，
            重建用于维护 ngram FULLTEXT 索引。
          </p>
          <el-button
            :icon="RefreshRight"
            :loading="rebuilding"
            data-testid="knowledge-rebuild-card"
            @click="rebuild"
          >
            立即重建索引
          </el-button>
          <p v-if="rebuildMessage" class="knowledge-rebuild-card__ok" role="status">
            {{ rebuildMessage }}
          </p>
          <p v-if="rebuildError" class="knowledge-rebuild-card__error" role="alert">
            {{ rebuildError }}
          </p>
        </GlassPanel>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.knowledge-page {
  min-width: 0;
}

.knowledge-page__note-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.knowledge-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.9fr);
  gap: var(--space-4);
  align-items: start;
}

.knowledge-form-panel {
  min-width: 0;
}

.knowledge-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.46);
}

.knowledge-panel-head h2 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.knowledge-panel-head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.knowledge-form {
  padding: var(--space-4) var(--space-5) 0;
}

.knowledge-form :deep(.el-form-item .el-select) {
  width: 100%;
}

.knowledge-keyword-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.knowledge-skill-preview {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.46);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-form-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin: var(--space-2) 0 0;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.46);
}

.knowledge-side {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.knowledge-success {
  border-color: rgba(22, 163, 74, 0.24);
}

.knowledge-success__head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.knowledge-success__icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-success);
}

.knowledge-success__head h3 {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.knowledge-success__head p {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.knowledge-success__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  margin: var(--space-3) 0;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.4);
}

.knowledge-success__meta div {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.knowledge-success__meta dt {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.knowledge-success__meta dd {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-success__title {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1.6;
}

.knowledge-success__keywords {
  margin-top: 4px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.6;
  word-break: break-all;
}

.knowledge-side__placeholder h3,
.knowledge-rebuild-card h3 {
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.knowledge-side__placeholder p,
.knowledge-rebuild-card p {
  margin-top: 4px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.7;
}

.knowledge-rebuild-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.knowledge-rebuild-card__ok {
  color: var(--color-success);
}

.knowledge-rebuild-card__error {
  color: var(--color-danger);
}

@media (max-width: 1100px) {
  .knowledge-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .knowledge-panel-head,
  .knowledge-form,
  .knowledge-form-footer {
    padding-inline: var(--space-4);
  }
}
</style>
