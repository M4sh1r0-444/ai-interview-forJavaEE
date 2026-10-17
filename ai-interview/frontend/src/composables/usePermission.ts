import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types/auth'

/** 只做 UI 显隐；服务端仍强制校验权限（计划 §6.8）。 */
export function usePermission() {
  const auth = useAuthStore()

  const isStudent = computed(() => auth.user?.role === 'STUDENT')
  const isAdmin = computed(() => auth.user?.role === 'ADMIN')

  function hasRole(...roles: Role[]): boolean {
    const current = auth.user?.role
    return !!current && roles.includes(current)
  }

  return { isStudent, isAdmin, hasRole }
}
