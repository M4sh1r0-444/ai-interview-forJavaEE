<script setup lang="ts">
withDefaults(
  defineProps<{
    page: number
    size: number
    total: number
    pageSizes?: number[]
    disabled?: boolean
  }>(),
  {
    pageSizes: () => [10, 20, 50],
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()

function handleCurrentChange(page: number): void {
  emit('update:page', page)
}

function handleSizeChange(size: number): void {
  emit('update:size', size)
}
</script>

<template>
  <div class="pagination-bar" data-testid="pagination">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :current-page="page"
      :page-size="size"
      :page-sizes="pageSizes"
      :total="total"
      :disabled="disabled"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-3) 0 var(--space-1);
  overflow-x: auto;
}

.pagination-bar :deep(.el-pagination) {
  flex-wrap: wrap;
  row-gap: var(--space-2);
}

@media (max-width: 640px) {
  .pagination-bar {
    justify-content: flex-start;
    padding-inline: var(--space-4);
  }

  .pagination-bar :deep(.el-pagination__jump),
  .pagination-bar :deep(.el-pagination__sizes) {
    display: none;
  }

  .pagination-bar :deep(.el-pagination__total) {
    font-size: var(--font-size-xs);
  }
}
</style>
