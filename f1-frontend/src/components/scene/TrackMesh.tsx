import { useGLTF } from '@react-three/drei'
import { Component, Suspense, useEffect, useMemo, useRef, type ReactNode } from 'react'
import {
  BufferGeometry,
  DoubleSide,
  Float32BufferAttribute,
  Line,
  LineBasicMaterial,
  Mesh,
  MeshStandardMaterial,
} from 'three'
import {
  CENTER_LINE_MOVE_THRESHOLD,
  CENTER_LINE_Y_OFFSET,
  SCENE_COLORS,
  TRACK_SCALE,
} from '../../config/scene'
import { useTrackAssetUrl } from '../../hooks/useTrackAssetUrl'
import { useRaceStore } from '../../store/raceStore'
import type { Position } from '../../types/position'
import { positionToVector3 } from '../../utils/scenePosition'
import { resolveTrackAssetUrl } from '../../utils/trackAssetUrl'

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
  const loadUrl = useMemo(() => resolveTrackAssetUrl(url), [url])
  const { scene } = useGLTF(loadUrl)
  const trackMaterial = useMemo(
    () =>
      new MeshStandardMaterial({
        color: SCENE_COLORS.trackPlane,
        side: DoubleSide,
      }),
    [],
  )
  const clonedScene = useMemo(() => {
    const clone = scene.clone()
    clone.traverse((object) => {
      if (!(object instanceof Mesh)) {
        return
      }
      object.material = trackMaterial
    })
    return clone
  }, [scene, trackMaterial])

  useEffect(() => {
    return () => {
      trackMaterial.dispose()
      clonedScene.traverse((object) => {
        if (object instanceof Mesh) {
          object.geometry.dispose()
        }
      })
    }
  }, [clonedScene, trackMaterial])

  return (
    <primitive
      object={clonedScene}
      scale={[TRACK_SCALE, TRACK_SCALE, TRACK_SCALE]}
      rotation={[-Math.PI / 2, 0, 0]}
    />
  )
}

interface GlbTrackErrorBoundaryProps {
  fallback: ReactNode
  children: ReactNode
}

interface GlbTrackErrorBoundaryState {
  hasError: boolean
}

class GlbTrackErrorBoundary extends Component<GlbTrackErrorBoundaryProps, GlbTrackErrorBoundaryState> {
  state: GlbTrackErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): GlbTrackErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error): void {
    if (import.meta.env.DEV) {
      console.warn('TrackMesh: GLB load failed, using procedural fallback', error)
    }
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return this.props.fallback
    }
    return this.props.children
  }
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
  const trackAssetVersion = useRaceStore((state) => state.trackAssetVersion)
  const positions = useRaceStore((state) => state.positions)
  const resolvedTrackAssetUrl = useTrackAssetUrl(sessionKey, trackAssetVersion)

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
    const span = 2 * TRACK_SCALE * 1.4
    return { width: span, depth: span }
  }, [])

  return (
    <group>
      {resolvedTrackAssetUrl ? (
        <GlbTrackErrorBoundary
          key={resolvedTrackAssetUrl}
          fallback={<ProceduralTrackMesh planeSize={planeSize} />}
        >
          <Suspense fallback={<ProceduralTrackMesh planeSize={planeSize} />}>
            <GlbTrackMesh url={resolvedTrackAssetUrl} />
          </Suspense>
        </GlbTrackErrorBoundary>
      ) : (
        <ProceduralTrackMesh planeSize={planeSize} />
      )}
      {!resolvedTrackAssetUrl && <primitive object={centerLine} />}
    </group>
  )
}
