import React from 'react'
import ReactDOM from 'react-dom/client'

function App(): React.JSX.Element {
  return (
    <div style={{ color: 'white', background: '#0a0a0a', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <h1>F1 3D Race Visualizer — Coming Sprint 5</h1>
    </div>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
