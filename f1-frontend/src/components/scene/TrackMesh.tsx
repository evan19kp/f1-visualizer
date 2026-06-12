import { useGLTF } from '@react-three/drei'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  BufferGeometry,
  Float32BufferAttribute,
  Line,
  LineBasicMaterial,
} from 'three'
import { API_URL } from '../../config/session'
import {
  CENTER_LINE_MOVE_THRESHOLD,
  CENTER_LINE_Y_OFFSET,
  SCENE_COLORS,
  TRACK_SCALE,
} from '../../config/scene'
import { useRaceStore } from '../../store/raceStore'
import type { Position } from '../../types/position'
import { positionToVector3 } from '../../utils/scenePosition'

interface SessionBounds {
  minX: number
  maxX: number
  minY: number
  maxY: number
  minZ: number
  maxZ: number
}

interface TrackAssetResponse {
  url: string
  circuitSlug: string
}

function isSessionBounds(value: unknown): value is SessionBounds {
  if (!value || typeof value !== 'object') {
    return false
  }
  const bounds = value as Record<string, unknown>
  return ['minX', 'maxX', 'minY', 'maxY', 'minZ', 'maxZ'].every(
    (key) => typeof bounds[key] === 'number' && Number.isFinite(bounds[key] as number),
  )
}

function isTrackAssetResponse(value: unknown): value is TrackAssetResponse {
  if (!value || typeof value !== 'object') {
    return false
  }
  const payload = value as Record<string, unknown>
  return typeof payload.url === 'string' && typeof payload.circuitSlug === 'string'
}

function buildCenterLineVertices(drivers: Position[]): Float32Array | null {
  if (drivers.length < 2) {
    return null
  }

  const centroidX = drivers.reduce((sum, p) => sum + p.x, 0) / drivers.length
  const centroidZ = drivers.reduce((sum, p) => sum + p.z, 0) / drivers.length

  const sorted = [...drivers].sort(
    (a, b) =>
      Math.atan2(a.z - centroidZ, a.x - centroidX) -
      Math.atan2(b.z - centroidZ, b.x - centroidX),
  )
  const loop = [...sorted, sorted[0]]
  const vertices = new Float32Array(loop.length * 3)

  loop.forEach((position, index) => {
    const point = positionToVector3(position)
    vertices[index * 3] = point.x
    vertices[index * 3 + 1] = CENTER_LINE_Y_OFFSET
    vertices[index * 3 + 2] = point.z
  })

  return vertices
}

function hasSignificantMovement(current: Float32Array, previous: Float32Array): boolean {
  if (current.length !== previous.length) {
    return true
  }
  for (let i = 0; i < current.length; i++) {
    if (Math.abs(current[i] - previous[i]) > CENTER_LINE_MOVE_THRESHOLD) {
      return true
    }
  }
  return false
}

function updateCenterLineGeometry(geometry: BufferGeometry, vertices: Float32Array): void {
  const position = geometry.getAttribute('position') as Float32BufferAttribute | undefined
  if (position && position.count === vertices.length / 3) {
    position.array.set(vertices)
    position.needsUpdate = true
    return
  }
  geometry.setAttribute('position', new Float32BufferAttribute(vertices, 3))
}

function GlbTrackMesh({ url }: { url: string }): React.JSX.Element {
  const { scene } = useGLTF(url)
  const clonedScene = useMemo(() => scene.clone(), [scene])

  return (
    <primitive
      object={clonedScene}
      scale={[TRACK_SCALE, TRACK_SCALE, TRACK_SCALE]}
      rotation={[-Math.PI / 2, 0, 0]}
    />
  )
}

function ProceduralTrackMesh({
  planeSize,
}: {
  planeSize: { width: number; depth: number }
}): React.JSX.Element {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]} receiveShadow>
      <planeGeometry args={[planeSize.width, planeSize.depth]} />
      <meshStandardMaterial color={SCENE_COLORS.trackPlane} />
    </mesh>
  )
}

export function TrackMesh(): React.JSX.Element {
  const sessionKey = useRaceStore((state) => state.sessionKey)
  const positions = useRaceStore((state) => state.positions)
  const [bounds, setBounds] = useState<SessionBounds | null>(null)
  const [trackAssetUrl, setTrackAssetUrl] = useState<string | null>(null)

  const centerLine = useMemo(
    () =>
      new Line(
        new BufferGeometry(),
        new LineBasicMaterial({ color: SCENE_COLORS.centerLine }),
      ),
    [],
  )

  const lastDriverCountRef = useRef(0)
  const lastVerticesRef = useRef<Float32Array | null>(null)
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      centerLine.geometry.dispose()
      ;(centerLine.material as LineBasicMaterial).dispose()
    }
  }, [centerLine])

  useEffect(() => {
    if (!sessionKey) {
      setTrackAssetUrl(null)
      return
    }

    const controller = new AbortController()
    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/track-asset`, {
          signal: controller.signal,
        })
        if (response.status === 404) {
          setTrackAssetUrl(null)
          return
        }
        if (!response.ok) {
          if (import.meta.env.DEV) {
            console.warn(
              `TrackMesh: track-asset request failed (${response.status}) for session ${sessionKey}`,
            )
          }
          setTrackAssetUrl(null)
          return
        }

        const payload: unknown = await response.json()
        if (isTrackAssetResponse(payload)) {
          setTrackAssetUrl(payload.url)
          return
        }

        if (import.meta.env.DEV) {
          console.error(`TrackMesh: invalid track-asset payload for session ${sessionKey}`, payload)
        }
        setTrackAssetUrl(null)
      } catch (error) {
        if (controller.signal.aborted) {
          return
        }
        if (import.meta.env.DEV) {
          console.error(`TrackMesh: failed to fetch track-asset for session ${sessionKey}`, error)
        }
        setTrackAssetUrl(null)
      }
    })()

    return () => controller.abort()
  }, [sessionKey])

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const controller = new AbortController()
    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/bounds`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          if (import.meta.env.DEV) {
            console.warn(
              `TrackMesh: bounds request failed (${response.status}) for session ${sessionKey}`,
            )
          }
          return
        }

        const payload: unknown = await response.json()
        if (isSessionBounds(payload)) {
          setBounds(payload)
          return
        }

        if (import.meta.env.DEV) {
          console.error(`TrackMesh: invalid bounds payload for session ${sessionKey}`, payload)
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return
        }
        if (import.meta.env.DEV) {
          console.error(`TrackMesh: failed to fetch bounds for session ${sessionKey}`, error)
        }
      }
    })()

    return () => controller.abort()
  }, [sessionKey])

  useEffect(() => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current)
    }

    rafRef.current = requestAnimationFrame(() => {
      rafRef.current = null
      const drivers = Array.from(positions.values())
      const driverCount = drivers.length
      const vertices = buildCenterLineVertices(drivers)

      if (!vertices) {
        centerLine.visible = false
        lastDriverCountRef.current = 0
        lastVerticesRef.current = null
        return
      }

      const countChanged = driverCount !== lastDriverCountRef.current
      const lastVertices = lastVerticesRef.current
      const moved =
        lastVertices === null || countChanged || hasSignificantMovement(vertices, lastVertices)

      if (!moved) {
        return
      }

      updateCenterLineGeometry(centerLine.geometry, vertices)
      centerLine.visible = true
      lastDriverCountRef.current = driverCount
      lastVerticesRef.current = vertices
    })

    return () => {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current)
      }
    }
  }, [positions, centerLine])

  const planeSize = useMemo(() => {
    if (bounds) {
      const width = Math.max((bounds.maxX - bounds.minX) * TRACK_SCALE, 20)
      const depth = Math.max((bounds.maxZ - bounds.minZ) * TRACK_SCALE, 20)
      return { width: width * 1.4, depth: depth * 1.4 }
    }
    return { width: 120, depth: 120 }
  }, [bounds])

  return (
    <group>
      {trackAssetUrl ? (
        <GlbTrackMesh url={trackAssetUrl} />
      ) : (
        <ProceduralTrackMesh planeSize={planeSize} />
      )}
      <primitive object={centerLine} />
    </group>
  )
}
