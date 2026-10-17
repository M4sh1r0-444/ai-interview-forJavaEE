import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 纯 UI 状态：侧栏折叠、离线、路由加载条。不持久化。 */
export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const globalOffline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
  const routeLoading = ref(false)

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setSidebarCollapsed(value: boolean): void {
    sidebarCollapsed.value = value
  }

  function setOffline(value: boolean): void {
    globalOffline.value = value
  }

  function startRouteLoading(): void {
    routeLoading.value = true
  }

  function stopRouteLoading(): void {
    routeLoading.value = false
  }

  return {
    sidebarCollapsed,
    globalOffline,
    routeLoading,
    toggleSidebar,
    setSidebarCollapsed,
    setOffline,
    startRouteLoading,
    stopRouteLoading,
  }
})
