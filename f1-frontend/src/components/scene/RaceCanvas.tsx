import { Canvas } from '@react-three/fiber'
import { SCENE_COLORS } from '../../config/scene'
import { Cars } from './Cars'
import { CameraRig } from './CameraRig'
import { EmptyTrackOverlay } from './EmptyTrackOverlay'
import { TrackMesh } from './TrackMesh'

export function RaceCanvas(): React.JSX.Element {
  return (
    <div className="relative h-full w-full overflow-hidden rounded-lg border border-zinc-800">
      <Canvas
        className="h-full w-full"
        camera={{ position: [0, 45, 60], fov: 50, near: 0.1, far: 500 }}
        shadows
      >
        <color attach="background" args={[SCENE_COLORS.background]} />
        <ambientLight intensity={0.45} />
        <directionalLight castShadow intensity={1.1} position={[30, 50, 20]} />
        <TrackMesh />
        <Cars />
        <CameraRig />
      </Canvas>
      <EmptyTrackOverlay />
    </div>
  )
}
