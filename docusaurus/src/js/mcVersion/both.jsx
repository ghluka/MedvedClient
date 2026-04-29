import { useEffect, useState } from 'react'

async function fetchRelease() {
  const res = await fetch('https://api.github.com/repos/ghluka/MedvedClient/releases/latest')
  const data = await res.json()
  const tag = data.tag_name
  if (!tag) return 'unknown'
  const raw = await fetch(`https://raw.githubusercontent.com/ghluka/MedvedClient/${tag}/gradle.properties`)
  const text = await raw.text()
  const match = text.match(/^minecraft_version=(.+)$/m)
  return match ? match[1].trim() : 'unknown'
}

async function fetchExperimental() {
  const res = await fetch('https://raw.githubusercontent.com/ghluka/MedvedClient/main/gradle.properties')
  const text = await res.text()
  const match = text.match(/^minecraft_version=(.+)$/m)
  return match ? match[1].trim() : 'unknown'
}

export default function FabricInfo() {
  const [release, setRelease] = useState(null)
  const [experimental, setExperimental] = useState(null)

  useEffect(() => {
    fetchRelease().then(setRelease)
    fetchExperimental().then(setExperimental)
  }, [])

  if (!release || !experimental) return <span>loading...</span>

  if (release === experimental) {
    return <span>You need Minecraft version {release} to run Grizzly Client.</span>
  }

  return <span>You need Minecraft version {release} to run Grizzly Client.
  <br></br>However, if you use the experimental version of Grizzly, you need {experimental}.</span>
}