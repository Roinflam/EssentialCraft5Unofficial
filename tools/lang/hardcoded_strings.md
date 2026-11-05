# 原作写死在代码里的英文

来源：原作 EssentialCraft 4 Unofficial 仓库 `src/main/java/essentialcraft/` 下的 Java 源码（下表路径都相对这个目录；`integration/` 是联动代码，不看）。
已经在 `ECLanguageProvider` 的 NEW 表里做过的（平衡值三态、绑定宝石状态、MRU 数值 `%s/%s MRU` 与 `%s MRU/t`、月相、绑定宝石 tooltip）不重复列。

约定：
- 键名按任务要求分 `gui.` / `tooltip.` / `message.` 三类，前缀都是 `essentialcraft5`；指令反馈用原版风格的 `commands.essentialcraft5.*`，按键用 `key.essentialcraft5.*`，新拆出来的方块/物品用 `block.` / `item.essentialcraft5.<注册名>`。
- 原作是字符串拼接，这里一律改成带 `%s` 的整句，方便调语序（中文语序和英文不同）。`%s%%` 表示「数字 + 百分号」。
- 中文排版规则见 `glossary.md` 第一节。

---

## 1. 界面（gui）

| 文件:行号 | 原文 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| client/gui/element/GuiCrystalState.java:53 | `"Fire: "+x+"%"` | gui.essentialcraft5.crystal.fire | Fire: %s%% | 火：%s%% |
| client/gui/element/GuiCrystalState.java:54 | `"Water: "+x+"%"` | gui.essentialcraft5.crystal.water | Water: %s%% | 水：%s%% |
| client/gui/element/GuiCrystalState.java:55 | `"Earth: "+x+"%"` | gui.essentialcraft5.crystal.earth | Earth: %s%% | 地：%s%% |
| client/gui/element/GuiCrystalState.java:56 | `"Air: "+x+"%"` | gui.essentialcraft5.crystal.air | Air: %s%% | 风：%s%% |
| client/gui/element/GuiCrystalState.java:57 | `"Size: "+x+"%"` | gui.essentialcraft5.crystal.size | Size: %s%% | 大小：%s%% |
| client/gui/element/GuiCrystalState.java:62 | `"Chance: "+x+"%"` | gui.essentialcraft5.crystal.chance | Chance: %s%% | 几率：%s%% |
| client/gui/element/GuiEnchantmentState.java:51 | Nothing To Enchant! | gui.essentialcraft5.enchanter.nothing | Nothing to enchant! | 没有可附魔的物品！ |
| client/gui/element/GuiEnchantmentState.java:54 | Output Must Be Empty! | gui.essentialcraft5.enchanter.output_full | Output must be empty! | 请先清空输出槽！ |
| client/gui/element/GuiEnchantmentState.java:57 | No MRU! | gui.essentialcraft5.no_mru（附魔台、修复器、天气控制器共用） | No MRU! | MRU 不足！ |
| client/gui/element/GuiEnchantmentState.java:60、65、75 | Item Can't Be Enchanted! | gui.essentialcraft5.enchanter.cannot_enchant | This item can't be enchanted! | 该物品无法附魔！ |
| client/gui/element/GuiEnchantmentState.java:68 | No Bookshelves! | gui.essentialcraft5.enchanter.no_bookshelves | No bookshelves! | 周围没有书架！ |
| client/gui/element/GuiEnchantmentState.java:71 | Enchanting Item... | gui.essentialcraft5.enchanter.working | Enchanting... | 正在附魔…… |
| client/gui/element/GuiRepairState.java:37 | Nothing To Fix! | gui.essentialcraft5.repairer.nothing | Nothing to repair! | 没有需要修理的物品！ |
| client/gui/element/GuiRepairState.java:40 | Can't Fix This! | gui.essentialcraft5.repairer.cannot | Can't repair this! | 这个修不了！ |
| client/gui/element/GuiRepairState.java:43 | Already Fixed! | gui.essentialcraft5.repairer.done | Already repaired! | 已经修好了！ |
| client/gui/element/GuiRepairState.java:46 | No MRU! | gui.essentialcraft5.no_mru | No MRU! | MRU 不足！ |
| client/gui/element/GuiRepairState.java:49 | Working... | gui.essentialcraft5.repairer.working | Repairing... | 修理中…… |
| client/gui/element/GuiWeatherState.java:36 | No Catalyst! | gui.essentialcraft5.weather.no_catalyst | No catalyst! | 缺少催化剂！ |
| client/gui/element/GuiWeatherState.java:39 | Bottle Storage Full! | gui.essentialcraft5.weather.bottles_full | Bottle slot full! | 瓶子槽已满！ |
| client/gui/element/GuiWeatherState.java:42 | No MRU! | gui.essentialcraft5.no_mru | No MRU! | MRU 不足！ |
| client/gui/element/GuiWeatherState.java:46 | Clearing Skies... | gui.essentialcraft5.weather.clearing | Clearing skies... | 正在驱散云层…… |
| client/gui/element/GuiWeatherState.java:49 | Already Clear! | gui.essentialcraft5.weather.already_clear | Already clear! | 已经是晴天了！ |
| client/gui/element/GuiWeatherState.java:54 | Making Rain... | gui.essentialcraft5.weather.raining | Making rain... | 正在呼唤降雨…… |
| client/gui/element/GuiWeatherState.java:57 | Already Raining! | gui.essentialcraft5.weather.already_raining | Already raining! | 已经在下雨了！ |
| client/gui/element/GuiWeatherState.java:62 | Creating Thunder... | gui.essentialcraft5.weather.thundering | Summoning thunder... | 正在召唤雷暴…… |
| client/gui/element/GuiWeatherState.java:65 | Already Thundering! | gui.essentialcraft5.weather.already_thundering | Already thundering! | 已经在打雷了！ |
| client/gui/element/GuiWeatherState.java:69 | Invalid Item! | gui.essentialcraft5.weather.invalid_item | Invalid item! | 物品无效！ |
| client/gui/element/GuiMRUGenerated.java:41 | `usage+"UBMRU > "+gen+"MRU"` | gui.essentialcraft5.matrix_absorber.rate | %s UBMRU → %s MRU | %s UBMRU → %s MRU |
| client/gui/element/GuiMRUGenerated.java:119 | `"Heat: "+heat+"C"` | gui.essentialcraft5.heat | Heat: %s°C | 温度：%s°C |
| client/gui/element/GuiMRUGenerated.java:173 | `gen+" MRU/hit"` | gui.essentialcraft5.mru_per_hit | %s MRU/hit | 每击 %s MRU |
| client/gui/element/GuiResistanceState.java:43 | `resistance+" MROV"` | gui.essentialcraft5.mrov | %s MROV | %s MROV |
| client/gui/GuiFilter.java:102 | Metadata: Ignored | gui.essentialcraft5.filter.damage.ignored | Durability: Ignored | 耐久：忽略 |
| client/gui/GuiFilter.java:105 | Metadata: Not Ignored | gui.essentialcraft5.filter.damage.matched | Durability: Matched | 耐久：匹配 |
| client/gui/GuiFilter.java:112 | NBT Tag: Ignored | gui.essentialcraft5.filter.nbt.ignored | NBT: Ignored | NBT：忽略 |
| client/gui/GuiFilter.java:115 | NBT Tag: Not Ignored | gui.essentialcraft5.filter.nbt.matched | NBT: Matched | NBT：匹配 |
| client/gui/GuiFilter.java:123；GuiCraftingFrame.java:80 | Ore Dictionary: Ignored | gui.essentialcraft5.filter.tag.ignored | Match by Tag: Off | 按标签：关（原作「忽略矿辞」和这个开关语义相反，第六批 6b 按「按标签」改写） |
| client/gui/GuiFilter.java:126；GuiCraftingFrame.java:83 | Ore Dictionary: Not Ignored | gui.essentialcraft5.filter.tag.matched | Match by Tag: On | 按标签：开 |
| client/gui/GuiMIMScreen.java:202 | Request | gui.essentialcraft5.mim.request | Request | 请求 |
| client/gui/GuiMIMScreen.java:420 | Craft | gui.essentialcraft5.mim.craft | Craft | 合成 |
| client/gui/GuiPlayerPentacle.java:114、267；client/render/RenderHandlerEC.java:963、967 | `n+" ESPE"` | gui.essentialcraft5.espe | %s ESPE | %s ESPE |
| client/gui/GuiPlayerPentacle.java:109；client/render/RenderHandlerEC.java:965 | ` [Creative]` | gui.essentialcraft5.creative_suffix | %s [Creative] | %s［创造］ |
| client/gui/GuiWeaponBench.java:27 | Done | 直接用原版 `gui.done` | Done | 完成 |
| proxy/ClientProxy.java:536 | ComputerArmorBoost（按键名） | key.essentialcraft5.computer_armor_boost | Computer Armor: Boost | 宅男盔甲：喷射推进 |
| proxy/ClientProxy.java:538 | ComputerArmorNightVision（按键名） | key.essentialcraft5.computer_armor_night_vision | Computer Armor: Night Vision | 宅男盔甲：夜视 |
| proxy/ClientProxy.java:536、538 | 按键分类借用原版 key.categories.gameplay | key.categories.essentialcraft5 | EssentialCraft | 源质魔法 |

另：`client/gui/GuiWeaponBench.java:58–66` 用 `item.ec3.gun.*.name` 当界面标题，这几个键在原作语言文件里不存在，原作界面上显示的是键名本身。移植时直接用对应枪械物品的名字。

## 2. 物品提示（tooltip）

| 文件:行号 | 原文 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| common/item/ItemsCore.java:133 | Converts life energy of fallen foes to your hunger | tooltip.essentialcraft5.void_helmet | Converts the life energy of fallen foes into food | 将倒下敌人的生命能量转化为你的饱食度 |
| common/item/ItemsCore.java:138 | Can dodge 20% of attacks | tooltip.essentialcraft5.void_chestplate | Can dodge 20% of attacks | 有 20% 几率闪避攻击 |
| common/item/ItemsCore.java:142 | Can phase-shift through monsters | tooltip.essentialcraft5.void_leggings | Can phase-shift through monsters | 能相位偏移穿过怪物（第七批不用这个键：原作没有实现，删提示行，batch7.md §4.3） |
| common/item/ItemsCore.java:146 | Negates 90% of falldamage | tooltip.essentialcraft5.void_boots | Negates 90% of fall damage | 抵消 90% 摔落伤害 |
| common/item/ItemsCore.java:152 | Linses created from the moonlight metal to see unpurity | tooltip.essentialcraft5.inquisitorium_helmet | Lenses forged from moonlight metal to see impurity | 以月光金属磨成的镜片，能看穿一切不洁 |
| common/item/ItemsCore.java:157 | Be quiet, Dominique! | tooltip.essentialcraft5.inquisitorium_chestplate | Be quiet, Dominique! | 安静点，多米尼克！ |
| common/item/ItemsCore.java:161 | Magic is a parasite. It must be wiped out! | tooltip.essentialcraft5.inquisitorium_leggings | Magic is a parasite. It must be wiped out! | 魔法是寄生虫，必须根除！ |
| common/item/ItemsCore.java:165 | Magic shall not prevail! | tooltip.essentialcraft5.inquisitorium_boots | Magic shall not prevail! | 魔法绝不能得逞！ |
| common/item/ItemBaublesResistance.java:58；utils/common/PlayerTracker.java:44 | `"+"+n+"% to MRUCorruption resistance"` | tooltip.essentialcraft5.resistance.mru_corruption | +%s%% MRU Corruption Resistance | MRU 腐化抗性 +%s%% |
| common/item/ItemBaublesResistance.java:59；utils/common/PlayerTracker.java:45 | `"+"+n+"% to MRURadiation resistance"` | tooltip.essentialcraft5.resistance.mru_radiation | +%s%% MRU Radiation Resistance | MRU 辐射抗性 +%s%% |
| common/item/ItemBaublesResistance.java:60；utils/common/PlayerTracker.java:46 | `"-"+n+"% to Corruption affection"` | tooltip.essentialcraft5.resistance.corruption | -%s%% Corruption Affection | 腐化影响 -%s%% |
| common/item/ItemControlRod.java:80 | Currently linked to Mirror At: | tooltip.essentialcraft5.control_rod.linked | Linked to Magical Mirror at: | 已链接的魔力镜： |
| common/item/ItemControlRod.java:84；ItemInventoryGem.java:105 | `"dimension: "+id` | 建议把 NEW 表的 tooltip.essentialcraft5.bound_gem.dimension 改名为 tooltip.essentialcraft5.dimension 三处共用 | Dimension: %s | 维度：%s |
| common/item/ItemInventoryGem.java:101 | Currently Bound To Inventory At: | tooltip.essentialcraft5.inventory_gem.bound | Bound to inventory at: | 当前绑定的容器： |
| common/item/ItemCraftingFrame.java:37 | Current recipe: | tooltip.essentialcraft5.crafting_frame.recipe | Current recipe: | 当前配方： |
| common/item/ItemCraftingFrame.java:42 | `i+": Empty"` | tooltip.essentialcraft5.crafting_frame.empty_slot | %s: Empty | %s：空 |
| common/item/ItemCraftingFrame.java:51 | Result: None | tooltip.essentialcraft5.crafting_frame.no_result | Result: None | 产物：无 |
| common/item/ItemCraftingFrame.java:54 | `"Result: "+name` | tooltip.essentialcraft5.crafting_frame.result | Result: %s | 产物：%s |
| common/item/ItemElementalSword.java:189 | `"Primal: "+elem` | tooltip.essentialcraft5.forged_elemental_sword.primary | Primary: %s | 主元素：%s |
| common/item/ItemElementalSword.java:190 | `"Second: "+elem` | tooltip.essentialcraft5.forged_elemental_sword.secondary | Secondary: %s | 副元素：%s |
| common/item/ItemElementalSword.java:272–297 | Fire / Water / Earth / Air / Combined（存进 NBT 再原样显示） | tooltip.essentialcraft5.element.fire / water / earth / air / combined | Fire / Water / Earth / Air / Combined | 火 / 水 / 地 / 风 / 复合 |
| common/item/ItemElementalSword.java:350 等 | `"Damage +N"`（N = 2~11） | tooltip.essentialcraft5.ember_effect.damage（未用：余烬伤害合计改用 tooltip.essentialcraft5.forged_elemental_sword.ember_damage「余烬加成：攻击伤害 +%s」） | Damage +%s | 伤害 +%s |
| common/item/ItemElementalSword.java:356 | Slowness | tooltip.essentialcraft5.ember_effect.slowness | Slowness | 缓慢 |
| common/item/ItemElementalSword.java:375 | Greater Slow | tooltip.essentialcraft5.ember_effect.greater_slowness | Greater Slowness | 强效缓慢 |
| common/item/ItemElementalSword.java:362 | Poison | tooltip.essentialcraft5.ember_effect.poison | Poison | 中毒 |
| common/item/ItemElementalSword.java:365 | Greater Poison | tooltip.essentialcraft5.ember_effect.greater_poison | Greater Poison | 强效中毒 |
| common/item/ItemElementalSword.java:371、408 | Damage Self | tooltip.essentialcraft5.ember_effect.damage_self | Hurts the Wielder | 反噬自身 |
| common/item/ItemElementalSword.java:381 | Lightning | tooltip.essentialcraft5.ember_effect.lightning | Lightning | 引雷 |
| common/item/ItemElementalSword.java:390 | Weakness | tooltip.essentialcraft5.ember_effect.weakness | Weakness | 虚弱 |
| common/item/ItemElementalSword.java:396 | Damage Boost | tooltip.essentialcraft5.ember_effect.strength | Strength | 力量 |
| common/item/ItemElementalSword.java:399 | Speed Boost | tooltip.essentialcraft5.ember_effect.speed | Speed | 迅捷 |
| common/item/ItemElementalSword.java:424 | Greater Speed Boost | tooltip.essentialcraft5.ember_effect.greater_speed | Greater Speed | 强效迅捷 |
| common/item/ItemElementalSword.java:405 | Lifesteal | tooltip.essentialcraft5.ember_effect.lifesteal | Lifesteal | 吸血 |
| common/item/ItemElementalSword.java:412 | Greater Lifesteal | tooltip.essentialcraft5.ember_effect.greater_lifesteal | Greater Lifesteal | 强效吸血 |
| common/item/ItemElementalSword.java:409 | Greater Hunger | tooltip.essentialcraft5.ember_effect.greater_hunger | Greater Hunger | 强效饥饿 |
| common/item/ItemEssence.java:47–56 | `"Rarity: "+Common/Uncommon/Rare/Exceptional` | tooltip.essentialcraft5.rarity + .rarity.common / uncommon / rare / exceptional | Rarity: %s；Common / Uncommon / Rare / Exceptional | 稀有度：%s；普通 / 罕见 / 稀有 / 卓越 |
| common/item/ItemMRUMatrixProjection.java:68 | `"Thus is a projection of "+name+"'s MRU Matrix"` | tooltip.essentialcraft5.matrix_projection.owner | This is a projection of %s's MRU Matrix | 这是%s的 MRU 矩阵投影 |
| common/item/ItemPlayerList.java:54 | Allowed Players: | tooltip.essentialcraft5.player_list.allowed | Allowed players: | 允许的玩家： |
| common/item/ItemWindTablet.java:165 | Wind Name: | tooltip.essentialcraft5.wind_tablet.name | Wind Name: | 风之真名： |
| common/item/ItemSoulStone.java:71 | `"Tracking MRU Matrix of "+name` | tooltip.essentialcraft5.soul_stone.tracking | Tracking the MRU Matrix of %s | 正在追踪%s的 MRU 矩阵 |
| common/item/ItemSoulStone.java:72 | `"Detected "+n+" UBMRU Energy"` | tooltip.essentialcraft5.soul_stone.ubmru | Detected %s UBMRU | 检测到 %s UBMRU |
| common/item/ItemSoulStone.java:101 | `"MRU Matrix twists with "+type+" Energies"` | tooltip.essentialcraft5.soul_stone.matrix_type | The MRU Matrix twists with %s energies | MRU 矩阵中涌动着%s能量 |
| common/item/ItemSoulStone.java:77–97 | Neutral / Chaos / Frozen / Magic / Shade / Unknown | gui.essentialcraft5.matrix_type.neutral / chaos / frozen / magic / shade / unknown（矩阵投影、枪械透镜也能用） | Neutral / Chaos / Frozen / Magic / Shade / Unknown | 中性 / 混沌 / 冰冻 / 魔力 / 暗影 / 未知 |
| common/item/ItemSoulStone.java:103 | The player is Windbound | tooltip.essentialcraft5.soul_stone.windbound | The player is Windbound | 该玩家受到风之眷顾 |
| common/item/ItemSoulStone.java:108 | The MRU Matrix of the owner is too weak to track | tooltip.essentialcraft5.soul_stone.too_weak | The owner's MRU Matrix is too weak to track | 主人的 MRU 矩阵太弱，无法追踪 |
| common/item/ItemSecret.java:59–62 | 铁木树枝的 4 行说明 | tooltip.essentialcraft5.secret.ironwood_branch.0~3 | The branch seems to be made of §firon§7 / However it is clearly a branch of a tree / You feel better while holding it / Maybe it can improve your §bspells§7? | 这根树枝好像是§f铁§7做的 / 但它分明是从树上折下来的 / 握着它让你感觉好了一些 / 也许能增强你的§b法术§7？ |
| common/item/ItemSecret.java:66–69 | 神秘木棍的 4 行说明 | tooltip.essentialcraft5.secret.mysterious_stick.0~3 | This seems to be a regular stick / But it gives you a strange feel of power / You only know one thing / Whoever controls the stick controls the §3universe...§7 | 这看起来只是根普通的木棍 / 却让你感到一股奇异的力量 / 你只知道一件事 / 谁掌握了这根木棍，谁就掌握了§3宇宙……§7 |
| common/item/ItemSecret.java:73–76 | 丝滑石头的 4 行说明 | tooltip.essentialcraft5.secret.smooth_silky_stone.0~3 | This stone is too smooth / It makes you feel better / It is also very silky / Maybe some kind of §2bird§7 would like it? | 这块石头光滑得过分 / 摸着让人心情变好 / 而且非常丝滑 / 也许某种§2鸟§7会喜欢它？ |
| common/item/ItemSecret.java:80–83 | 奇异人偶的 4 行说明 | tooltip.essentialcraft5.secret.strange_figure.0~3 | This is a very strange figure / It seems to be from the future / You can't do anything with it / But it seems a bit §8damaged...§7 | 这是个非常奇怪的人偶 / 它似乎来自未来 / 你拿它什么也做不了 / 不过它好像有点§8损坏……§7 |
| common/item/ItemSecret.java:87–90 | 奇异徽记的 4 行说明 | tooltip.essentialcraft5.secret.strange_symbol.0~3 | This is a very strange symbol / It seems to be an image of something / When you look at it you want to glorify something / There are letters on the bottom that say §dEZIC | 这是个非常奇怪的符号 / 像是某种东西的图案 / 看着它，你忍不住想要赞颂些什么 / 底部有一行字：§dEZIC |

（410 路公交车票和 D6 的说明原作已经放进语言文件：`essentialcraft.text.desc.secret_0_*`、`secret_1_*`，译文在 overrides 里；移植时可以顺手改成和上面同一套键名。）
（原作 tooltip 默认是灰色，上面中文里的 `§7` 是为了在彩色词之后切回灰色；如果移植代码用 Component 样式而不是格式码，去掉即可。）

## 3. 聊天消息（message）

| 文件:行号 | 原文 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| common/item/ItemBalanceSetter.java:38 | `"Set Mode To "+mode`（mode 0/1/2 就是平衡值 0/1/2） | message.essentialcraft5.balance_setter.mode（参数用 gui.essentialcraft5.balance.frozen / pure / chaos） | Mode set to: %s | 模式已切换为：%s |
| common/item/ItemBaublesSpecial.java:285 | The wind catcher catches some wind... | message.essentialcraft5.wind_catcher | The Wind Catcher catches some wind... | 捕风挂坠捕获了一缕风…… |
| common/item/ItemControlRod.java:48 | Mirror linked to the wand! | message.essentialcraft5.control_rod.mirror_selected | Mirror linked to the rod! | 已记下这面魔力镜！ |
| common/item/ItemControlRod.java:63 | Mirror linked to the inventory! | message.essentialcraft5.control_rod.mirror_linked | Mirror linked to the inventory! | 魔力镜已链接到这个容器！ |
| common/item/ItemMagicalBuilder.java:118 | `"[Magical Builder] First position: "+x+", "+y+", "+z` | message.essentialcraft5.builder.first | [Building Wand] First point: %s, %s, %s | [建筑魔杖] 第一点：%s, %s, %s |
| common/item/ItemMagicalBuilder.java:129 | `"[Magical Builder]The distance between points ("+d+") is too large, max: 48!"` | message.essentialcraft5.builder.too_far | [Building Wand] The points are too far apart (%s), max %s! | [建筑魔杖] 两点距离 %s 超出上限 %s！ |
| common/item/ItemMagicalBuilder.java:135 | `"[Magical Builder] Second position: "+x+", "+y+", "+z` | message.essentialcraft5.builder.second | [Building Wand] Second point: %s, %s, %s | [建筑魔杖] 第二点：%s, %s, %s |
| common/item/ItemMagicalBuilder.java:142 | [Magical Builder]Both points already set! Shift-rightclick air to reset! | message.essentialcraft5.builder.both_set | [Building Wand] Both points are already set! Sneak + right-click the air to reset. | [建筑魔杖] 两个点都已设置！潜行对空气右键可重置 |
| common/item/ItemMagicalBuilder.java:149 | `"[Magical Builder]Set the block to: "+name` | message.essentialcraft5.builder.block | [Building Wand] Block set to: %s | [建筑魔杖] 方块已设为：%s |
| common/item/ItemMagicalBuilder.java:159 | [Magical Builder]Both points reseted! | message.essentialcraft5.builder.reset | [Building Wand] Both points reset! | [建筑魔杖] 已重置两个点！ |
| common/item/ItemMagicalBuilder.java:165 | `"[Magical Builder]Filled selected area! "+n+" blocks got replaced!"` | message.essentialcraft5.builder.filled | [Building Wand] Done! %s blocks changed. | [建筑魔杖] 完成，改动了 %s 格！（五种模式共用，第六批 6b 改成不只说「填充」） |
| common/item/ItemMRUMatrixProjection.java:115 | Your MRU Matrix twists with new colors! | message.essentialcraft5.matrix_projection.used | Your MRU Matrix twists with new colors! | 你的 MRU 矩阵扭曲出了新的色彩！ |
| common/item/ItemSecret.java:110 | You gase into the stars holding the ticket. | message.essentialcraft5.ticket.0 | You gaze at the stars, holding the ticket. | 你握着车票，凝望星空。 |
| common/item/ItemSecret.java:111 | Suddenly a gust of wind swoops upon you. | message.essentialcraft5.ticket.1 | Suddenly a gust of wind swoops upon you. | 一阵风忽然向你卷来。 |
| common/item/ItemSecret.java:112 | You are immediately beeing attacked by lots of feels. | message.essentialcraft5.ticket.2 | A flood of feelings washes over you. | 种种情绪瞬间将你淹没。 |
| common/item/ItemSecret.java:113 | Strange, warm feels fall upon you. | message.essentialcraft5.ticket.3 | Strange, warm feelings fall upon you. | 一种奇异而温暖的感觉笼罩了你。 |
| common/item/ItemSecret.java:114 | You feel calm and relaxed. | message.essentialcraft5.ticket.4 | You feel calm and relaxed. | 你感到平静而放松。 |
| common/item/ItemSecret.java:115 | A feeling falls upon you. You feel like you've just lived a whole another life. | message.essentialcraft5.ticket.5 | You feel as if you have just lived a whole other life. | 你忽然觉得，自己仿佛刚刚过完了另一段人生。 |
| common/item/ItemSecret.java:116 | You try to remember what happened, but memory lets you down. | message.essentialcraft5.ticket.6 | You try to remember what happened, but your memory lets you down. | 你试着回想发生了什么，记忆却一片空白。 |
| common/item/ItemSecret.java:117 | You suddenly realise, that you no longer keep the ticket in your hand. | message.essentialcraft5.ticket.7 | You suddenly realise the ticket is no longer in your hand. | 你猛然发现，手里的车票不见了。 |
| common/item/ItemSecret.java:118 | Instead a music disk is in your hand. | message.essentialcraft5.ticket.8 | Instead, you are holding a music disc. | 取而代之的是一张音乐唱片。 |
| common/item/ItemSecret.java:119 | When you gaze to the disk, you begin to hear the song, written on it. | message.essentialcraft5.ticket.9 | As you gaze at the disc, you begin to hear the song recorded on it. | 凝视着唱片，你开始听见录在上面的那首歌。 |
| common/item/ItemSecret.java:120 | You start feeling really sad, like you've missed something very important to you. | message.essentialcraft5.ticket.10 | You feel really sad, as if you have missed something very important. | 你忽然很难过，像是错过了对你非常重要的东西。 |
| common/item/ItemSecret.java:121 | You feel lonely, like missing another half of you. | message.essentialcraft5.ticket.11 | You feel lonely, as if missing the other half of yourself. | 你感到孤独，像是失去了另一半的自己。 |
| common/item/ItemSecret.java:122 | After some time you calm down. | message.essentialcraft5.ticket.12 | After a while, you calm down. | 过了一会儿，你平静了下来。 |
| common/item/ItemWindKeeper.java:34 | You hear a very pale 'Thank you'... | message.essentialcraft5.wind_keeper.thanks | You hear a faint "Thank you"... | 你听见一声极轻的“谢谢”…… |
| utils/common/PlayerTracker.java:81 | The wind pushes you upwards... | message.essentialcraft5.wind.lift | The wind pushes you upwards... | 风将你向上托起…… |
| utils/common/WindRelations.java:88 | The wind timidly touches your hair... | message.essentialcraft5.wind.timid | The wind timidly touches your hair... | 风怯生生地拂过你的头发…… |
| utils/common/WindRelations.java:94 | You feel a warm touch of the wind... | message.essentialcraft5.wind.warm | You feel a warm touch of the wind... | 你感到风温柔地拂过…… |
| utils/common/WindRelations.java:99 | You feel a chilling touch of the wind... | message.essentialcraft5.wind.chilling | You feel a chilling touch of the wind... | 你感到一阵冷风拂过…… |
| utils/common/WindRelations.java:104 | You feel a strong push of the wind... | message.essentialcraft5.wind.push | You feel a strong push of the wind... | 一阵强风推了你一把…… |
| utils/common/WindRelations.java:109 | You feel a strong blow of the wind... | message.essentialcraft5.wind.blow | You feel a strong blow of the wind... | 一阵强风迎面扑来…… |
| utils/common/WindRelations.java:116 | The wind pushes you in the back... | message.essentialcraft5.wind.back | The wind pushes you from behind... | 风在背后推着你…… |
| common/world/event/WorldEventDarkness.java:15 | Why is it so dark?! | message.essentialcraft5.hoanna_event.darkness.start | Why is it so dark?! | 怎么这么黑？！ |
| common/world/event/WorldEventDarkness.java:33 | The lights are back! | message.essentialcraft5.hoanna_event.darkness.end | The lights are back! | 光回来了！ |
| common/world/event/WorldEventEarthquake.java:14 | The ground is shaking... | message.essentialcraft5.hoanna_event.earthquake.start | The ground is shaking... | 大地在颤抖…… |
| common/world/event/WorldEventEarthquake.java:28 | The ground is solid again! | message.essentialcraft5.hoanna_event.earthquake.end | The ground is solid again! | 大地重归平稳！ |
| common/world/event/WorldEventFumes.java:14 | What is that smell?! | message.essentialcraft5.hoanna_event.fumes.start | What is that smell?! | 这是什么味道？！ |
| common/world/event/WorldEventFumes.java:29 | The poison is gone! | message.essentialcraft5.hoanna_event.fumes.end | The poison is gone! | 毒气散去了！ |
| common/world/event/WorldEventSunArray.java:24 | This is going to be a hot day... | message.essentialcraft5.hoanna_event.sun_array.start | This is going to be a hot day... | 看来今天要热得要命…… |
| common/world/event/WorldEventSunArray.java:61 | The suns are back to normal! | message.essentialcraft5.hoanna_event.sun_array.end | The suns are back to normal! | 太阳们恢复正常了！ |
| common/block/BlockMRUCoil.java:89 | 用了键 `essentialcraft.txt.noPermission`，但原作语言文件里没有这个键 | message.essentialcraft5.no_permission | You don't have permission to use this! | 你没有权限使用它！ |
| common/tile/TileMagicalJukebox.java:90 | Achievable! | message.essentialcraft5.jukebox.achievable | Achievable! | 可达成！ |
| common/tile/TileMagicalJukebox.java:32 | 15 条假进度名（彩蛋，随机发到聊天栏） | message.essentialcraft5.jukebox.advancement.0~14 | Top Secret! / Secret Advancement 2017! / Never Give Up On Your Secrets! / Go, Go Secret Rangers! / Such Secrets! / The Secret Is A Lie! / Secret Bro! / Secrets, Secrets Everywhere! / Secrets, Secrets, Secrets! / Never Give Up! / Unachievable! / Too Many Secrets! / Very Secret! / Top Secret Of 2164! / The Secret Paradise! | 绝密！/ 2017 年度秘密进度！/ 永不放弃你的秘密！/ 冲吧，秘密战队！/ 好多秘密！/ 秘密是个谎言！/ 秘密好兄弟！/ 秘密，到处都是秘密！/ 秘密秘密秘密！/ 永不放弃！/ 无法达成！/ 秘密太多啦！/ 非常秘密！/ 2164 年度绝密！/ 秘密天堂！ |

`TileMagicalJukebox.java:33` 还有 65 个彩蛋怪物名（jeb_、Herobrine、OVER 9000! 之类），都是专有名词和梗，**不译**。

## 4. 指令反馈（commands）

指令用法字符串（`/corruptioneffect <player> <id|clear>` 这类）在 1.20 由 Brigadier 自动生成，不需要翻译。

| 文件:行号 | 原文 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| utils/commands/CommandCorruptionEffect.java:41 | `"Successfully added corruption effect "+id+" to "+name` | commands.essentialcraft5.corruptioneffect.success | Added corruption effect %s to %s | 已为%2$s添加腐化效果 %1$s |
| utils/commands/CommandCreateMRUCU.java:49 | Sucessfully created MRUCU | commands.essentialcraft5.createmrucu.success | Created an MRUCU | 已创建 MRUCU |
| utils/commands/CommandHoannaEvent.java:38 | `"Cannot find event with name "+id` | commands.essentialcraft5.hoannaevent.not_found | Cannot find event named %s | 找不到名为 %s 的事件 |
| utils/commands/CommandHoannaEvent.java:50 | Sucessfully stopped Hoanna event | commands.essentialcraft5.hoannaevent.stopped | Stopped the Hoanna event | 已停止海纳尔事件 |
| utils/commands/CommandHoannaEvent.java:58 | `"Sucessfully set Hoanna event to "+id` | commands.essentialcraft5.hoannaevent.set | Set the Hoanna event to %s | 海纳尔事件已设为 %s |
| utils/commands/CommandSetBalanceInMRUCU.java:42 | `"Successfully set balance in nearest MRUCU to "+v` | commands.essentialcraft5.setbalanceinmrucu.success | Set the balance of the nearest MRUCU to %s | 已将最近的 MRUCU 的平衡值设为 %s |
| utils/commands/CommandSetBalanceInMRUCU.java:45；CommandSetMRUInMRUCU.java:45 | Could not find MRUCU | commands.essentialcraft5.mrucu_not_found | Could not find an MRUCU | 找不到 MRUCU |
| utils/commands/CommandSetMRUInMRUCU.java:42 | `"Successfully set MRU in nearest MRUCU to "+v` | commands.essentialcraft5.setmruinmrucu.success | Set the MRU of the nearest MRUCU to %s | 已将最近的 MRUCU 的 MRU 设为 %s |
| utils/commands/CommandSetUBMRU.java:35 | `"Successfully set "+name+"'s UBMRU to "+v` | commands.essentialcraft5.setubmru.success | Set %s's UBMRU to %s | 已将%s的 UBMRU 设为 %s |
| utils/commands/CommandSetWindPoints.java:35 | `"Successfully set "+name+"'s Wind Points to "+v` | commands.essentialcraft5.setwindpoints.success | Set %s's wind relation to %s | 已将%s的风之好感设为 %s |
| utils/commands/CommandSetWindbound.java:35 | `"Successfully set "+name+"'s Windbound to "+v` | commands.essentialcraft5.setwindbound.success | Set %s's Windbound status to %s | 已将%s的风之眷顾状态设为 %s |

## 5. 名称类：原作写死、缺键，或 1.20 拆分后需要新名字

| 来源 | 原文 / 现状 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| common/registry/BiomeRegistry.java:27 | Chaos Corrupted Land | biome.essentialcraft5.chaos_corruption | Chaos Corrupted Land | 混沌腐化之地 |
| common/registry/BiomeRegistry.java:28 | Frozen Corrupted Land | biome.essentialcraft5.frozen_corruption | Frozen Corrupted Land | 冰冻腐化之地 |
| common/registry/BiomeRegistry.java:29 | Shadow Corrupted Land | biome.essentialcraft5.shadow_corruption | Shadow Corrupted Land | 暗影腐化之地 |
| common/registry/BiomeRegistry.java:30 | Magic Corrupted Land | biome.essentialcraft5.magic_corruption | Magic Corrupted Land | 魔力腐化之地 |
| common/registry/BiomeRegistry.java:31 | Hoanna Dreadlands | biome.essentialcraft5.dreadlands | Hoanna Dreadlands | 海纳尔恐惧荒原 |
| common/registry/BiomeRegistry.java:33 | Hoanna Desert | biome.essentialcraft5.desert | Hoanna Desert | 海纳尔沙漠 |
| common/item/ItemCharm.java:25、171 | `"Charm Of "+name`（10 种，原作名字写死，语言文件里的「Elemental Charm」从没显示过） | item.essentialcraft5.charm_of_fire / water / earth / air / steam / magma / lightning / life / rain / dust | Charm of Fire / Water / Earth / Air / Steam / Magma / Lightning / Life / Rain / Dust | 火之护符 / 水之护符 / 地之护符 / 风之护符 / 蒸汽护符 / 岩浆护符 / 雷电护符 / 生命护符 / 雨水护符 / 尘土护符 |
| common/item/ItemCapturedSoul.java:47 | `name+" - "+entity` | item.essentialcraft5.captured_soul.with_entity | Captured Soul - %s | 被俘灵魂（%s） |
| common/item/ItemSpawnEggEC.java | 已决定改成原版式刷怪蛋 | item.essentialcraft5.<实体>_spawn_egg | Wind Mage Spawn Egg / Demon Spawn Egg | 风法师刷怪蛋 / 恶魔刷怪蛋（其它活体同理：「<实体名>刷怪蛋」） |
| common/entity/EntitiesCore.java:33–37 | 这 5 个实体原作没有语言键（第八批实际用的名字：`divider` 改注册成 `divider_field`「二分场」，克隆体照刷怪蛋叫「玩家克隆体」，破甲弹照计划叫「护甲破坏者」；文字在 tools/lang/keys/framework8a.json、echologram.json、echoloweapons.json） | entity.essentialcraft5.player_clone / orbital_strike / divider_field / armor_destroyer / divider_projectile | Player Clone / Orbital Strike / Divider Field / Armor Destroyer / Divider Projectile | 玩家克隆体 / 轨道打击 / 二分场 / 护甲破坏者 / 二分弹 |
| common/block/BlockChestEC.java:131 | 子类型 magical / void 原作共用「Magical Chest」 | block.essentialcraft5.magical_chest / void_chest | Magical Chest / Void Chest | 魔力箱子 / 虚空箱子 |
| common/block/BlockFurnaceMagic.java:182 | 子类型 fortified / magic / pale / void 共用「Magmatic Furnace」 | block.essentialcraft5.fortified_magmatic_furnace 等 4 个 | Fortified / Magical / Pale / Void Magmatic Furnace | 强化岩浆炉 / 魔力岩浆炉 / 淡青岩浆炉 / 虚空岩浆炉 |
| common/block/BlockDropsOre.java:116 | 子类型 overworld / nether / end 共用「Elemental Infused Ore」 | block.essentialcraft5.elemental_ore / nether_elemental_ore / end_elemental_ore | Elemental Infused Ore / Nether … / End … | 元素蕴魔矿石 / 下界元素蕴魔矿石 / 末地元素蕴魔矿石 |
| common/block/BlockRightClicker.java:170 | 6 个子类型共用「Magical Activator」 | block.essentialcraft5.magical_activator 等 6 个 | Magical Activator / Sneaky … / Extended … / Sneaky Extended … / Advanced … / Sneaky Advanced … | 魔力执行器 / 潜行魔力执行器 / 扩展魔力执行器 / 潜行扩展魔力执行器 / 高级魔力执行器 / 潜行高级魔力执行器 |
| common/block/BlockWeaponMaker.java:93 | 4 个子类型共用「Weapon Bench」，靠 tooltip 区分 | block.essentialcraft5.pistol_bench / rifle_bench / sniper_bench / gatling_bench | Pistol / Rifle / Sniper / Gatling Bench | 手枪工作台 / 步枪工作台 / 狙击枪工作台 / 加特林工作台 |
| common/block/BlocksCore.java:26（BlockDrops） | 4 种放置状态的魔晶，原作没有语言键 | block.essentialcraft5.fire_drops 等 | Fire / Water / Earth / Air Drops (placed) | 火之魔晶堆 / 水之魔晶堆 / 地之魔晶堆 / 风之魔晶堆 |
| common/block/BlocksCore.java:96 | flowerGreen，无语言键 | block.essentialcraft5.hoanna_flower | Hoanna Flower | 海纳尔花 |
| common/block/BlocksCore.java:97；ItemsCore.java:95 | fruit（方块）/ fruit_Item（食物），无语言键 | block.essentialcraft5.magical_fruit_bush / item.essentialcraft5.magical_fruit | Magical Fruit Bush / Magical Fruit | 魔力果丛 / 魔力果 |
| common/block/BlocksCore.java:99 | tallGrass，无语言键 | block.essentialcraft5.hoanna_grass | Hoanna Grass | 海纳尔草丛 |
| common/block/BlocksCore.java:114 | torch（魔力灯笼放出的光源），无语言键 | block.essentialcraft5.magic_light | Magic Light | 魔力光源 |
| common/block/BlocksCore.java:192 | weatherController，无语言键 | block.essentialcraft5.weather_controller | Weather Controller | 天气控制器 |
| common/item/ItemsCore.java:295–297 | 三种天气催化剂，无语言键 | item.essentialcraft5.clearing_catalyst / raining_catalyst / thundering_catalyst | Clearing / Raining / Thundering Catalyst | 放晴催化剂 / 降雨催化剂 / 雷暴催化剂 |

## 6. 研究书界面（书要重做，只留作参考）

| 文件:行号 | 原文 | 新键名 | 英文 | 中文 |
|---|---|---|---|---|
| client/gui/GuiResearchBook.java:571、630 | `I18n.translateToLocal("MRU Required: "+n)`（把整句当键，永远查不到） | gui.essentialcraft5.book.mru_required | MRU Required: %s | 需要 MRU：%s |
| client/gui/GuiResearchBook.java:670 | `"MRU/t "+n` | 复用 NEW 表 gui.essentialcraft5.mru_per_tick | %s MRU/t | %s MRU/t |
| client/gui/GuiResearchBook.java:1066 | New | gui.essentialcraft5.book.new | New | 新 |

## 7. 不移植（联动）

- `common/item/ItemSoulStone.java:138、142、153、157`：血魔法生命源质（Life Essence）的提示，属于血魔法联动。
- `common/item/ItemComputerArmor.java:45`：神秘时代接口（护目镜、减耗），属于神秘时代联动。

---

## 8. zh_cn_overrides.json 的覆盖说明

- **键**：一律用 `legacy_lang/en_us.json` 里的原作英文键；原作中文文件拼错的键已换成英文文件的正确键：
  `storageMatterOfEthernity` → `storageMatterOfEternity`，`drops_flame` → `drops_fire`，`emberuncommon` → `emberflame`（后两个是原作中文文件用了过期键名，所以这两样东西原作一直显示英文）。
- **数量**：en_us 共 1225 条，译了 626 条（照抄原作 184；改动 389，其中 50 条只是补空格、换全角标点；原作缺而新补 53）；其余 599 条按下面分类没有收录。
  原作中文里本来就是英文的（饰品效果说明 32 条、枪械属性 13 条、死亡消息 4 条等）算在「改动」里。
- **研究书（561 条，书要重做，先不译）**，键前缀：
  - `essentialcraft.page.`（337 条，正文）
  - `ec3book.category_essentialcraft.`（12 条，分类名和简介）
  - `ec3book.discovery_essentialcraft.`（190 条，条目名和一句话简介）
  - `ec3book.discovery_demonicPentacle.`、`ec3book.discovery_enderstar.`、`ec3book.discovery_hologram.`、`ec3book.discovery_mithriline.`、`ec3book.discovery_mithrilineFurnace.`、
    `ec3book.discovery_newMimCraft.`、`ec3book.discovery_newMimInv.`、`ec3book.discovery_newMimScreen.`、`ec3book.discovery_playerPentacle.`、`ec3book.discovery_redSuit.`、`ec3book.discovery_windRune.`（各 2 条）
  - 书的界面短文本（`essentialcraft.txt.book.*`、`essentialcraft.txt.*Recipe`、`essentialcraft.txt.layer_*`、`essentialcraft.text.button.back` 等）顺手译了，新书界面可以直接用。重做时条目名请直接引用 overrides 里的物品名，别再单独翻一遍。
- **已删除（8 条）**：`item.essentialcraft.collectedSpawner.name`、`spawnerCollector.name`、`testItem.name`、`entityEgg.name`，`tile.essentialcraft.darknessObelisk.name`、`monsterHarvester.name`、
  `monsterClinger.name`（就是怪物禁锢器 BlockMonsterHolder），`potion.purpleFlame`（只声明未注册）。
- **联动（8 条）**：`jei.essentialcraft.recipe.*`（5，JEI）、`modifier.mrureinforced.*`（3，匠魂）。
- **过期键（18 条，原作 1.12 代码里已经没有对应的东西）**：`item.essentialcraft.spell*`（11，EC3 的法术）、`item.essentialcraft.staff_*`（3）、
  `tile.essentialcraft.mim.name`、`minEjector.name`、`minInjector.name`（BlocksCore 里已注释掉），`item.essentialcraft.genItemenderScalePlating.name`（[NYI]，没有任何配方用到）。
- **分组总名（4 条，从没显示过）**：`tile.essentialcraft.device.name`、`compressed.name`、`crystalLamp.name`、`mithrilineCrystal.name`——原作的 ItemBlock 总会拼上子类型后缀，1.20 各子类型独立注册后更用不上。
- **原作中文里多出来的 34 个过期键**（`achievement.*` 老成就、魔力装配站 `magicalAssembler`、`[TC]` 花纹方块、`soul.Entity*` 等）：对应内容在 1.12 里已经没有，不迁移。
- `essentialcraft.effect.desc_16`、`desc_18` 原作就是空字符串（这两个腐化效果不发消息），照样收成空字符串。
- `tile.air.name`（空气）只在研究书的结构预览里用，新书如果不画「这格留空」可以不要。

## 9. NEW 表的小修正建议（ECLanguageProvider）

- `gui.essentialcraft5.balance.format` 中文用了半角冒号 `%s: %s`，建议 `%s：%s`。
- `gui.essentialcraft5.link.not_magical`「目标不含MRU！」、`link.no_mru`「目标没有MRU！」：MRU 前后缺空格，建议「目标不含 MRU！」「目标没有 MRU！」。
- `gui.essentialcraft5.moon_phase.5`「娥眉月」：天文名词的规范写法是「蛾眉月」（两种都常见，改不改都行）。
- `tooltip.essentialcraft5.bound_gem.dimension` 建议改名为通用的 `tooltip.essentialcraft5.dimension`，控制棒、容器宝石的提示也要用。

## 10. 顺带发现的原作问题（和文字有关，移植时一起改）

- **余烬元素剑的效果是拿显示文字做判断的**（`ItemElementalSword.java`）：移植时应改成效果 id + 翻译键，不然一汉化就全坏。原作本身已经有两处对不上：
  - `:375` 产出的是 `"Greater Slow"`，`:98` 判断的是 `"Greater Slowness"`，所以强效缓慢从来不生效；`:127`、`:130` 判断的 `"Greater Weakness"`、`"Greater Damage Boost"` 根本没有地方产出。
  - `:347` 起用 `allEmbers.contains("corruption")` 匹配腐化余烬，但余烬的翻译键是 `item.essentialcraft.embercorrupted`，不含 `corruption`，所以和腐化余烬有关的 7 种组合（缓慢、强效缓慢、虚弱、吸血、强效吸血、反噬 + 强效饥饿、伤害 +5）全部失效。
- **元素剑的主/副元素**（`:272–297`）以英文单词存进 NBT 再原样显示，移植时存 id、显示时翻译。
- **灵魂石**（`ItemSoulStone.java:67–69`）按石头绑定的玩家名判断「数据存在」，却显示的是**当前查看者自己**的 UBMRU 和矩阵属性。
- **研究书**（`GuiResearchBook.java:571、630`）把拼好的整句 `"MRU Required: "+n` 当翻译键传给 `translateToLocal`，永远查不到译文、只能显示英文。

## 11. 英文修正建议

**语言文件（en_us）里的**：

| 键 / 位置 | 现在 | 建议 |
|---|---|---|
| 键名 `genItemmagicPurifyed*`（5 个） | Purifyed | Purified（1.20 注册名顺手改正） |
| 原作中文文件键名 `storageMatterOfEthernity` | Ethernity | Eternity |
| 键名 `genItemefficencyUpgrade` | efficency | efficiency |
| 键名 `genItemeyeOfAbsorbtion` | Absorbtion | Absorption（显示名本来就对） |
| 键名 `genItemsubstructionCore` | substruction | subtraction（显示名本来就对） |
| `tile.essentialcraft.dimTransciever.name` | MRU Dimensional Transciever | MRU Dimensional Transceiver |
| `tile.essentialcraft.darknessObelisk.name` | Darkness O8elisk | Darkness Obelisk（此方块已删除，仅作记录） |
| `item.essentialcraft.genItemvisionLense.name`；`essentialcraft.gun.txt.balance_*` | Vision Lense；Lense balance | Vision Lens；Lens balance |
| `item.essentialcraft.secretsmoothandsilkystone.name` | Smooth&Silky Stone | Smooth & Silky Stone |
| `tile.essentialcraft.cacti.name` | Cacti | Dread Cactus（复数形式 + 与原版重名） |
| `item.essentialcraft.genItempalePlate.name` | Pale Plating（和方块 platingPale 同名） | Pale Plate |
| `item.essentialcraft.elementalSword.name` | Elemental Sword（和工具 elemental_sword 同名） | Ember-Forged Sword |
| `item.essentialcraft.record.name`、`record_a.name` | Record | Music Disc（和原版一致） |
| `item.essentialcraft.mruMatrixProjection_*.name` | MRUMP - Chaos 等 | Chaos Matrix Projection 等 |
| `tile.essentialcraft.metadataManager.name` | Metadata Manager | Block State Reader（1.20 没有 metadata） |
| 多个名字里的 Of | Ring Of Experience、Pendant Of Health、Staff Of Life、Eye Of Absorption、Matter Of Eternity、Book Of Knowledge | 标题格式里介词小写：Ring of Experience 等 |
| `advancements.essentialcraft.*.title`（10 个） | Placeholder Text | computer_armor: Fully Geared Nerd；demon_trade: A Deal with the Devil；enter_hoanna: Gateway to Hoanna；magician_table: Magic 101；matrix_absorber: The First MRU；mithriline_furnace: Green Flame；mrucuec: Containment；mrucuec_big: The Bigger the Better；player_pentacle: Soul Cleansing；wind_gem: Gift of the Wind |
| `advancements.essentialcraft.hologram_big.title` | Rembo | Rambo |
| `advancements.essentialcraft.matrix_absorber.desc` | Create a Matrix Absorber | Create a Matrix Destructor（方块实际叫这个） |
| `advancements.essentialcraft.player_pentacle.desc` | Use a Player Pentacle with highest tier | Use a highest-tier Soul Pentacle |
| `advancements.essentialcraft.wind_gem.desc` | Obtain some Wind Gems | Obtain some Wind Crystals |
| `advancements.essentialcraft.mrucuec*.desc` | Construct a MRUCUEC | Construct an MRUCU Enrichment Chamber |
| `essentialcraft.effect.name_15` | Lightnings like you | Lightning likes you |
| `essentialcraft.effect.desc_6` | did a creeper just bypass you? | did a creeper just pass by you? |
| `essentialcraft.effect.desc_14` | You suddenly set on fire | You suddenly catch fire |
| `essentialcraft.txt.help.baubles.19` | some of it's events | some of its events |
| `essentialcraft.txt.fillMode.replaceButSelected` | Replace everything, but selected | Replace everything except selected |
| `essentialcraft.desc.fancy.packedPlate`、`doublePlate` | Packet Plated Tile | Packed Plated Tile |
| `essentialcraft.gun.txt.reload`、`speed`、`spread` | Cooldown time required(sec): 等 | 括号前加空格：Cooldown time required (sec): 等 |
| `essentialcraft.txt.format.*Balance` | ▼ Balance: | 可以写成 ▼ Min Balance: / ▲ Max Balance:，更好懂 |

**写死在代码里的**（上面各表的「英文」列已经是改好的写法）：

- `ItemMRUMatrixProjection.java:68` Thus is a projection → This is a projection
- `ItemsCore.java:152` Linses … unpurity → Lenses … impurity；`ItemsCore.java:146` falldamage → fall damage
- `ItemSecret.java:110` gase → gaze；`:112` beeing … feels → being … feelings；`:115` a whole another life → a whole other life
- `CommandCreateMRUCU.java:49`、`CommandHoannaEvent.java:50、58` Sucessfully → Successfully
- `ItemMagicalBuilder.java:159` reseted → reset；各条缺空格的 `[Magical Builder]First…` → `[Magical Builder] First…`
- `TileMagicalJukebox.java:32` The Secret Paradize → Paradise；Too Much Secrets → Too Many Secrets（彩蛋文字，改不改都行）
- `GuiMRUGenerated.java:119` Heat: 42C → Heat: 42°C
- `ItemElementalSword.java:189–190` Primal / Second → Primary / Secondary；`:375` Greater Slow → Greater Slowness（见第 10 节）
- `ItemCharm.java:171` Charm Of → Charm of
