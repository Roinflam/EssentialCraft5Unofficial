# Credits / 署名

EssentialCraft 5 Unofficial (源质魔法5非官方版) is an adaptation of the following works,
all licensed under the Creative Commons Attribution-ShareAlike 4.0 International License
(https://creativecommons.org/licenses/by-sa/4.0/):

- **EssentialCraft 3** and **DummyCore** by **Modbder**
  - https://github.com/Modbder/EssentialCraft3
  - https://github.com/Modbder/DummyCore
- **EssentialCraft 4 Unofficial** and **DummyCore Unofficial** by **TheLMiffy1111**,
  with **mrAppleXZ**, **InfinityStudio** and other contributors
  - https://github.com/TheLMiffy1111/EssentialCraft4Unofficial
  - https://github.com/TheLMiffy1111/DummyCoreUnofficial
  - https://www.curseforge.com/minecraft/mc-mods/essentialcraft-4-unofficial

The 1.20.1 port was made by **Roinflam**. The code was rewritten for Minecraft 1.20.1 Forge
(DummyCore is no longer used); textures, models, sounds and translations were taken from
EssentialCraft 4 Unofficial, with file paths renamed for 1.20.1. README.md summarizes the changes; the full list is in
the appendix of the in-game research guide ("Appendix · Changes from EC4").

This project is not affiliated with or endorsed by Modbder or TheLMiffy1111.

## Changes to the original assets / 对原作素材的修改

Per CC BY-SA 4.0, these assets were changed from the originals (the full list with notes is `tools/asset_map.tsv`,
rows `MODIFIED` and `DERIVED`; the generating scripts are in `tools/gen/`):

- `textures/block/firstworld/cactus_side.png`, `cactus_top.png`, `cactus_bottom.png` (Dread Cactus) and
  `textures/block/firstworld/dreaddirt.png` (Dread Dirt): brightened with a black-point lift in CIELCh, hue unchanged
  (average L* 17 → 32.5 on the cactus side, 10.5 → 25 on the dirt), so they no longer read as black blocks
  (`tools/gen/dread_textures.py`).
- `textures/block/voidstone_veins.png`, `textures/block/voidglass_veins.png` (new): glowing vein layers drawn over
  the original Void Stone / Void Glass textures, based on the original `voidstone.png`. Dyes now color only these veins;
  the original textures are drawn undyed underneath (`tools/gen/void_veins.py`).
- `textures/block/creative/*.png`, `textures/item/creative/*.png` (new, creative-only items): animated variants of the
  original Creative MRU Source / Creative ESPE Source block textures, the MRU Crystal and the Life Charm. The brown
  frame / pale gold ring is remapped to a gold ramp with a sweeping highlight, a rotating rainbow ring is drawn around the
  block core, and the crystal and charm gem cycle through rainbow hues; the heat / cold variants fill the core with the
  original chaos / frozen MRU flow textures (the chaos one recolored to lava tones) (`tools/gen/creative_textures.py`).
- `textures/block/icons/playerpentacle.png`, `textures/block/icons/demonicpentacle.png` (new): 32×32 item icons
  downscaled (Lanczos) from the original 512×512 Soul Pentacle / Demonic Pentacle textures
  (`tools/gen/demonic_pentacle_icon.py`).
- `models/block/*.obj` (19 machine models: Charging Chamber, Cold Distillator, Crystal Controller / Extractor / Former,
  Ender Generator, Natural Furnace, Heat Generator, Magical Enchantment Table, Magical Jukebox, Magical Repairer,
  Magician Table, Magmatic Smeltery, Matrix Destructor, Mithriline Furnace, MRU Coil Base, Potion Spreader,
  Radiating Chamber, Sun Ray Absorber): faces whose texture coordinates were collapsed to a line or a point in the
  original (side walls, plate edges, corner guards, recess walls), which showed up as stretched stripes, were given new
  texture coordinates unfolded from their neighbouring faces. Geometry, normals, the other faces and the textures are
  unchanged; faces that rendered as a single flat colour are kept as they were (`tools/gen/fix_obj_uv.py`).

按 CC BY-SA 4.0 注明：恐惧仙人掌三张贴图和恐惧泥土按原色相提亮（原来在沙漠里是一个个黑桩、恐惧之地一片漆黑）；
虚空石 / 虚空玻璃新加了一层以原贴图为底稿画的发光纹路，染料只染纹路，原贴图不染色垫在下面（原来整张乘色，16 种颜色都发黑、分不清）；
只放创造栏的「无尽」道具（`textures/block/creative/`、`textures/item/creative/`）以原作创造 MRU 源、创造 ESPE 源、MRU 水晶、生命护符的贴图为底稿做成动画：
棕框 / 淡金圈改成金色加流光，方块芯外加一圈转动的彩虹环，晶体和护符的宝石彩虹流动，冷热源的芯换成原作混沌 / 冰冻 MRU 的流动纹（混沌那张改成岩浆色）。
灵魂五芒星、恶魔五芒星的物品图标（`textures/block/icons/playerpentacle.png`、`demonicpentacle.png`）是原作 512×512 贴图缩成的 32×32 小图。
19 个机器 OBJ 模型（`models/block/*.obj`：充能仓、冷凝器、水晶控制器 / 提取器 / 成型器、末影产能器、自然熔炉、热力产能器、魔力附魔台、
魔力唱片机、魔力修复器、魔力工作台、岩浆冶炼炉、矩阵换能器、米斯莱熔炉、MRU 特斯拉线圈基座、药水扩散器、辐射仓、日光吸收器）里，
原作贴图坐标挤成一条线或一个点的面（侧壁、圆板厚边、护角块侧面、凹槽内壁，游戏里是一道道拉长的条纹）按相邻的面重新展开了贴图坐标；
几何、法线、其它面和贴图都没动，原来就是一块纯色的面保留原样（`tools/gen/fix_obj_uv.py`）。
明细见 `tools/asset_map.tsv` 的 MODIFIED / DERIVED 行，生成脚本在 `tools/gen/`。

本模组改编自上述作品，原作与本模组均按 CC BY-SA 4.0 授权。
1.20.1 移植由 Roinflam 完成：代码按 1.20.1 Forge 重写（不再依赖 DummyCore），
贴图、模型、音效与译文取自 EssentialCraft 4 Unofficial（为适应 1.20.1 改了文件路径）。
本项目与 Modbder、TheLMiffy1111 无关，也未获得他们的认可。

## Sounds / 音效

The demon sounds `sounds/sound/demon_*.ogg` (11 files: summon ×3, say ×4, depart, doom, trade ×2) and the ray gun sound
`sounds/sound/beam_start.ogg` are included unchanged.
They were added to EssentialCraft 3 by Modbder in version 4.5.1710.125 (2015), together with the demons themselves,
and are distributed with the rest of the original under CC BY-SA 4.0. The list with notes is in `tools/asset_map.tsv` (rows `SOUND`).

The holographic projection sounds `sounds/sound/hologram_rest.ogg`, `hologram_damage_melee.ogg`,
`hologram_damage_projectile.ogg`, `hologram_shutdown.ogg` and the orbital strike charge `sounds/sound/orbital_charge.ogg`
are included unchanged. They were added to EssentialCraft 3 by Modbder in May 2015, together with the holographic
projection boss, and are distributed with the rest of the original under CC BY-SA 4.0.

恶魔的 11 个音效（`sounds/sound/demon_*.ogg`：召唤 3、说话 4、离开、成交、交易 2）和射线枪的 `beam_start.ogg` 原样收录。
它们是 Modbder 在 EssentialCraft 3 的 4.5.1710.125 版（2015 年）随恶魔一起加入的，和原作其它内容一样按 CC BY-SA 4.0 授权。
全息投影的 4 个音效（`hologram_rest`、`hologram_damage_melee`、`hologram_damage_projectile`、`hologram_shutdown`）和轨道打击的充能声
`orbital_charge` 也原样收录，是 Modbder 在 2015 年 5 月随全息投影 Boss 一起加入的，同样按 CC BY-SA 4.0 授权。
明细见 `tools/asset_map.tsv` 的 SOUND 行。

## Music / 音乐

The Holographic Record and the holographic projection boss music (`sounds/records/hologram_battle.ogg`):
"Ropocolypse 2" Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/
(The original mod spells the title "Ropocalypse 2"; the file is included unchanged.)

全息唱片和全息投影 Boss 战音乐（`sounds/records/hologram_battle.ogg`）是 Kevin MacLeod 的「Ropocolypse 2」（incompetech.com），
按 CC BY 4.0 授权，原样收录（原作把曲名拼成 Ropocalypse 2）。

## Minecraft textures / 原版素材

Some textures inherited from the original are based on Minecraft's own textures. Those parts belong to Mojang Studios
and are not covered by this mod's CC BY-SA 4.0 license:

- GUI backgrounds: the Magical Chest (`textures/gui/magical_chest.png`, the vanilla large chest screen), the four weapon
  maker screens (`textures/gui/pistol_maker.png`, `rifle_maker.png`, `sniper_maker.png`, `gatling_maker.png`, based on the
  vanilla horse inventory screen), and the player inventory area of `textures/gui/void_chest.png`, `mim.png` and `mimscreen.png`.
- Recolors: the Elemental and Weak tool icons (`textures/item/tools/elemental_*.png`, `weak_*.png`, from the vanilla
  golden tools), `textures/block/chaos.png`, `textures/block/portal.png` and `textures/special/chaos.png` (from the vanilla
  Nether portal), and the Dread Cactus (from the vanilla cactus).

原作继承下来的部分贴图以 Minecraft 原版贴图为底：魔力箱界面（原版大箱子界面）、四种武器工坊界面（以原版马的物品栏界面为底）、
虚空箱等三个界面里的玩家物品栏部分，以及元素 / 虚弱工具图标（原版金工具改色）、混沌和传送门贴图（原版下界传送门改色）、恐惧仙人掌（原版仙人掌）。
这些部分属于 Mojang Studios，不在本模组的 CC BY-SA 4.0 授权范围内。

## Third-party assets not included / 未收录的第三方素材

The original mod ships some assets that are not the original authors' to license.
They are not included in this port:

- Music discs "Arstotzkan Anthem" (Papers, Please), "Secrets!" (The Stanley Parable),
  "Let's Be Friends" (Everlasting Summer)
- Sounds `lightning_hit` and `mrucu_noise` (freesfx.co.uk)
- Sounds `gen_electricity`, `heartbeat`, `tinnitus` and `deep_noise`: their origin could not be verified (they were added
  in the same commit as the two freesfx sounds; `heartbeat` carries the tags of a downloaded track). The events play
  vanilla sounds instead.
- Easter-egg item textures in `textures/items/secrets/` (references to other games; origin unverified). The six secret
  items themselves are not ported.

原作里有一些素材的版权不属于原作者，本移植没有收录：上面三张唱片、两个 freesfx 音效、
来源说不清的 `gen_electricity`、`heartbeat`、`tinnitus`、`deep_noise` 四个音效（和两个 freesfx 音效同一次提交加入，
`heartbeat` 还带着下载来的成品的标签；这几个音效事件改播原版声音）、
secrets 目录下致敬其它游戏的彩蛋贴图（来源未核实；六个秘密物品本身也不移植）。
