# :bear: Medved Client

[![GitHub License](https://img.shields.io/github/license/ghluka/MedvedClient)](https://github.com/ghluka/MedvedClient/blob/main/LICENSE.md)
[![Total Downloads](https://img.shields.io/github/downloads/ghluka/MedvedClient/total)](https://github.com/ghluka/MedvedClient/releases/latest)

> [!IMPORTANT]  
> This is the nextgen version of Medved, for latest Fabric.
> 
> Go to [Camel Client](https://github.com/ghluka/CamelClient/tree/main) for the Forge 1.8.9 version.

A quality-of-life utility mod for the latest version of Minecraft on Fabric.

[![](/src/main/resources/assets/medved/icon2.png)](#)

## :gear: Installation

You can get the latest official build from the [latest release](https://github.com/ghluka/MedvedClient/releases/latest).

Alternatively, you can get the latest artifact by going to the [build workflows](https://github.com/ghluka/MedvedClient/actions/workflows/build.yml), going to the latest build, scrolling to the artifacts and downloading it!

## :scroll: Modules

<details>
  <summary>Combat</summary>

- **Left Clicker**\*
  - Automatically left-clicks while holding the attack key
- **Right Clicker**\*
  - Automatically right-clicks while holding the use key
- **Backtrack**\*
  - Delays enemy position updates
- **Knockback Delay**\*
  - Buffers all incoming packets when hit, freezing the world until the delay expires
- **No Hit Delay**\*
  - Removes the 1.8 attack hit delay for 1.7-style PvP on 1.8 servers
- **Reach**\*
  - Extends your entity attack reach distance
- **Velocity**\*
  - Modifies knockback you receive from attacks
  - Modes:
    - Reduce - reduce your knockback by a customizable %
    - Reverse - makes your knockback pull you forwards
    - Jump Reset - jumps when you get hit to take less knockback, perfect for prediction anticheats
- **Combo Tap**\*
  - Taps movement keys on attack to adjust velocity for better combos
  - Modes:
    - W Tap - suppresses W to create a tiny speed gap
    - S Tap - pulls you slightly back through the hit
    - Shift Tap - slows you down for tight combos
    - Sprint Reset - kills sprint but keeps W pressed
    - Jump Reset - jumps after hit and grounded
</details>

<details>
    <summary>Movement</summary>

- **Sprint**\*
  - Automatically sprints
</details>

<details>
    <summary>Player</summary>

- **Blink**\*
  - Buffers outgoing packets, making you appear frozen to the server
</details>

<details>
    <summary>World</summary>

- **Scaffold**\*
  - Automatically places blocks under you while walking
  - Modes:
    - Ninja - crouches at the end of blocks
</details>

<details>
    <summary>HUD</summary>

- **Modules List**\*
  - Shows all enabled modules
</details>

*module isn't available in release, but available in workflow artifact 