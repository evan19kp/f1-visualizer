import { OrbitControls } from '@react-three/drei'

export function OrbitCamera(): React.JSX.Element {
  return <OrbitControls makeDefault enableDamping dampingFactor={0.08} />
}
