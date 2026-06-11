import { Client } from '@stomp/stompjs'
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

    setConnectionStatus('connecting')

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/sessions/${sessionKey}/positions`, (message) => {
          const batch = JSON.parse(message.body) as Position[]
          updatePositions(batch)
        })
      },
      onDisconnect: () => setConnectionStatus('disconnected'),
      onStompError: () => setConnectionStatus('disconnected'),
      onWebSocketClose: () => setConnectionStatus('disconnected'),
    })

    client.activate()

    return () => {
      client.deactivate()
      setConnectionStatus('disconnected')
    }
  }, [sessionKey, setConnectionStatus, updatePositions])
}
