# Elytra Flight Modes: FollowNetherTrails and FollowOverworldTrails

This fork adds two new Elytra flight modes: **FollowNetherTrails** and **FollowOverworldTrails**.

- **FollowNetherTrails**: Utilizes Baritone (Elytra) and the XaeroPlus database to follow nether trails.
- **FollowOverworldTrails**: Uses the XaeroPlus database to follow overworld trails.

## Setup Instructions

To use these modes, you need to download the modified XaeroPlus mod, which is a fork that [saves new chunks to the database as soon as they're discovered](https://github.com/WarriorLost/XaeroPlus/commit/ff2f8bdcc6bbd888ede06404d4084d2c70de6875).


### Downloads Links (for v1.21):

- [Meteor Client with Trails](https://github.com/WarriorLost/meteor-client/releases/tag/0.5.8-trails)
- [XaeroPlus Modified for Trails](https://github.com/WarriorLost/XaeroPlus/releases/tag/latest)


Once you have done that, follow these steps:

1. Select the ElytraFly Mode FollowNetherTrails or FollowOverworldTrails.
2. Enable the ElytraFly module.
3. If you are using FollowNetherTrails, you will need to enable the Baritone Elytra first and then when you are actually flying, enable ElytraFly.

## Or if you want to build the mods yourself:

### Meteor Client

1. Clone the Meteor Client repository:
   ```bash
   git clone git@github.com:WarriorLost/meteor-client.git
   ```

2. Run the build command:
   ```bash
   ./gradlew build
   ```
3. Copy the generated JAR file to your Minecraft mods folder:
   ```bash
   cp build/libs/meteor-client-0.5.8.jar /path/to/your/minecraft/mods/
   ```

### XaeroPlus

1. Clone the XaeroPlus repository:
   ```bash
   git clone git@github.com:WarriorLost/XaeroPlus.git
   git checkout 1.21
   ```

2. Run the build command:
   ```bash
   ./gradlew build
   ```
2. Copy the generated JAR file to your Minecraft mods folder:
   ```bash
   cp fabric/build/libs/XaeroPlus-2.24.5+fabric-1.21-WM1.39.0-MM24.5.0.jar /path/to/your/minecraft/mods/
   ```
