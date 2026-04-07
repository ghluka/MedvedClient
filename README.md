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

- **Aim Assist**\*
  - Smoothly rotates toward the nearest player within FOV
- **Auto Block**\*
  - Holds block with your sword to reduce incoming damage
- **Left Clicker**\*
  - Automatically left-clicks while holding the attack key
- **Right Clicker**\*
  - Automatically right-clicks while holding the use key
- **No Hit Delay**\*
  - Removes the 1.8 attack hit delay for 1.7-style PvP on 1.8 servers
- **Hit Select**\*
  - Restricts auto-clicks and attacks to the most effective moments
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
- **Knockback Delay**\*
  - Buffers all incoming packets when hit, freezing the world until the delay expires
- **Reach**\*
  - Extends your entity attack reach distance
- **Knockback Displacement**\*
  - Silently flicks rotation on each attack to displace knockback sideways
- **Backtrack**\*
  - Delays enemy position updates
</details>

<details>
    <summary>Movement</summary>

- **Sprint**\*
  - Automatically sprints
- **Timer**\*
  - Speeds up or slows down the game tick rate
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
- **Fast Place**\*
  - Removes the right-click delay when placing blocks
- **Auto Place**\*
  - Automatically places blocks when looking at a block surface
- **Bed Breaker**\*
  - Automatically breaks enemy beds
</details>

<details>
    <summary>HUD</summary>

- **Modules List**\*
  - Shows all enabled modules
- **Target HUD**\*
  - Displays target info and fight prediction when in combat
</details>

*module isn't available in release, but available in workflow artifact 