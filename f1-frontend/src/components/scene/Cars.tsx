import { useMemo } from 'react'
import { useRaceStore } from '../../store/raceStore'
import { positionToVector3 } from '../../utils/scenePosition'
import { CarMesh } from './CarMesh'

export function Cars(): React.JSX.Element {
  const positions = useRaceStore((state) => state.positions)
  const selectedDriver = useRaceStore((state) => state.selectedDriver)
  const setSelectedDriver = useRaceStore((state) => state.setSelectedDriver)

  const drivers = useMemo(() => Array.from(positions.values()), [positions])

  return (
    <group>
      {drivers.map((position) => (
        <CarMesh
          key={position.driverNumber}
          driverNumber={position.driverNumber}
          targetPosition={positionToVector3(position)}
          selected={selectedDriver === position.driverNumber}
          onSelect={setSelectedDriver}
        />
      ))}
    </group>
  )
}
