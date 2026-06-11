import { useFrame } from '@react-three/fiber'
import { type RefObject, useRef } from 'react'
import { type Object3D, Vector3 } from 'three'
import { LERP_FACTOR } from '../config/scene'

export function useLerpedPosition(
  objectRef: RefObject<Object3D>,
  target: Vector3,
  lerpFactor = LERP_FACTOR,
): RefObject<Vector3> {
  const positionRef = useRef(new Vector3().copy(target))

  useFrame(() => {
    positionRef.current.lerp(target, lerpFactor)
    objectRef.current?.position.copy(positionRef.current)
  })

  return positionRef
}
