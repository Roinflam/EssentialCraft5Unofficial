#!/usr/bin/env python3
"""
从原作（EC4U 1.12 分支）导入资源，改成 1.20.1 能用的路径。

    python tools/import_assets.py textures            # 导入全部贴图（可重复跑，结果一致）
    python tools/import_assets.py obj chargingchamber # 导入 OBJ 模型并改写 MTL 里的贴图引用（models/block/ 下）
    python tools/import_assets.py obj item/board      # 不在 models/block/ 的写子目录（原作 models/item/board.obj）
    python tools/import_assets.py model moonwell moon_well minecraft:translucent
                                                      # 导入 JSON 方块模型（改贴图引用、父模型，可加渲染类型）
    python tools/import_assets.py lang                # 原作 .lang 转成 tools/legacy_lang/*.json（datagen 按原键取译文）
    python tools/import_assets.py sound sound/hologram_rest records/hologram_battle
                                                      # 原样拷音效（sounds/<路径>.ogg，路径不变）；收不收先查来源，再记 SOUND 行

为什么直接读 git 对象而不是工作区文件：
  原作仓库里有 168 对只差大小写的路径（如 ct/magicPlating 与 ct/magicplating），
  Windows 上克隆只会留下其中一份。这里按 git 对象读，并核对同组的内容是否真的一样。

路径规则（1.20.1 的 ResourceLocation 只允许小写）：
  - 全部转小写
  - textures/blocks   -> textures/block    （1.19.3 起方块图集只扫 block/ 和 item/）
  - textures/items    -> textures/item
  - textures/models   -> textures/block/model （OBJ 模型贴图必须进方块图集）
  - textures/entities -> textures/entity
  - 其它目录（gui、hud、special）保持原名

排除（写进 tools/asset_map.tsv 的 EXCLUDED 行，方便以后查）：
  - textures/items/secrets/：致敬其它游戏的彩蛋贴图，来源未核实，要重画
  - textures/blocks/ct/：EC3 遗留的 CTM 分边贴图，1.20.1 不用 CTM
  - textures/blocks/*_ct.png、*_ct.png.mcmeta、*_ct_tex.png：给 ConnectedTexturesMod 用的连接纹理（不做联动，第五批 D5 删掉）

导入后又改过的贴图列在 MODIFIED 表里：重新导入时不覆盖（原图改了就重跑表里写的那个脚本），asset_map.tsv 记 MODIFIED 行。
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
SOURCE_REPO = os.path.join(os.path.dirname(PROJECT), 'EssentialCraft4Unofficial')
SRC_ASSETS = 'src/main/resources/assets/essentialcraft/'
OLD_NS = 'essentialcraft'
NEW_NS = 'essentialcraft5'
DST_ASSETS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', NEW_NS)
MAP_FILE = os.path.join(HERE, 'asset_map.tsv')

DIR_RENAMES = [
    ('textures/blocks/', 'textures/block/'),
    ('textures/items/', 'textures/item/'),
    ('textures/models/', 'textures/block/model/'),
    ('textures/entities/', 'textures/entity/'),
]
# 贴图以外、或不是一对一照搬的资源（模型、从图集切出来的图、音效）：手工维护，
# import_textures 重写 asset_map.tsv 时原样追加在末尾（第六批 6a 起）。kind：MODEL / DERIVED / EXCLUDED / SOUND（照搬的贴图是 TEXTURE，导入后改过的是 MODIFIED；
# SOUND 是原样拷过来的音效，第七批起记；之前导入的 beam_start 第八批补记，gen_electricity / heartbeat / tinnitus 第八批 D9 改成原版声音、记 EXCLUDED）
EXTRA_ROWS = [
    'MODEL\tmodels/block/levitator.json\tmodels/block/levitator.json\t第六批 6a M7 已导入（python tools/import_assets.py model levitator levitator，实心层；贴图改 block/fortifiedstone、minecraft:block/beacon、block/magicplatingblock）',
    'MODEL\tmodels/block/spreader.json\tmodels/block/spreader.json\t第六批 6a M7 已导入（python tools/import_assets.py model spreader spreader，实心层；贴图 block/magicplatingblock）',
    'DERIVED\ttextures/special/potions.png\ttextures/mob_effect/radiation.png\t第六批 6a M4 从效果图集切：像素 (72, 234)~(90, 252)（(4, 2) 格，切法同 mru_corruption.png）',
    'EXCLUDED\tsounds/sound/deep_noise.ogg\t\t来源核实不了（46 秒立体声环境录音、没有 Vorbis 注释，和两个 freesfx 音效同一次提交 ceabf8b 加入，原作从没播放过）；sound.deep_noise 定义改指原版 entity.generic.explode（第六批 6a F6a）',
    'EXCLUDED\tsounds/sound/lightning_hit.ogg\t\tfreesfx.co.uk（Vorbis 注释 title=http://www.freesfx.co.uk），条款禁止再分发；sound.lightning_hit 指原版事件（第四批 F4-14）',
    'EXCLUDED\tsounds/sound/mrucu_noise.ogg\t\tfreesfx.co.uk（同上）',
    'DERIVED\ttextures/blocks/voidstone.png\ttextures/block/voidstone_veins.png\t第六批 6a M7-2 新画的虚空石纹路层（tools/gen/void_veins.py：从原图取亮处起稿、手画能量脉络，16 × 16 灰度，染料色只染这一层）',
    'DERIVED\ttextures/blocks/voidglass.png\ttextures/block/voidglass_veins.png\t第六批 6a M7-2 新画的虚空玻璃纹路层（同上，主脉两侧的光晕改成半透明）',
    'DERIVED\ttextures/blocks/creativemrustorage.png\ttextures/block/creative/infinite_mru_source.png\t第六批 6b M5 无尽 MRU 源（tools/gen/creative_textures.py）：棕框按亮度换成金色色带 + 斜向流光，芯外边框换成转动的彩虹环，粉色十字明暗呼吸；32 帧 × 2 刻（.mcmeta 同名）',
    'DERIVED\ttextures/blocks/creativeespestorage.png\ttextures/block/creative/infinite_espe_source.png\t第六批 6b M5 无尽 ESPE 源（同上，绿色芯明暗呼吸）',
    'DERIVED\ttextures/blocks/creativemrustorage.png + textures/blocks/chaos.png\ttextures/block/creative/infinite_thermal_source_hot.png\t第六批 6b M5 无尽冷热源（热）：框同无尽 MRU 源，芯换成 chaos.png 的流动纹、按亮度改成岩浆色（同上脚本）',
    'DERIVED\ttextures/blocks/creativemrustorage.png + textures/blocks/frozen.png\ttextures/block/creative/infinite_thermal_source_cold.png\t第六批 6b M5 无尽冷热源（冷）：框同无尽 MRU 源，芯换成 frozen.png 的流动纹（同上脚本）',
    'DERIVED\ttextures/items/misc/mrucrystal.png\ttextures/item/creative/infinite_mru_crystal.png\t第六批 6b M5 无尽 MRU 晶体（同上脚本）：轮廓压成暗金，晶体按对角线铺彩虹、逐帧流动（保留原明暗），三处星光轮流闪；32 帧 × 2 刻',
    'DERIVED\ttextures/items/tools/charmlife.png\ttextures/item/creative/creative_ward.png\t第六批 6b M5 无尽护佑护符（同上脚本）：淡金圈换金色色带 + 流光，心形宝石彩虹流动，宝石底的灰换成呼吸的白光；32 帧 × 2 刻',
    'DERIVED\ttextures/blocks/playerpentacle.png\ttextures/block/icons/playerpentacle.png\t第三批灵魂五芒星的物品图标：原图 Lanczos 缩到 32×32（和原图缩放的结果逐像素相同）',
    'DERIVED\ttextures/blocks/demonicpentacle.png\ttextures/block/icons/demonicpentacle.png\t第七批 M1 恶魔五芒星的物品图标：原图 Lanczos 缩到 32×32（tools/gen/demonic_pentacle_icon.py，同灵魂五芒星的 block/icons/playerpentacle.png）',
    'SOUND\tsounds/sound/demon_summon_0.ogg\tsounds/sound/demon_summon_0.ogg\t第七批 D3 = A 导入，原样拷贝：原作 EssentialCraft 3 4.5.1710.125（327cb83，2015-05-03，Modbder「Demons」那次提交）随恶魔一起加入；原作没有 CREDITS，README 声明全模组 CC BY-SA 4.0、没有列外；文件里没有第三方标记（无 Vorbis 注释，不像两个 freesfx 音效带 title=freesfx.co.uk），11 个文件同一编码器、按事件命名、长度 2.7~5.7 秒；按 CC BY-SA 4.0 署名原作者（CREDITS.md「Sounds」）；音效定义 tools/sounds/ecdemon.json',
    'SOUND\tsounds/sound/demon_summon_1.ogg\tsounds/sound/demon_summon_1.ogg\t同上（sound.demon.summon）',
    'SOUND\tsounds/sound/demon_summon_2.ogg\tsounds/sound/demon_summon_2.ogg\t同上（sound.demon.summon）',
    'SOUND\tsounds/sound/demon_say_0.ogg\tsounds/sound/demon_say_0.ogg\t同上（sound.demon.say）',
    'SOUND\tsounds/sound/demon_say_1.ogg\tsounds/sound/demon_say_1.ogg\t同上（sound.demon.say）',
    'SOUND\tsounds/sound/demon_say_2.ogg\tsounds/sound/demon_say_2.ogg\t同上（sound.demon.say）',
    'SOUND\tsounds/sound/demon_say_3.ogg\tsounds/sound/demon_say_3.ogg\t同上（sound.demon.say）',
    'SOUND\tsounds/sound/demon_depart_0.ogg\tsounds/sound/demon_depart_0.ogg\t同上（sound.demon.depart）',
    'SOUND\tsounds/sound/demon_doom.ogg\tsounds/sound/demon_doom.ogg\t同上（sound.demon.doom）',
    'SOUND\tsounds/sound/demon_trade_0.ogg\tsounds/sound/demon_trade_0.ogg\t同上（sound.demon.trade）',
    'SOUND\tsounds/sound/demon_trade_1.ogg\tsounds/sound/demon_trade_1.ogg\t同上（sound.demon.trade）',
    'SOUND\tsounds/sound/beam_start.ogg\tsounds/sound/beam_start.ogg\t第五批随枪械导入（sound.beam，定义 tools/sounds/ecguns.json），第八批补记：原作 327cb83（2015-05-03，Modbder，和第七批收下的 11 个恶魔音效同一次提交）；编码器 Lavf56.23.106 / Lavc56.26.100，和两个 freesfx 音效的 Lavf56.12.103 不是同一版，没有第三方标记；按 CC BY-SA 4.0 署名原作者',
    'EXCLUDED\tsounds/sound/gen_electricity.ogg\t\t第八批 D9 = A 不收：和两个 freesfx 音效（lightning_hit、mrucu_noise）同一次提交 ceabf8b（2014-12-08）加入，编码器标记 Lavf56.12.103 / Lavc56.12.100 和它们逐字相同，来源说不清；sound.gen_electricity 定义改指原版 entity.lightning_bolt.impact（tools/sounds/framework4.json）',
    'EXCLUDED\tsounds/sound/heartbeat.ogg\t\t第八批 D9 = A 不收：同一次提交 ceabf8b 加入，Vorbis 注释带 ARTIST / ALBUM / TITLE = Heartbeat、GENRE = Blues，像下载来的成品，来源说不清；sound.heartbeat 定义改指原版 entity.warden.heartbeat（tools/sounds/eccorruption.json）',
    'EXCLUDED\tsounds/sound/tinnitus.ogg\t\t第八批 D9 = A 不收：同一次提交 ceabf8b 加入，没有注释、只有编码器 libVorbis 20070622，来源说不清（和 deep_noise 一样按「来源说不清的不收」处理）；sound.tinnitus 定义改指原版 block.beacon.ambient（升调，tools/sounds/eccorruption.json）',
    'SOUND\tsounds/sound/hologram_rest.ogg\tsounds/sound/hologram_rest.ogg\t第八批 8-Fb 导入，原样拷贝（python tools/import_assets.py sound …）：Modbder 在 0d0e9a1（2015-05-28，全息投影 Boss 代码 243cb4c 同一天紧接着的资源提交）加入；编码器 libVorbis 20101101、没有 Vorbis 注释、没有第三方标记，照第七批 D3 的规矩收（同恶魔音效），按 CC BY-SA 4.0 署名原作者（CREDITS.md「Sounds」）；音效定义 tools/sounds/echologram.json（sound.mob.hologram.stop）',
    'SOUND\tsounds/sound/hologram_damage_melee.ogg\tsounds/sound/hologram_damage_melee.ogg\t同上（sound.mob.hologram.damage.melee）',
    'SOUND\tsounds/sound/hologram_damage_projectile.ogg\tsounds/sound/hologram_damage_projectile.ogg\t同上（sound.mob.hologram.damage.projectile）；Vorbis 注释只有转换工具 Encoded by=Sound Grinder 3.3.10',
    'SOUND\tsounds/sound/hologram_shutdown.ogg\tsounds/sound/hologram_shutdown.ogg\t同上（sound.mob.hologram.shutdown）',
    'SOUND\tsounds/sound/orbital_charge.ogg\tsounds/sound/orbital_charge.ogg\t同上（sound.orbital_strike，定义 tools/sounds/echoloweapons.json）',
    'SOUND\tsounds/records/hologram_battle.ogg\tsounds/records/hologram_battle.ogg\t第八批 8-Fb 导入，原样拷贝：同一次提交 0d0e9a1；Kevin MacLeod「Ropocolypse 2」（incompetech.com，CC BY 4.0；原作语言文件拼成 Ropocalypse 2），107.04 秒；CREDITS.md 单独署名；records.hologram（全息唱片与 Boss 战音乐共用），定义 tools/sounds/echologram.json',
    'EXCLUDED\tsounds/sound/hologram_attack.ogg\t\t原作 sound.mob.hologram.start 的文件，注册了却从没播放过（原作 SoundRegistry L30，全源码没有调用），第八批不导入',
    'MODEL\tmodels/item/board.obj\tmodels/item/board.obj\t第八批 8-Fb 已导入（python tools/import_assets.py obj item/board）：宅男反重力板飞行时脚下的板子；原作在代码里绑贴图（RenderHandlerEC L126 textures/models/board.png），MTL 没写 map_Kd，导入时补 map_Kd essentialcraft5:block/model/board',
    'MODEL\tmodels/item/board.mtl\tmodels/item/board.mtl\t同上',
    'MODEL\tmodels/block/chargingchamber.mtl\tmodels/block/chargingchamber.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/chargingchamber.obj\tmodels/block/chargingchamber.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标：原作侧壁、厚边、护角块侧面、凹槽内壁的 UV 挤在一条线（或一个点）上，游戏里拉成一道道条纹；只给这些面新加 vt、改 f 行里的 vt 序号（顶点、法线、其它面的 UV 不动，整条线落在一种颜色里的纯色面保留原样），由 tools/gen/fix_obj_uv.py 从原作生成，重新导入后要重跑',
    'MODEL\tmodels/block/colddistillator.mtl\tmodels/block/colddistillator.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/colddistillator.obj\tmodels/block/colddistillator.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/crystalcontroller.mtl\tmodels/block/crystalcontroller.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/crystalcontroller.obj\tmodels/block/crystalcontroller.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/crystalextractor.mtl\tmodels/block/crystalextractor.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/crystalextractor.obj\tmodels/block/crystalextractor.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/crystalformer.mtl\tmodels/block/crystalformer.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/crystalformer.obj\tmodels/block/crystalformer.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'DERIVED\tmodels/block/mithrilinecrystal.mtl\tmodels/block/demoniccrystal.mtl\t第八批补记：原作同一个 mithrilinecrystal.obj 在渲染器代码里换绑 textures/models/demoniccrystal.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 mithrilinecrystal.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/demoniccrystal',
    'MODEL\tmodels/block/endergenerator.mtl\tmodels/block/endergenerator.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/endergenerator.obj\tmodels/block/endergenerator.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/flowerburner.mtl\tmodels/block/flowerburner.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/flowerburner.obj\tmodels/block/flowerburner.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成；终极自然熔炉换贴图共用这个模型）',
    'MODEL\tmodels/block/heatgenerator.mtl\tmodels/block/heatgenerator.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/heatgenerator.obj\tmodels/block/heatgenerator.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成；终极热力产能器换贴图共用这个模型）',
    'MODEL\tmodels/block/magicalenchanter.mtl\tmodels/block/magicalenchanter.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/magicalenchanter.obj\tmodels/block/magicalenchanter.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/magicaljukebox.mtl\tmodels/block/magicaljukebox.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/magicaljukebox.obj\tmodels/block/magicaljukebox.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/magicalrepairer.mtl\tmodels/block/magicalrepairer.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/magicalrepairer.obj\tmodels/block/magicalrepairer.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/magiciantable.mtl\tmodels/block/magiciantable.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/magiciantable.obj\tmodels/block/magiciantable.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/magmaticsmeltery.mtl\tmodels/block/magmaticsmeltery.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/magmaticsmeltery.obj\tmodels/block/magmaticsmeltery.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/matrixabsorber.mtl\tmodels/block/matrixabsorber.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/matrixabsorber.obj\tmodels/block/matrixabsorber.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/mirror.mtl\tmodels/block/mirror.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODEL\tmodels/block/mirror.obj\tmodels/block/mirror.obj\t第八批补记：import_assets.py obj 导入，原样（第十一批 tools/gen/fix_obj_uv.py 查过：退化的面整条线都落在一种颜色里，游戏里是纯色、没有条纹，不改）',
    'MODEL\tmodels/block/mirrorinv.mtl\tmodels/block/mirrorinv.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/mirrorinv.obj\tmodels/block/mirrorinv.obj\t第八批补记：import_assets.py obj 导入后末尾加了玻璃背面（反向的 pPlane1、法线 -z 和两个面，文件里有注释）：物品按背面剔除画，默认的方块图标角度、左手、掉落物转身时会看到背面，没有这几行玻璃就不见了。第十一批 tools/gen/fix_obj_uv.py 查过：退化的面整条线都落在一种颜色里，游戏里是纯色、没有条纹，不改（脚本照原样把这几行接回去）',
    'MODEL\tmodels/block/mithrilinecrystal.mtl\tmodels/block/mithrilinecrystal.mtl\t第八批补记：import_assets.py obj 导入；map_Kd 改指新命名空间的贴图 essentialcraft5:block/model/mithrilinecrystal，Kd 0 0 0 改 1 1 1（Forge 的 OBJ 加载器拿 Kd 当顶点颜色乘贴图，0 会画成全黑）',
    'MODEL\tmodels/block/mithrilinecrystal.obj\tmodels/block/mithrilinecrystal.obj\t第八批补记：import_assets.py obj 导入，原样',
    'MODEL\tmodels/block/mithrilinefurnace.mtl\tmodels/block/mithrilinefurnace.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/mithrilinefurnace.obj\tmodels/block/mithrilinefurnace.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成；侧面绿色 X 那些纯色三角形保留原样）',
    'MODEL\tmodels/block/mrucoilhardener.mtl\tmodels/block/mrucoilhardener.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/mrucoilhardener.obj\tmodels/block/mrucoilhardener.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/mrureactor_btm.mtl\tmodels/block/mrureactor_btm.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODEL\tmodels/block/mrureactor_btm.obj\tmodels/block/mrureactor_btm.obj\t第八批补记：import_assets.py obj 导入，原样（第十一批 tools/gen/fix_obj_uv.py 查过：退化的面整条线都落在一种颜色里，游戏里是纯色、没有条纹，不改）',
    'DERIVED\tmodels/block/mithrilinecrystal.mtl\tmodels/block/palecrystal.mtl\t第八批补记：原作同一个 mithrilinecrystal.obj 在渲染器代码里换绑 textures/models/palecrystal.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 mithrilinecrystal.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/palecrystal',
    'MODEL\tmodels/block/potionspreader.mtl\tmodels/block/potionspreader.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/potionspreader.obj\tmodels/block/potionspreader.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'MODEL\tmodels/block/radiatingchamber.mtl\tmodels/block/radiatingchamber.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/radiatingchamber.obj\tmodels/block/radiatingchamber.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'DERIVED\tmodels/block/mithrilinecrystal.mtl\tmodels/block/shadecrystal.mtl\t第八批补记：原作同一个 mithrilinecrystal.obj 在渲染器代码里换绑 textures/models/shadecrystal.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 mithrilinecrystal.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/shadecrystal',
    'MODEL\tmodels/block/sphere.mtl\tmodels/block/sphere.mtl\t第八批补记：import_assets.py obj 导入；map_Kd 改指新命名空间的贴图 essentialcraft5:block/model/sphere，Kd 0 0 0 改 1 1 1（Forge 的 OBJ 加载器拿 Kd 当顶点颜色乘贴图，0 会画成全黑）',
    'MODEL\tmodels/block/sphere.obj\tmodels/block/sphere.obj\t第八批补记：import_assets.py obj 导入，原样',
    'MODEL\tmodels/block/sunraycollector.mtl\tmodels/block/sunraycollector.mtl\t第八批补记：import_assets.py obj 导入；只把 map_Kd 改指新命名空间的贴图（essentialcraft5:block/model/…）',
    'MODIFIED\tmodels/block/sunraycollector.obj\tmodels/block/sunraycollector.obj\t第八批补记：import_assets.py obj 导入；第十一批修了退化的贴图坐标（同 chargingchamber.obj 那行，tools/gen/fix_obj_uv.py 生成）',
    'DERIVED\tmodels/block/flowerburner.mtl\tmodels/block/ultraflowerburner.mtl\t第八批补记：原作同一个 flowerburner.obj 在渲染器代码里换绑 textures/models/ultraflowerburner.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 flowerburner.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/ultraflowerburner',
    'DERIVED\tmodels/block/heatgenerator.mtl\tmodels/block/ultraheatgenerator.mtl\t第八批补记：原作同一个 heatgenerator.obj 在渲染器代码里换绑 textures/models/ultraheatgenerator.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 heatgenerator.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/ultraheatgenerator',
    'DERIVED\tmodels/block/mithrilinecrystal.mtl\tmodels/block/voidcrystal.mtl\t第八批补记：原作同一个 mithrilinecrystal.obj 在渲染器代码里换绑 textures/models/voidcrystal.png；Forge 的 OBJ 加载器只从 MTL 拿贴图，照 mithrilinecrystal.mtl 另写一份、map_Kd 指 essentialcraft5:block/model/voidcrystal',
    'EXCLUDED\tsounds/records/arstotzkan_anthem.ogg\t\t版权音乐（Lucas Pope & MetalCanyon - Arstotzkan Anthem，《Papers, Please》），唱片 record_a 已去掉（命名表 removed），音频不收',
    "EXCLUDED\tsounds/records/lets_be_friends.ogg\t\t版权音乐（SilentOwl - Let's Be Friends），唱片 record 已去掉（命名表 removed），音频不收",
    'EXCLUDED\tsounds/records/there_are_no_secrets.ogg\t\t版权音乐（《The Stanley Parable》- Secrets!），唱片 record_secret 已去掉（命名表 removed），音频不收',
    'EXCLUDED\tsounds/sound/page_turn.ogg\t\t原作研究书的翻页声（sound.pageturn）；研究手册换成自己的界面后翻页用原版 book.page_turn（client/guide/GuideScreen），不收',
]

# 导入后又改过的贴图：import_textures 不再用原图覆盖它们（改原图后重跑对应的脚本），asset_map.tsv 里记成 MODIFIED
MODIFIED = {
    'textures/block/firstworld/cactus_side.png': '第六批 6a M1-5 提亮（tools/gen/dread_textures.py：CIELCh 里抬黑场、色相不动；平均 L* 17.0 → 32.5）',
    'textures/block/firstworld/cactus_top.png': '第六批 6a M1-5 提亮（tools/gen/dread_textures.py，和侧面同一条曲线；平均 L* 29.4 → 42.6）',
    'textures/block/firstworld/cactus_bottom.png': '第六批 6a M1-5 提亮（tools/gen/dread_textures.py，和侧面同一条曲线；平均 L* 70.2 → 75.8）',
    'textures/block/firstworld/dreaddirt.png': '第六批 6a M1-5 提亮（tools/gen/dread_textures.py：CIELCh 里抬黑场、色相不动；平均 L* 10.5 → 25.0）',
}

EXCLUDED_PREFIXES = {
    'textures/items/secrets/': '致敬其它游戏的彩蛋贴图，来源未核实，要重画',
    'textures/blocks/ct/': 'EC3 遗留的 CTM 分边贴图，1.20.1 不用 CTM',
    'textures/gui/gui_generic.png': '原版界面 / 图标贴图（Mojang）的副本，本模组不用，不收（原版锻造台界面）',
    'textures/gui/moon_phases.png': '原版界面 / 图标贴图（Mojang）的副本，本模组不用，不收（原版月相图）',
    'textures/special/icons.png': '原版界面 / 图标贴图（Mojang）的副本，本模组不用，不收（原版 gui/icons.png）',
}


def git(*args, binary=False):
    out = subprocess.run(['git', '-C', SOURCE_REPO, *args], check=True, capture_output=True)
    return out.stdout if binary else out.stdout.decode('utf-8')


def list_blobs(prefix):
    """返回 [(相对 assets 的路径, blob sha)]。"""
    result = []
    for line in git('ls-tree', '-r', 'HEAD', SRC_ASSETS + prefix).splitlines():
        meta, path = line.split('\t', 1)
        _, kind, sha = meta.split(' ')
        if kind == 'blob':
            result.append((path[len(SRC_ASSETS):], sha))
    return result


def new_path(old_rel):
    p = old_rel.lower()
    for old, new in DIR_RENAMES:
        if p.startswith(old):
            return new + p[len(old):]
    return p


CT_SUFFIXES = ('_ct.png', '_ct.png.mcmeta', '_ct_tex.png')
CT_REASON = '给 ConnectedTexturesMod 用的连接纹理（不做联动），_ct 与普通贴图相同；第五批 D5 删掉'


def excluded_reason(old_rel):
    low = old_rel.lower()
    for prefix, reason in EXCLUDED_PREFIXES.items():
        if low.startswith(prefix.lower()):
            return reason
    if low.startswith('textures/blocks/') and low.endswith(CT_SUFFIXES):
        return CT_REASON
    return None


def write_blob(sha, dst_rel):
    dst = os.path.join(DST_ASSETS, *dst_rel.split('/'))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    data = git('cat-file', 'blob', sha, binary=True)
    with open(dst, 'wb') as f:
        f.write(data)


def import_textures():
    blobs = list_blobs('textures/')
    groups = {}
    for rel, sha in blobs:
        groups.setdefault(rel.lower(), []).append((rel, sha))

    rows, conflicts, dupes, written = [], [], 0, 0
    for low in sorted(groups):
        members = groups[low]
        reason = excluded_reason(low)
        if reason:
            for rel, _ in members:
                rows.append(f'EXCLUDED\t{rel}\t\t{reason}')
            continue
        shas = {sha for _, sha in members}
        if len(shas) > 1:
            conflicts.append(members)
            continue
        dupes += len(members) - 1
        dst_rel = new_path(members[0][0])
        if dst_rel in MODIFIED:
            for rel, _ in members:
                rows.append(f'MODIFIED\t{rel}\t{dst_rel}\t{MODIFIED[dst_rel]}')
            continue
        write_blob(members[0][1], dst_rel)
        written += 1
        for rel, _ in members:
            rows.append(f'TEXTURE\t{rel}\t{dst_rel}\t')

    with open(MAP_FILE, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# kind\told path (assets/essentialcraft/...)\tnew path (assets/essentialcraft5/...)\tnote\n')
        f.write('\n'.join(rows + EXTRA_ROWS) + '\n')

    print(f'写入 {written} 个文件；跳过 {dupes} 个大小写重复（内容相同）；排除 {sum(r.startswith("EXCLUDED") for r in rows)} 个')
    if conflicts:
        print('!! 以下大小写重名的文件内容不同，未导入，需要人工挑选：')
        for members in conflicts:
            for rel, sha in members:
                print(f'   {sha[:10]}  {rel}')
        sys.exit(1)


def texture_ref(old_ref):
    """把 MTL / 模型 JSON 里的旧贴图引用（essentialcraft:models/x、models/x.png 等）换成新的 RL。"""
    ref = old_ref.strip()
    ns = OLD_NS
    if ':' in ref:
        ns, ref = ref.split(':', 1)
    if ref.endswith('.png'):
        ref = ref[:-4]
    if '/' not in ref:
        # MTL 里只写文件名的（armtextures.png），原作都放在 textures/models/
        ref = 'models/' + ref
    if ns != OLD_NS:
        raise ValueError(f'不是原作命名空间的贴图引用：{old_ref}')
    path = new_path('textures/' + ref + '.png')
    return NEW_NS + ':' + path[len('textures/'):-len('.png')]


# 原作模型里引用的原版贴图（1.13 扁平化改过名），全表只有这些；遇到表外的会报错，补进来即可
VANILLA_TEXTURES = {
    'blocks/beacon': 'block/beacon',
    'blocks/anvil_base': 'block/anvil',
    'blocks/water_still': 'block/water_still',
    'blocks/lava_still': 'block/lava_still',
    'blocks/redstone_torch_on': 'block/redstone_torch',
    'blocks/quartz_block_top': 'block/quartz_block_top',
    'blocks/glass_magenta': 'block/magenta_stained_glass',
    'blocks/glass': 'block/glass',
    'blocks/endframe_top': 'block/end_portal_frame_top',
    'blocks/dirt_podzol_top': 'block/podzol_top',
    'blocks/diamond_block': 'block/diamond_block',
    'blocks/cauldron_top': 'block/cauldron_top',
    'items/record_far': 'item/music_disc_far',
    'items/record_blocks': 'item/music_disc_blocks',
    'items/record_13': 'item/music_disc_13',
}


def model_texture_ref(ref):
    if ref.startswith('#'):
        return ref
    if ref.startswith('minecraft:'):
        path = ref[len('minecraft:'):]
        if path not in VANILLA_TEXTURES:
            raise SystemExit(f'原版贴图 {ref} 不在 VANILLA_TEXTURES 表里，查 1.13 的新名字补进去')
        return 'minecraft:' + VANILLA_TEXTURES[path]
    return texture_ref(ref)


def model_parent_ref(ref):
    """1.12 的 "minecraft:cube_all" 简写在 1.20.1 要写全 "minecraft:block/cube_all"。"""
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns == 'minecraft' and '/' not in path:
        path = 'block/' + path
    elif ns == OLD_NS:
        ns = NEW_NS
    return f'{ns}:{path}'


def import_model(old_name, new_name, render_type=None):
    """导入 models/block/<old>.json 为 models/block/<new>.json：改贴图引用和父模型，去掉注释键。"""
    import json
    old_rel = f'models/block/{old_name}.json'
    matches = [(rel, sha) for rel, sha in list_blobs('models/block/') if rel.lower() == old_rel]
    if not matches:
        raise SystemExit(f'原作没有 {old_rel}')
    model = json.loads(git('cat-file', 'blob', matches[0][1], binary=True).decode('utf-8'))
    model.pop('__comment', None)
    if 'parent' in model:
        model['parent'] = model_parent_ref(model['parent'])
    if 'textures' in model:
        model['textures'] = {k: model_texture_ref(v) for k, v in model['textures'].items()}
    if render_type:
        model['render_type'] = render_type
    dst = os.path.join(DST_ASSETS, 'models', 'block', f'{new_name}.json')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
        f.write('\n')
    print(f'MODEL\t{old_rel}\t-> models/block/{new_name}.json')


def import_lang():
    """原作 .lang 转成 tools/legacy_lang/*.json（原键名不变），datagen 的语言提供者从这里按原键取译文。"""
    import json
    out_dir = os.path.join(HERE, 'legacy_lang')
    os.makedirs(out_dir, exist_ok=True)
    for rel, sha in list_blobs('lang/'):
        name = os.path.basename(rel)
        if not name.endswith('.lang'):
            continue
        text = git('cat-file', 'blob', sha, binary=True).decode('utf-8-sig')
        entries = {}
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            entries[key.strip()] = value
        dst = os.path.join(out_dir, name[:-len('.lang')].lower() + '.json')
        with open(dst, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(entries, f, indent=1, ensure_ascii=False, sort_keys=True)
            f.write('\n')
        print(f'LANG\t{rel}\t-> tools/legacy_lang/{os.path.basename(dst)}（{len(entries)} 条）')


# 原作在代码里绑贴图、MTL 没写 map_Kd 的模型：导入时补上（Forge 的 OBJ 加载器只从 MTL 拿贴图）。键是 models/ 下的路径，值是原作贴图引用
OBJ_MISSING_TEXTURES = {
    'item/board': 'models/board',  # 反重力板：原作 RenderHandlerEC L126 绑 textures/models/board.png
}


def import_obj(name):
    """导入 models/<子目录>/<name>.obj 与 .mtl（不写子目录就是 block/）；MTL 的 map_Kd 改成新贴图 RL，mtllib 保持同名。"""
    path = name if '/' in name else 'block/' + name
    folder, base = path.rsplit('/', 1)
    for ext in ('obj', 'mtl'):
        old_rel = f'models/{path}.{ext}'
        matches = [(rel, sha) for rel, sha in list_blobs(f'models/{folder}/') if rel.lower() == old_rel]
        if not matches:
            raise SystemExit(f'原作没有 {old_rel}')
        data = git('cat-file', 'blob', matches[0][1], binary=True).decode('utf-8')
        if ext == 'mtl':
            lines = []
            has_map = False
            for line in data.splitlines():
                if line.strip().startswith('map_Kd'):
                    ref = line.strip()[len('map_Kd'):].strip()
                    line = 'map_Kd ' + texture_ref(ref)
                    has_map = True
                lines.append(line)
            if not has_map and path in OBJ_MISSING_TEXTURES:
                lines.append('map_Kd ' + texture_ref(OBJ_MISSING_TEXTURES[path]))
            data = '\n'.join(lines) + '\n'
        dst = os.path.join(DST_ASSETS, 'models', *folder.split('/'), f'{base}.{ext}')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8', newline='\n') as f:
            f.write(data)
        print(f'OBJ\t{old_rel}\t-> models/{path}.{ext}')


def import_sounds(paths):
    """原样拷音效：sounds/<路径>.ogg 拷到新命名空间的同一路径（不重新编码、不转单声道）。"""
    blobs = list_blobs('sounds/')
    for path in paths:
        old_rel = f'sounds/{path}.ogg'
        matches = [(rel, sha) for rel, sha in blobs if rel.lower() == old_rel.lower()]
        if not matches:
            raise SystemExit(f'原作没有 {old_rel}')
        write_blob(matches[0][1], old_rel.lower())
        print(f'SOUND\t{old_rel}\t-> {old_rel.lower()}')


def main():
    if not os.path.isdir(os.path.join(SOURCE_REPO, '.git')):
        raise SystemExit(f'找不到原作仓库：{SOURCE_REPO}')
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    cmd = sys.argv[1]
    if cmd == 'textures':
        import_textures()
    elif cmd == 'obj':
        for name in sys.argv[2:]:
            import_obj(name)
    elif cmd == 'lang':
        import_lang()
    elif cmd == 'sound':
        import_sounds(sys.argv[2:])
    elif cmd == 'model':
        # model <原名> <新名> [render_type]
        import_model(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)
    else:
        raise SystemExit(__doc__)


if __name__ == '__main__':
    main()
