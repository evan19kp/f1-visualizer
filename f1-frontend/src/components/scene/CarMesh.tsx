import { useMemo, useRef } from 'react'
import { type Mesh, type Vector3 } from 'three'
import { useLerpedPosition } from '../../hooks/useLerpedPosition'
import { DRIVER_COLORS } from './driverColors'

interface CarMeshProps {
  driverNumber: number
  targetPosition: Vector3
  selected: boolean
  onSelect: (driverNumber: number) => void
}

export function CarMesh({
  driverNumber,
  targetPosition,
  selected,
  onSelect,
}: CarMeshProps): React.JSX.Element {
  const meshRef = useRef<Mesh>(null)
  useLerpedPosition(meshRef, targetPosition)

  const color = useMemo(
    () => DRIVER_COLORS[driverNumber % DRIVER_COLORS.length],
    [driverNumber],
  )

  return (
    <mesh
      ref={meshRef}
      castShadow
      onClick={(event) => {
        event.stopPropagation()
        onSelect(driverNumber)
      }}
    >
      <boxGeometry args={[1.8, 0.5, 0.9]} />
      <meshStandardMaterial
        color={color}
        emissive={selected ? color : '#000000'}
        emissiveIntensity={selected ? 0.35 : 0}
      />
    </mesh>
  )
}
