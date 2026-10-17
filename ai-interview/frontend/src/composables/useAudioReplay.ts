import { onScopeDispose, ref, type Ref } from 'vue'

import { errorMessage, isBizError } from '@/api/http'
import { fetchAudioBlob } from '@/api/interview'

/**
 * 受保护音频回放（F3-3 / F4-3）：blob URL + 单实例播放器。
 * 一次只允许一个音频播放，切换/卸载会暂停并释放 object URL。
 */
export function useAudioReplay(sessionId: Ref<number>) {
  const playingRound = ref<number | null>(null)
  const loadingRound = ref<number | null>(null)
  const error = ref('')

  let audio: HTMLAudioElement | null = null
  let objectUrl: string | null = null

  function release(): void {
    if (audio) {
      audio.onended = null
      audio.onerror = null
      audio.pause()
      audio.src = ''
      audio = null
    }
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
      objectUrl = null
    }
    playingRound.value = null
  }

  function stop(): void {
    release()
    loadingRound.value = null
  }

  async function replay(roundNo: number, messageId: number): Promise<void> {
    const id = sessionId.value
    if (!id || !Number.isFinite(messageId)) return
    release()
    error.value = ''
    loadingRound.value = roundNo
    try {
      const blob = await fetchAudioBlob(id, messageId)
      objectUrl = URL.createObjectURL(blob)
      const player = new Audio(objectUrl)
      audio = player
      player.onended = () => {
        if (audio === player) release()
      }
      player.onerror = () => {
        if (audio === player) {
          error.value = '音频播放失败，文件可能已过期'
          release()
        }
      }
      await player.play()
      playingRound.value = roundNo
    } catch (cause) {
      release()
      error.value = isBizError(cause)
        ? errorMessage(cause, '音频回放失败，请稍后重试')
        : '音频格式不受支持或文件已过期，暂时无法回放'
    } finally {
      loadingRound.value = null
    }
  }

  onScopeDispose(() => {
    stop()
  })

  return { playingRound, loadingRound, error, replay, stop }
}
