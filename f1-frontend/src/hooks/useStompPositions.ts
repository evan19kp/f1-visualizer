import { Client, StompSubscription } from '@stomp/stompjs'
import { useEffect } from 'react'
import SockJS from 'sockjs-client'
import { WS_URL } from '../config/session'
import { useRaceStore } from '../store/raceStore'
import type { Position } from '../types/position'

export function useStompPositions(sessionKey: string): void {
  const updatePositions = useRaceStore((s) => s.updatePositions)
  const setConnectionStatus = useRaceStore((s) => s.setConnectionStatus)

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const isMounted = { current: true }
    let subscription: StompSubscription | undefined

    const safeSetStatus = (status: 'connecting' | 'connected' | 'disconnected') => {
      if (isMounted.current) {
        setConnectionStatus(status)
      }
    }

    safeSetStatus('connecting')

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,
      onConnect: () => {
        if (!isMounted.current) {
          return
        }
        safeSetStatus('connected')
        subscription = client.subscribe(`/topic/sessions/${sessionKey}/positions`, (message) => {
          try {
            const batch = JSON.parse(message.body) as Position[]
            updatePositions(batch)
          } catch (error) {
            console.error('Failed to parse STOMP position message:', error)
          }
        })
      },
      onDisconnect: () => safeSetStatus('disconnected'),
      onStompError: () => safeSetStatus('disconnected'),
      onWebSocketClose: () => safeSetStatus('disconnected'),
    })

    client.activate()

    return () => {
      isMounted.current = false
      subscription?.unsubscribe()
      client.onConnect = () => {}
      client.onDisconnect = () => {}
      client.onStompError = () => {}
      client.onWebSocketClose = () => {}
      client.deactivate()
      safeSetStatus('disconnected')
    }
  }, [sessionKey, setConnectionStatus, updatePositions])
}
