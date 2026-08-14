import { useGLTF } from '@react-three/drei'
import { render } from '@testing-library/react'
import { BufferGeometry, Mesh, MeshStandardMaterial, Scene } from 'three'
import { describe, expect, it, vi } from 'vitest'
import { GlbTrackMesh } from './TrackMesh'

vi.mock('@react-three/drei', () => ({
  useGLTF: vi.fn(),
}))

describe('GlbTrackMesh', () => {
  it('does not dispose geometry owned by the useGLTF cache when unmounted', () => {
    const geometry = new BufferGeometry()
    const cachedMaterial = new MeshStandardMaterial()
    const cachedScene = new Scene()
    cachedScene.add(new Mesh(geometry, cachedMaterial))
    const geometryDispose = vi.spyOn(geometry, 'dispose')
    const materialDispose = vi.spyOn(cachedMaterial, 'dispose')
    vi.mocked(useGLTF).mockReturnValue(
      { scene: cachedScene } as unknown as ReturnType<typeof useGLTF>,
    )

    const { unmount } = render(<GlbTrackMesh url="https://example.test/track.glb" />)
    unmount()

    expect(geometryDispose).not.toHaveBeenCalled()
    expect(materialDispose).not.toHaveBeenCalled()
  })
})
