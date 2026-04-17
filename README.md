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

- **Kill Aura**\*
  - Silently aims and attacks enemies when they enter your range
  - Modes:
    - APS - sends attack packets per second
    - CPS - silently aims and autoclicks onto a player, perfect for 1.8
    - Sequential - silently aims and times attacks after the previous one, perfect for 1.9+
  - Targeting:
    - Single - locks onto the closest target and attacks until they die or get out of range
    - Switch - switches to attack players based on the best candidate
    - Multi - requires APS mode, attacks multiple players at a time 
- **Aim Assist**
  - Smoothly rotates toward the nearest player within FOV
- **Left Clicker**
  - Automatically left-clicks while holding the attack key
- **Right Clicker**
  - Automatically right-clicks while holding the use key
- **No Hit Delay**
  - Removes the 1.8 attack hit delay for 1.7-style PvP on 1.8 servers
- **Block Hit**
  - Blocks with your sword to reduce incoming damage when autoclicking
- **Hit Select**
  - Restricts auto-clicks and attacks to the most effective moments
  - Modes:
    - Pause - pauses the autoclicker but allows for manual clicks to go through
    - Active - cancel attack packets directly, good for auto-clicker or manual clicks
- **Velocity**
  - Modifies knockback you receive from attacks
  - Modes:
    - Reduce - reduce your knockback by a customizable %
    - Reverse - makes your knockback pull you forwards
    - Cancel - completely cancels knockback packets
    - Jump Reset - jumps when you get hit to take less knockback
    - Delay - customizable latency that happens when you get hit, doesn't reduce knockback but delays it
- **Combo Tap**
  - Taps movement keys on attack to adjust velocity for better combos
  - Modes:
    - W Tap - suppresses W to create a tiny speed gap
    - S Tap - pulls you slightly back through the hit
    - Shift Tap - slows you down for tight combos
    - Sprint Reset - kills sprint but keeps W pressed
    - Jump Reset - jumps after hit and grounded
- **Knockback Delay**
  - Buffers all incoming packets when hit, freezing the world until the delay expires
- **Reach**
  - Extends your entity attack reach distance
- **Hit Flick**\*
  - Silently flicks rotation on each attack to displace knockback sideways
- **Backtrack**
  - Delays enemy position updates to hit players at their previous position
  - Modes:
    - Manual - directly lets you hit players at previous positions, similar to reach
    - Lag - modifies your connection to the server by increasing latency to hit players
- **Criticals**\*
  - Forces critical hits on attacks
  - Modes:
    - No Ground - works for 1.8 servers
    - Packet - works for 1.9+ servers
    - Jump - jumps legit
</details>

<details>
    <summary>Movement</summary>

- **Sprint**
  - Automatically sprints
- **Speed**\*
  - Increases your movement speed
  - Modes:
    - On Ground
    - Bhop
    - Low Hop
- **Flight**\*
  - Allows you to fly or glide in the air
  - Modes:
    - Vanilla
    - Creative
    - Air Hop
    - Glide
- **Timer**
  - Speeds up or slows down the game tick rate
- **No Fall**\*
  - Prevents taking fall damage
  - Modes:
    - Packet
    - Spoof
    - Distance
</details>

<details>
    <summary>Player</summary>

- **Fake Lag**
  - Delays outgoing packets to simulate real network latency
  - Modes:
    - Dynamic - lags you when another player is nearby, making you harder to hit while you can still reach them
    - Repel - after each attack lands, you lag until the hit window closes so you can get double hits
    - Latency - constant lag
- **Blink**
  - Buffers outgoing packets, making you appear frozen to the server
- **Client Brand**
  - Spoofs the client brand sent to the server on join
</details>

<details>
    <summary>World</summary>

- **Scaffold**
  - Automatically places blocks under you while walking
  - Modes:
    - Ninja - crouches at the end of blocks
- **Clutch**
  - Bridges blocks back to safety when knocked off an edge (also can be used as telly bridge)
  - Triggers:
    - Always
    - On void
    - On lethal fall
    - Custom fall distance
- **Fast Place**
  - Removes the right-click delay when placing blocks
- **Auto Place**
  - Automatically places blocks when looking at a block surface
- **Bed Breaker**
  - Automatically breaks enemy beds
- **Chest Aura**\*
  - Automatically opens nearby chests with legit rotations. Also works as a Powder Chest Aura for Skyblock with an optional drill swap
</details>

<details>
    <summary>HUD</summary>

- **Modules List**
  - Shows all enabled modules
- **Target HUD**
  - Displays target info and fight prediction when in combat
- **Block Counter**
  - Displays hotbar block count for the item in hand
</details>

<details>
    <summary>Other</summary>

- **Click Gui**
- **Colour**
  - Global accent color for GUI and HUD elements
- **Font**
  - Customize the font used in GUI and HUD elements
- **Rotations**
  - Global aim interpolation settings for all rotation modules
- **Target Filter**\*
  - Filters targets across combat modules (anti-bot, teams, etc)
</details>

*module isn't available in release, but available in workflow artifact