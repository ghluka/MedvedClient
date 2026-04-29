import { useEffect, useState } from 'react'

export default function ExperimentalVersion() {
  const [version, setVersion] = useState(null)

  useEffect(() => {
    fetch('https://raw.githubusercontent.com/ghluka/MedvedClient/main/gradle.properties')
      .then(r => r.text())
      .then(text => {
        const match = text.match(/^minecraft_version=(.+)$/m)
        setVersion(match ? match[1].trim() : 'unknown')
      })
      .catch(() => setVersion('unknown'))
  }, [])

  if (!version) return <span>loading...</span>
  return <span>{version}</span>
}