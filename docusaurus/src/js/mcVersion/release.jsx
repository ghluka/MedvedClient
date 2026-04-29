import { useEffect, useState } from 'react'

export default function ReleaseVersion() {
  const [version, setVersion] = useState(null)

  useEffect(() => {
    fetch('https://api.github.com/repos/ghluka/MedvedClient/releases/latest')
      .then(r => r.json())
      .then(async data => {
        const tag = data.tag_name
        if (!tag) return setVersion('unknown')

        const raw = await fetch(
          `https://raw.githubusercontent.com/ghluka/MedvedClient/${tag}/gradle.properties`
        )
        const text = await raw.text()
        const match = text.match(/^minecraft_version=(.+)$/m)
        setVersion(match ? match[1].trim() : 'unknown')
      })
      .catch(() => setVersion('unknown'))
  }, [])

  if (!version) return <span>loading...</span>
  return <span>{version}</span>
}