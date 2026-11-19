<div align="center">

<p align="center"><img src="https://cdn.modrinth.com/data/cached_images/793bef423b4ce9cf491985de4a0f0b020c715d6d_0.webp" alt="EssentialCraft 5 Unofficial" width="800"></p>

# EssentialCraft 5 Unofficial | 源质魔法5非官方版

[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.13+-orange?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![License](https://img.shields.io/badge/License-CC%20BY--SA%204.0-lightgrey?style=for-the-badge)](LICENSE)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/essentialcraft-5-unofficial) · [Modrinth](https://modrinth.com/mod/essentialcraft5unofficial)

</div>

<details open>
<summary><h2>English</h2></summary>

**EssentialCraft 5 Unofficial** is a port of [EssentialCraft 4 Unofficial](https://github.com/TheLMiffy1111/EssentialCraft4Unofficial) to Minecraft 1.20.1 Forge. The original was made by TheLMiffy1111 and contributors, based on Modbder's EssentialCraft 3. It is a magic mod built around one form of energy, MRU (Magical Radiation Units): machines gather it, Bound Gems carry it through the air, crystals and batteries store it, and it powers machines, tools, armor and rituals. Machines leak MRU as they work, and the clusters the leaks form slowly corrupt anyone who stays too close.

Everything from the original is ported, except what was removed on purpose (listed in the in-game appendix). New chapters, monsters and bosses carry the game past where the original stopped, and the research book has been rewritten as a step-by-step guide: a main line of 23 chapters, from your first elemental drops to the final boss, plus side chapters you can build whenever you like.

### Screenshots

![The Research Book: the main line, side chapters and your next step](https://cdn.modrinth.com/data/cached_images/96411df1f3e40852e7f36cb72517d01a8038030b.png)
*The Research Book: the main line, side chapters and your next step*

![An early base: Magician Table, Radiating Chamber and generators linked by Bound Gems](https://cdn.modrinth.com/data/cached_images/385c97e5b7c38466d54a46c7c54cbc72992d28c8.jpg)
*An early base: Magician Table, Radiating Chamber and generators linked by Bound Gems*

![Hoanna at night: a destroyed town under two moons and the MRU aurora](https://cdn.modrinth.com/data/cached_images/9f39cdb206736a3f4e91ce0d03d5e3d21f622eed_0.webp)
*Hoanna at night: a destroyed town under two moons and the MRU aurora*

![The Heart of Calamity: cracks in the ground give warning before lances of light strike](https://cdn.modrinth.com/data/cached_images/fe988ba829a2d50e809f93625c1ebee50e9c89db.jpg)
*The Heart of Calamity: cracks in the ground give warning before lances of light strike*

![The Verdict Blade's Judgment strikes the hostile creatures all around](https://cdn.modrinth.com/data/cached_images/1a7e92c79499b27d9168aab3609ae4ff4dc02707.jpg)
*The Verdict Blade's Judgment strikes the hostile creatures all around*

![Element Burst, an awakening of the Ember Elemental Sword](https://cdn.modrinth.com/data/cached_images/f421431cb1a8cd1e8961d2f51b1117ff8b8c0b6e.jpg)
*Element Burst, an awakening of the Ember Elemental Sword*

### Installation

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.13 or newer |
| Java | 17 |
| Curios API | 5.14 or newer (required) |
| JEI | optional: shows the mod's machine recipes |
| Botania | optional: the MRU Converter can output mana |

Install on both the client and the server. DummyCore and Baubles are no longer needed. Every player gets the Research Book on first join; hold **Ctrl** over an item's tooltip to open the page about it.

### Differences from EssentialCraft 4 Unofficial

The full list, including where every renamed item went, is in the Research Book under **Appendix · Changes from EC4**.

**New**

- Nine matrix ranks, broken through by winning trials at the Matrix Crucible.
- The Ley Projector, which turns MRU into ore inside the machine, and its upgrade, the Ley Condenser.
- The Inquisitorium chapter, modules and the Computer Terminal for the Computer Guy's Suit, Demonic Renown for demon trades, attunement for armor and weapons, and two final chapters ending with the Heart of Calamity.
- Hoanna expanded with new structures, the mana tide and ley storms; 13 new monsters and 6 new bosses in all.
- Optional JEI and Botania support, and an MRU Converter that outputs Forge Energy.

**Changed and removed**

- Baubles are worn in Curios slots (two rings, a necklace and a belt), and only one of each kind takes effect.
- Machines never dig or place blocks in the world: the Magical Quarry, activators, block breakers and the Magical Replanter are gone. Explosions from the mod never break blocks.
- Removed the machines that made mob farms trivial (Spawner Collector, Darkness Obelisk, Monster Duplicator, Monster Holding Device), the large-scale world tools (Biome Wand, Weather Controller, MRU Chunk Loader) and the Magical Inventory Manager network, which could duplicate or eat items.
- Void armor and the Computer Guy's Suit are no longer invulnerable while charged; they have real armor points and extra health instead.
- Corruption blocks and the Corruption Cleaner are not ported, since the original never switched corruption spreading on.
- Many bugs of the original are fixed, including a few that players relied on, such as the Moon Well running at full rate above its height limit.
- The original's integrations (CraftTweaker, Blood Magic, Thaumcraft, Tinkers' Construct, Waila) are gone, and so are the third-party music discs and sounds (see [CREDITS.md](CREDITS.md)).

### Building from source

Build with JDK 17 (on Windows, use `gradlew.bat`):

```bash
./gradlew build            # the jar is in build/libs/
./gradlew runData          # regenerates src/generated/resources (block states, models, loot tables, tags, lang)
python tools/guide/make_traditional.py --lang-only   # rebuilds the zh_tw / zh_hk lang files from zh_cn
./gradlew runGameTestServer   # optional: runs the GameTests
```

- Run `make_traditional.py --lang-only` after every `runData`, since the Traditional Chinese lang files are generated from the Simplified Chinese one. It needs Python 3 and `pip install opencc-python-reimplemented`.
- Run `runData` before `runGameTestServer`; the loot tables the tests check are generated.
- Curios, JEI and Botania are fetched from Maven; no jars have to be added by hand.

### License and credits

- Licensed under [CC BY-SA 4.0](LICENSE), the same license as the original works. You may share and adapt this mod, including in modpacks, as long as you give credit and share your adaptations under the same license.
- This work is a derivative of **EssentialCraft 4 Unofficial** and **DummyCore Unofficial** by **TheLMiffy1111**, with **mrAppleXZ**, **InfinityStudio** and other contributors, which are in turn derivatives of **EssentialCraft 3** and **DummyCore** by **Modbder**. All are licensed under CC BY-SA 4.0.
- The code was rewritten for 1.20.1 Forge by **Roinflam**. Most textures, models, sounds and translations come from EssentialCraft 4 Unofficial; full attribution and the list of changed assets are in [CREDITS.md](CREDITS.md).
- A few GUI backgrounds and recolored textures inherited from the original are based on Minecraft's own textures. Those parts belong to Mojang Studios and are not covered by CC BY-SA.
- This project is not affiliated with or endorsed by Modbder or TheLMiffy1111.

</details>

<details>
<summary><h2>中文</h2></summary>

**源质魔法5非官方版**是[源质魔法4非官方版](https://github.com/TheLMiffy1111/EssentialCraft4Unofficial)（TheLMiffy1111 等人）的 Minecraft 1.20.1 Forge 移植，源质魔法4非官方版本身源自 Modbder 的源质魔法3。这是一个围绕单一能量 MRU（魔法辐射单位）展开的魔法模组：机器收集 MRU，绑定宝石隔空输送 MRU，晶体和电池储存 MRU，再由 MRU 驱动机器、工具、盔甲与仪式。机器运转时会泄漏 MRU，泄漏形成的聚集体会逐渐腐化长时间待在附近的玩家。

原作的内容已全部移植，有意去掉的除外（清单见游戏内附录）。在此之上，新增的章节、怪物与首领把游戏延续到原作止步之处以后；研究手册也重新编写为循序渐进的教程：主线共 23 章，从第一批元素掉落物一直到最终首领，另有可随时制作的支线章节。

### 截图

![研究手册：主线、支线与下一步](https://cdn.modrinth.com/data/cached_images/96411df1f3e40852e7f36cb72517d01a8038030b.png)
*研究手册：主线、支线与下一步*

![前期基地：魔力工作台、辐射仓与用绑定宝石连接的产能器](https://cdn.modrinth.com/data/cached_images/385c97e5b7c38466d54a46c7c54cbc72992d28c8.jpg)
*前期基地：魔力工作台、辐射仓与用绑定宝石连接的产能器*

![海纳尔的夜晚：双月与 MRU 光幕下的城镇废墟](https://cdn.modrinth.com/data/cached_images/9f39cdb206736a3f4e91ce0d03d5e3d21f622eed_0.webp)
*海纳尔的夜晚：双月与 MRU 光幕下的城镇废墟*

![天灾之心：地面先裂出纹路示警，随后光束落下](https://cdn.modrinth.com/data/cached_images/fe988ba829a2d50e809f93625c1ebee50e9c89db.jpg)
*天灾之心：地面先裂出纹路示警，随后光束落下*

![裁决之刃的「断罪」：电弧同时打击周围的敌对生物](https://cdn.modrinth.com/data/cached_images/1a7e92c79499b27d9168aab3609ae4ff4dc02707.jpg)
*裁决之刃的「断罪」：电弧同时打击周围的敌对生物*

![余烬元素剑的觉醒「元素爆发」](https://cdn.modrinth.com/data/cached_images/f421431cb1a8cd1e8961d2f51b1117ff8b8c0b6e.jpg)
*余烬元素剑的觉醒「元素爆发」*

### 安装

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.13 及以上 |
| Java | 17 |
| Curios API | 5.14 及以上（必装） |
| JEI | 可选：显示本模组机器的配方 |
| 植物魔法（Botania） | 可选：MRU 转换器可输出魔力 |

客户端和服务端都需要安装，不再需要 DummyCore 和 Baubles。玩家第一次进入世界时会获得研究手册；在物品提示上按住 **Ctrl** 即可翻到介绍该物品的页面。

### 与源质魔法4非官方版的区别

完整清单（包括每件改名物品的去向）见研究手册的「附录 · 与原作的不同」。

**新增**

- 九个矩阵阶，在矩阵淬炼台赢得试炼后逐阶突破。
- 地脉投影仪：消耗 MRU 在机器内部投影出矿物，不挖掘世界里的方块；后期可升级为地脉凝聚器。
- 审判庭一章、宅男套装的模块与宅男终端、恶魔交易的契约之誉、盔甲与武器的同调，以及以天灾之心收尾的最后两章。
- 海纳尔新增多种结构以及魔力潮汐与地脉风暴；全模组共新增 13 种怪物和 6 个首领。
- 可选的 JEI 与植物魔法支持，以及可输出 Forge 能量的 MRU 转换器。

**调整与移除**

- 原作的 Baubles 饰品改为佩戴在 Curios 饰品栏中（两枚指环、一条项链、一条腰带），同一种饰品只有一件生效。
- 机器不再挖掘或放置世界里的方块：魔力采石场、执行器、方块破坏器和补种器已移除。本模组的爆炸一律不破坏方块。
- 移除了让刷怪塔过于简单的机器（刷怪笼采集者、黑暗方尖碑、怪物复制器、怪物约束装置）、大范围改动世界的工具（生态魔杖、天气控制器、MRU 区块加载器），以及可能复制或吞掉物品的魔力仓库管理器。
- 虚空套装与宅男套装不再「有电即无敌」，改为提供真正的护甲值和额外生命值。
- 腐化方块与腐化清除器未移植，因为原作从未开启腐化扩散。
- 修复了原作的大量问题，其中包括部分玩家当作窍门使用的问题，例如月亮井在高度上限以上仍满产。
- 移除了原作与其他模组的联动（CraftTweaker、血魔法、神秘时代、匠魂、Waila），版权属于第三方的唱片音乐与音效也未收录（见 [CREDITS.md](CREDITS.md)）。

### 从源码构建

使用 JDK 17 构建（Windows 下使用 `gradlew.bat`）：

```bash
./gradlew build            # 产物在 build/libs/
./gradlew runData          # 重新生成 src/generated/resources（方块状态、模型、战利品表、标签、语言文件）
python tools/guide/make_traditional.py --lang-only   # 由 zh_cn 重新生成 zh_tw / zh_hk 语言文件
./gradlew runGameTestServer   # 可选：运行 GameTest
```

- 繁体中文语言文件由简体中文生成，每次执行 `runData` 之后都需要重新运行 `make_traditional.py --lang-only`。该脚本需要 Python 3，并需先执行 `pip install opencc-python-reimplemented`。
- 运行 `runGameTestServer` 之前需先执行 `runData`，测试所检查的战利品表是生成的。
- Curios、JEI 与植物魔法均从 Maven 仓库获取，无需手动放入 jar。

### 许可与致谢

- 本模组采用 [CC BY-SA 4.0](LICENSE)（知识共享 署名-相同方式共享 4.0 国际）协议，与原作相同：可以分享、改编、加入整合包，但需署名，并以相同协议发布改编作品。
- 本作品改编自 **TheLMiffy1111** 及 **mrAppleXZ**、**InfinityStudio** 等贡献者的**源质魔法4非官方版**与 **DummyCore Unofficial**，二者又改编自 **Modbder** 的**源质魔法3**与 **DummyCore**，均采用 CC BY-SA 4.0 协议。
- 代码由 **Roinflam** 按 1.20.1 Forge 重写；贴图、模型、音效与译文大多取自源质魔法4非官方版，完整署名与改动素材清单见 [CREDITS.md](CREDITS.md)。
- 原作继承下来的少数界面底图和改色贴图以 Minecraft 原版贴图为底，这些部分属于 Mojang Studios，不在 CC BY-SA 授权范围内。
- 本项目与 Modbder、TheLMiffy1111 无关，也未获得其认可。

</details>
