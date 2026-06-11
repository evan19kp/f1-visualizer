import { useEffect, useMemo, useState } from 'react'
import { BufferGeometry, Float32BufferAttribute, Line, LineBasicMaterial } from 'three'
import { API_URL } from '../../config/session'
import { TRACK_SCALE } from '../../config/scene'
import { useRaceStore } from '../../store/raceStore'
import { positionToVector3 } from '../../utils/scenePosition'

interface SessionBounds {
  minX: number
  maxX: number
  minY: number
  maxY: number
  minZ: number
  maxZ: number
}

export function TrackMesh(): React.JSX.Element {
  const sessionKey = useRaceStore((state) => state.sessionKey)
  const positions = useRaceStore((state) => state.positions)
  const [bounds, setBounds] = useState<SessionBounds | null>(null)

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
        if (response.ok) {
          setBounds((await response.json()) as SessionBounds)
        }
      } catch {
        /* derive layout from live positions when bounds unavailable */
      }
    })()

    return () => controller.abort()
  }, [sessionKey])

  const planeSize = useMemo(() => {
    if (bounds) {
      const width = Math.max((bounds.maxX - bounds.minX) * TRACK_SCALE, 20)
      const depth = Math.max((bounds.maxZ - bounds.minZ) * TRACK_SCALE, 20)
      return { width: width * 1.4, depth: depth * 1.4 }
    }
    return { width: 120, depth: 120 }
  }, [bounds])

  const centerLineGeometry = useMemo(() => {
    const drivers = Array.from(positions.values())
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
    const points = loop.map((position) => positionToVector3(position))

    const geometry = new BufferGeometry()
    const vertices = new Float32Array(points.length * 3)
    points.forEach((point, index) => {
      vertices[index * 3] = point.x
      vertices[index * 3 + 1] = 0.02
      vertices[index * 3 + 2] = point.z
    })
    geometry.setAttribute('position', new Float32BufferAttribute(vertices, 3))
    return geometry
  }, [positions])

  const centerLine = useMemo(() => {
    if (!centerLineGeometry) {
      return null
    }
    return new Line(centerLineGeometry, new LineBasicMaterial({ color: '#ffffff' }))
  }, [centerLineGeometry])

  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]} receiveShadow>
        <planeGeometry args={[planeSize.width, planeSize.depth]} />
        <meshStandardMaterial color="#333333" />
      </mesh>
      {centerLine && <primitive object={centerLine} />}
    </group>
  )
}
