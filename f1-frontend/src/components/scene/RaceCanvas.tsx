import { Canvas } from '@react-three/fiber'
import { Suspense } from 'react'
import { SCENE_COLORS } from '../../config/scene'
import { Cars } from './Cars'
import { OrbitCamera } from './OrbitCamera'
import { TrackMesh } from './TrackMesh'

export function RaceCanvas(): React.JSX.Element {
  return (
    <div className="h-full w-full overflow-hidden rounded-lg border border-zinc-800">
      <Canvas
        className="h-full w-full"
        camera={{ position: [0, 45, 60], fov: 50, near: 0.1, far: 500 }}
        shadows
      >
        <color attach="background" args={[SCENE_COLORS.background]} />
        <ambientLight intensity={0.45} />
        <directionalLight castShadow intensity={1.1} position={[30, 50, 20]} />
        <Suspense fallback={null}>
          <TrackMesh />
          <Cars />
          <OrbitCamera />
        </Suspense>
      </Canvas>
    </div>
  )
}
