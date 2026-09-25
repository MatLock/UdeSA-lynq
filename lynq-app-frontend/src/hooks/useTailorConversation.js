import { useCallback, useRef, useState } from 'react'
import useApi from './useApi'
import resumeService from '../services/resumeService'
import { activeLocale } from '../i18n'

const TURN_IN_PROGRESS = 'TURN_IN_PROGRESS'
const CONVERSATION_EXHAUSTED = 'CONVERSATION_EXHAUSTED'
const EXHAUSTED = 'EXHAUSTED'
const CONFLICT = 409

const MAX_IN_PROGRESS_RETRIES = 40
const IN_PROGRESS_RETRY_MS = 5000

const newTurnKey = () =>
  globalThis.crypto?.randomUUID?.() ??
  `${Date.now()}-${Math.random().toString(16).slice(2)}`

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const runTurn = async (freshAuthFetch, conversationId, message, turnKey, attempt = 0) => {
  try {
    return await resumeService.tailor_turn(
      freshAuthFetch,
      conversationId,
      message,
      turnKey,
    )
  } catch (error) {
    const retryable =
      error?.status === CONFLICT &&
      error?.code === TURN_IN_PROGRESS &&
      attempt < MAX_IN_PROGRESS_RETRIES
    if (!retryable) throw error
    await wait(IN_PROGRESS_RETRY_MS)
    return runTurn(freshAuthFetch, conversationId, message, turnKey, attempt + 1)
  }
}

const useTailorConversation = (jobId) => {
  const { authFetch, freshAuthFetch } = useApi()

  const [conversationId, setConversationId] = useState('')
  const [status, setStatus] = useState('')
  const [messages, setMessages] = useState([])
  const [resume, setResume] = useState(null)
  const [changes, setChanges] = useState([])
  const [warnings, setWarnings] = useState([])
  const [version, setVersion] = useState(0)
  const [turnsLeft, setTurnsLeft] = useState(null)
  const [starting, setStarting] = useState(false)
  const [thinking, setThinking] = useState(false)
  const [failure, setFailure] = useState('')

  const conversationIdRef = useRef('')

  const start = useCallback(
    async (storedResume) => {
      setStarting(true)
      setFailure('')
      try {
        const started = await resumeService.start_tailor(
          freshAuthFetch,
          storedResume.id,
          jobId,
          activeLocale,
        )
        conversationIdRef.current = started.conversationId
        setConversationId(started.conversationId)
        setStatus(started.status)
        setMessages([{ role: 'assistant', content: started.greeting }])
        setResume(storedResume.resume ?? null)
        setChanges([])
        setWarnings([])
        setVersion(0)
        setTurnsLeft(null)
        return true
      } catch {
        setFailure('start')
        return false
      } finally {
        setStarting(false)
      }
    },
    [freshAuthFetch, jobId],
  )

  const resync = useCallback(async () => {
    const id = conversationIdRef.current
    if (!id) return
    try {
      const view = await resumeService.get_tailor_conversation(authFetch, id)
      if (!view) return
      setStatus(view.status)
      setTurnsLeft(view.turnsLeft ?? 0)
      if (view.currentResume) setResume(view.currentResume)
    } catch {
      setStatus(EXHAUSTED)
    }
  }, [authFetch])

  const send = useCallback(
    async (message) => {
      const text = message.trim()
      if (!text || thinking || !conversationIdRef.current) return

      setMessages((previous) => [...previous, { role: 'user', content: text }])
      setThinking(true)
      setFailure('')

      try {
        const answer = await runTurn(
          freshAuthFetch,
          conversationIdRef.current,
          text,
          newTurnKey(),
        )
        setMessages((previous) => [
          ...previous,
          { role: 'assistant', content: answer.reply, warnings: answer.warnings ?? [] },
        ])
        setResume(answer.resume)
        setChanges(answer.changes ?? [])
        setWarnings(answer.warnings ?? [])
        setVersion(answer.version ?? 0)
        setStatus(answer.status)
        setTurnsLeft(answer.turnsLeft ?? 0)
      } catch (error) {
        if (error?.status === CONFLICT && error?.code === CONVERSATION_EXHAUSTED) {
          setStatus(EXHAUSTED)
          setTurnsLeft(0)
          await resync()
        } else {
          setFailure('turn')
        }
      } finally {
        setThinking(false)
      }
    },
    [freshAuthFetch, resync, thinking],
  )

  const reset = useCallback(() => {
    conversationIdRef.current = ''
    setConversationId('')
    setStatus('')
    setMessages([])
    setResume(null)
    setChanges([])
    setWarnings([])
    setVersion(0)
    setTurnsLeft(null)
    setFailure('')
  }, [])

  return {
    conversationId,
    status,
    messages,
    resume,
    changes,
    warnings,
    version,
    turnsLeft,
    starting,
    thinking,
    failure,
    start,
    send,
    reset,
  }
}

export default useTailorConversation
