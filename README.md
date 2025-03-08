[![GitHub Pre-Release](https://img.shields.io/github/release-pre/CozmycDev/PK-HealingSpores.svg)](https://github.com/CozmycDev/PK-HealingSpores/releases)
[![Github All Releases](https://img.shields.io/github/downloads/CozmycDev/PK-HealingSpores/total.svg)](https://github.com/CozmycDev/PK-HealingSpores/releases)
![Size](https://img.shields.io/github/repo-size/CozmycDev/PK-HealingSpores.svg)

# HealingSpores Ability for ProjectKorra

This is an addon ability for the [ProjectKorra](https://projectkorra.com/) plugin for Spigot Minecraft servers. This ability allows Plantbenders to provide temporary AoE healing. This concept was designed by
LuxaelNi.

## Description

**HealingSpores** is a Plant ability that enables players to create a special temporary spore blossom, optionally using wheat seeds. In the presence of this spore blossom, players receive regeneration for a configured amount of time.

## Instructions

- **Preparation**: With wheat seeds in your inventory (configurable) and HealingSpores selected, hold shift until a visual indicator appears near your right hand.
- **Ready**: Once the indicator is present, it will be gray or green depending on if the block you are looking at is valid, usually leaves. (configurable)
- **Activate**: Release shift while the indicator is green to activate the AoE healing affect.

## Installation

1. Download the latest `healingspores.jar` file from [releases](https://github.com/CozmycDev/PK-HealingSpores/releases).
2. Place the `healingspores.jar` file in the `./plugins/ProjectKorra/Abilities` directory.
3. Restart your server or reload the ProjectKorra plugin with `/b reload` to enable the ability.

## Compatibility

- **Minecraft Version**: Tested and working on MC 1.20.4+
- **ProjectKorra Version**: Tested and working on PK 1.11.2+

## Configuration

The configuration for this ability can be found in the `ProjectKorra` configuration file (`config.yml`). Below are the
default settings:

```yaml
ExtraAbilities:
  Cozmyc:
    HealingSpores:
      Cooldown: 22000  // time in ms
      ChargeTime: 4000  // time required to hold shift
      UsesSeeds: true  // whether wheat seeds are required
      RequiredSeeds: 1  // how many wheat seeds per use
      UseDistance: 10  // how far you can be from the target block
      HealingRadius: 22  // the distance in which AoE healing works
      HealingLevel: 10  // the MC potion level of regeneration
      Duration: 5000  // time in ms before the spore blossom fades and ability ends
      AllowSporeBlossomDrop: false  // whether its possible to drop the spore blossom block itself
      CustomValidSpawnMaterials:  // other valid blocks healingspores can target (ALL leaves are always enabled, even modded)
        - DIRT
```

## Development

- **Authors**: LuxaelNI, Cozmyc