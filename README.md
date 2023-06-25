# Sleep Warp

[![GitHub Release](https://img.shields.io/github/v/release/Giggitybyte/SleepWarp?include_prereleases)](https://github.com/Giggitybyte/SleepWarp/releases) [![Curseforge Download](https://cf.way2muchnoise.eu/full_579295_downloads.svg)](https://legacy.curseforge.com/minecraft/mc-mods/sleep-warp/files/all) [![Modrinth Download](https://img.shields.io/modrinth/dt/sleep-warp?label=modrinth&logo=modrinth)](https://modrinth.com/mod/sleep-warp/versions) [![Discord Server](https://img.shields.io/discord/385375030755983372.svg?label=discord)](https://discord.gg/UPKuVWgU4G)

**This is a Minecraft mod which will accelerate time while you sleep instead of skipping directly to day**<br/>
*When installed on a multiplayer server, the acceleration speed will scale exponentially based on the number of players asleep*

https://user-images.githubusercontent.com/19187704/153569152-1eb07fec-c93a-4e8f-b48b-46252ce628aa.mp4

**The world will also be ticked at the same rate to simulate the passage of time (configurable)**

https://user-images.githubusercontent.com/19187704/155882953-427244fa-9943-4088-bcc5-dcc5fb621240.mp4

https://user-images.githubusercontent.com/19187704/155883315-6bbac83b-b429-4f41-88bf-401733e7c20e.mp4

https://user-images.githubusercontent.com/19187704/155883317-07af47a1-cb13-4895-a577-612c656077ab.mp4

*SleepWarp can be used in either a single player, LAN, or dedicated server environment (not required on the client)*

[![Fabric Mod](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/fabric_46h.png)](https://fabricmc.net/use/) [![Forge Mod](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/forge_46h.png)](https://files.minecraftforge.net/net/minecraftforge/forge/) [![Quilt Mod](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/quilt_46h.png)](https://quiltmc.org/en/install/)

## History
I was looking for a decent multiplayer sleep mod back when Minecraft 1.18.1 was out and I found a few mods which accelerated time while the player slept; unfortunately I discovered that they were all abandoned and stuck on Minecraft 1.16.5.
I thought the idea was really cool and I wanted to play with that mechanic, so I decided to write up my own mod using Fabric for the current version of Minecraft at the time. 
Lucid<sup>([1](https://modrinth.com/mod/lucid), [2](https://github.com/nukeythenuke/lucid))</sup> and BetterSleepPlus<sup>([1](https://www.curseforge.com/minecraft/mc-mods/bettersleepplus), [2](https://github.com/TeamQuantumFusion/bettersleepplus))</sup> were the two of the abandoned mods which I liked the most and both were used as a reference while writing SleepWarp.

Shortly after the release for Minecraft 1.18.1 I ported SleepWarp to Minecraft 1.16.5 and 1.17.1 to give people the option to use a newer sleep mod on older Minecraft versions, and at the time I decided that would be the only release for those old Minecraft versions; new features would be only added for the most recent Minecraft version.
Around the same time I added the option to tick the world and block entities at the same accelerated rate while sleeping to give the impression that time had actually past.

However, I wasn't quite happy with the mod as a whole. There was no proper feedback while you were sleeping to let you know that you were sleeping; if you were inside a building or a cave it would be hard to gauge how long you'd be in the bed for.
I felt this was made worse with multiple players since you could end up just sitting in bed for a minute or more; it just didn't feel right.
Additionally, playing with the world simulation options enabled was not a fun gameplay experience. Ticking the world the way I did at the time worked well enough in a fresh single player world, but on older worlds and multiplayer worlds the performance was horrible and not even worth playing.

After a hiatus I rewrote the majority of the mod to address the above issues and added support for ModMenu and its in-game configuration button. 
Instead of ticking the world as a whole in one go on the server thread, the various portions of the world which can be ticked were done so sequentially and asynchronously on one of three threads; doing this greatly improved performance with the world simulation features enabled, enough for me to have most enabled by default. 
The rewrite was released as version 2.0.0 for Minecraft 1.19.2, 1.19.4, and 1.20.1. 

SleepWarp is now on version 3.0.0. This version is another rewrite (mostly) from the ground up which adds support for all modern mod loaders (Fabric, Forge, and Quilt) through Architectury, as well as improves to performance and mod compatibility.
