import { OrbitControls } from '@react-three/drei'
import { useFrame, useThree } from '@react-three/fiber'
import { useMemo } from 'react'
import { Vector3 } from 'three'
import { useRaceStore } from '../../store/raceStore'
import { positionToVector3 } from '../../utils/scenePosition'

const FOLLOW_OFFSET = new Vector3(0, 10, 18)
const TV_POSITIONS = [
  new Vector3(0, 55, 75),
  new Vector3(-70, 45, 20),
  new Vector3(65, 50, -50),
] as const

const CAMERA_LERP = 0.08

export function CameraRig(): React.JSX.Element | null {
  const cameraMode = useRaceStore((s) => s.cameraMode)
  const selectedDriver = useRaceStore((s) => s.selectedDriver)
  const positions = useRaceStore((s) => s.positions)
  const { camera } = useThree()

  const centroid = useMemo(() => {
    const point = new Vector3()
    if (positions.size === 0) {
      return point
    }
    for (const position of positions.values()) {
      point.add(positionToVector3(position))
    }
    point.divideScalar(positions.size)
    return point
  }, [positions])

  const tvPosition = useMemo(() => {
    if (positions.size === 0) {
      return TV_POSITIONS[0].clone()
    }
    let best = TV_POSITIONS[0]
    let bestDistance = Infinity
    for (const candidate of TV_POSITIONS) {
      const distance = candidate.distanceToSquared(centroid)
      if (distance < bestDistance) {
        bestDistance = distance
        best = candidate
      }
    }
    return best.clone()
  }, [centroid, positions])

  useFrame(() => {
    if (cameraMode === 'orbit') {
      return
    }

    if (cameraMode === 'follow') {
      const driverPosition =
        (selectedDriver != null ? positions.get(selectedDriver) : undefined) ??
        positions.values().next().value
      if (!driverPosition) {
        return
      }
      const target = positionToVector3(driverPosition)
      const desired = target.clone().add(FOLLOW_OFFSET)
      camera.position.lerp(desired, CAMERA_LERP)
      camera.lookAt(target)
      return
    }

    camera.position.lerp(tvPosition, CAMERA_LERP)
    camera.lookAt(centroid)
  })

  if (cameraMode === 'orbit') {
    return <OrbitControls makeDefault enableDamping dampingFactor={0.08} />
  }

  return null
}
