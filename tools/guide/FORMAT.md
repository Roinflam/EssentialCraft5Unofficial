# 源质魔法5 研究手册 —— 内容格式规范（EC5 版）

> 以 KuvaLich 冒险指南的 FORMAT.md 为底（<https://github.com/Roinflam/KuvaLich> 的 `tools/guide/FORMAT.md`），加上 EC5 自己的块、条件和打勾写法。
> 方案见 `tools/plan/guide_kuvalich.md`（§2.6 是本文件相对 KuvaLich 的差异清单）。**引擎照本文件实现，内容照本文件写。**
> 改内容后跑：
> `PYTHONIOENCODING=utf-8 python tools/guide/validate_guide.py [分类id…]`、`python tools/guide/meta_scan.py [分类id…]`，
> 简体改完再跑 `python tools/guide/make_traditional.py` 重新生成繁体。

## 变更记录

| 日期 | 改了什么 | 谁 |
|---|---|---|
| 2026-09-28 | 初版：KuvaLich 格式 + `recipe / structure / ritual / image` 块、`items.per_row`、`tip lore`、`bauble:` 条件、`{block:} {balance:} {keybind:}`、分区 `group`、`complete / optional / advancement / items`、`_draft` | 工具与内容转换 |
| 2026-09-28 | 小改（不影响引擎）：「做成的标志」英文标题定为 `Done when`（和旧正文一致，原写 `You're done when`）；长段落按显示字数算（物品名展开），不再只数汉字；`config_keys.json` 每项多一个 `comment`（配置注释，常写着单位）；§5 补 `guide_lib.py` 与各脚本参数；§2 注明入门分类第一段的临时内容 | 工具与内容转换 |
| 2026-09-28 | 引擎落地时的澄清（不改写法）：查阅索引的 ② 只收 `recipe` 块**写明的** `item` / `items`（和旧手册一致；只写 `recipe` / `recipe_type` 的块不把表里的产物收进索引，要让某物品查到这一篇就写进条目 `items`）；多层结构图的滚轮切层只在层切换按钮那一排上生效（压在图上滚轮照常滚正文）；结构 / 仪式块也认 `caption?` | 引擎 |
| 2026-09-28 | 按引擎实际表现改两处说明（不改写法）：分类 `subtitle` 只在入门横幅上直接显示，主线 / 支线格子在悬停提示里显示；全是可选步骤的分类（支线），格子上的 x/y 数的是可选步骤（§1.2、§3） | 收尾修复 |
| 2026-09-29 | 护眼改版后的配色说明（不改写法）：阅读底是低饱和的暗灰紫 #1F1C25、正文暖米白 #D9D2C7，`<em>` / `<hl>` 是暖白加粗（不是纯白）；提示框统一灰紫底；结构图 `anchor` 的品红四角改成静止；配方 / 结构图的候选物品（标签原料、可换方块）只在鼠标压着那张图时轮换，平时停在第一个、物品格右上角有个薰衣草小三角 | 收尾修复 |
| 2026-10-05 | §2 骨架加支线 `void_machines`（98）；`logistics` 加 `advanced_filters`、`void_chest`（第六批 6b，不改写法） | 6b 框架 |
| 2026-10-05 | §2 骨架加支线 `creative`（99，第六批 6b M5 无尽道具，当时叫「创造模式道具」） | 6b 集成 |
| 2026-10-05 | §1.6 加条件 `gamemode:creative`（本地玩家是创造模式；`creative` 章整章用它，生存首页不显示）；引擎、`validate_guide.py` 同步 | 6b 修复 |
| 2026-10-05 | §2 `creative` 章改名「支线 · 无尽道具」，去掉整章 `gamemode:creative` 条件（这几件没有配方，但生存里照常能用，所有玩家都看得到）；条件写法保留，现在没有内容用它 | 6b 调整 |
| 2026-10-05 | §2 骨架加主线 `demons`（100，第十七章 · 恶魔）；`pentacle` 加 `tier_two`、`tier_three`，`gear` 加 `void_armor`，`void_machines` 加 `ember_forge`、`ember_sword`（第七批，batch7.md §7，不改写法） | 第七批框架 |
| 2026-10-06 | §2 骨架加主线 `hologram`（101，第十八章 · 全息）、`computer`（102，第十九章 · 宅男套装）（第八批，batch8.md §8，不改写法） | 第八批框架 |
| 2026-10-06 | 引擎补一处表现（不改写法）：首页底部条多一种「主线已完成」——必做步骤全做完时取代「继续阅读」，指向最后一个可见主线章的最后一条（门开着是 `computer/epilogue`，门关着是 `logistics/after_main_line`），薄荷绿细边、不加动画，键 `guide.essentialcraft5.ui.main_done`（batch8.md §8.5） | 第八批集成 |
| 2026-10-06 | §2 `ley` 加 `ley_condenser`、`condensing`（第九批，batch9.md §8，不改写法） | 第九批 |
| 2026-10-07 | §1.2 条目 `items` 可以带条件组 `{"if": …, "items": [id…]}`（条件不成立时组里的物品不进查阅索引和搜索的物品名；引擎 `client/guide/EntryItems`，`validate_guide.py`、`check_progression.py` 同步）；引擎补一处表现（不改写法）：数字的整数部分 ≥ 1000 时按界面语言插千位分隔符（键 `guide.essentialcraft5.ui.fmt.group_sep`，英文逗号、中文不分组），`{cfg:}` 的 raw / int / pct / pctv / x / sec 和配方图的 MRU 数都走它（batch10.md §5.3） | 第十批 |
| 2026-10-07 | §2 骨架加支线 `from_ec4`（120，附录 · 与原作的不同，7 篇说明条目；batch10.md §5.2，不改写法） | 第十批 |
| 2026-10-07 | §2 `computer` 加 `seal_field`（饰品封禁场，排在 `antigravity_board` 后、`costs` 前；batch10.md §5.4，不改写法） | 第十批 |
| 2026-10-12 | `validate_guide.py` 加两项（不改写法）：`{item:}` 物品链接要有落脚的地方（某篇的条目 `items`、配方块写明的产物、物品块、结构 / 仪式产物），且至少一处落脚点的条件链接所在处都带着——附录 `from_ec4` 报错、别的分类警告；附录三列表每格显示宽度 ≤ 30（汉字 1、半角 0.5，物品名展开、配置值按 2 位数） | 第十批 |
| 2026-10-12 | §1.2 条目 `optional` 可以写条件（**按条件可选**，写法同 `if`：条件成立时可选、不成立时必做，例 `"optional": "!cfg:general.hoanna.portal_enabled"`）；引擎 `client/guide/GuideSteps`（界面和 GameTest 共用），`validate_guide.py`（「主线章没有必做步骤」按默认配置和门关两种情形各看一次）、`check_progression.py`（每种情形各求一次）、`meta_scan.py`（新警告：`optional: true` 的步骤首段写「要用 / 必须」却没写「可选」）同步；§3 补老存档迁移（`player/GuideMigration`、`gen_migration.py`，batch10.md §2.5~§2.7） | 第十批 |
| 2026-10-12 | §1.6 加条件 `module:ns:id`（这块宅男套装模块没有被 `general.computer_modules.disabled` 关掉；引擎 `client/guide/GuideConditions`、`validate_guide.py`、`check_progression.py` 同步）；§2 骨架加主线 `inquisitorium`（103，第十九章 · 审判庭）、`terminal`（105，第二十一章 · 终端与模块），`computer` order 102 → 104、改「第二十章」并加 `force_dome`（batch10.md §4.0、§5.14） | 第十批 |
| 2026-10-12 | §2 `inquisitorium` 加可选步骤 `armor`（审判庭套装）；`terminal` 的 `epilogue` 成为主线终点，首页「主线已完成」门开着时指向它（引擎照旧取最后一个看得见的主线章的最后一条，不改写法）；`computer/epilogue` 改成过渡（batch10.md §5.14） | 第十批 |
| 2026-10-13 | §2 骨架加支线 `converter`（106，支线 · MRU 转换器；mru_converter.md §7，不改写法）；`creative/creative_items` 加无尽 MRU 转换器 | MRU 转换器 |
| 2026-10-13 | 引擎（不改写法）：`structure` 块默认画成**立体图**（`client/guide/StructureView3D`），顶上「立体 / 平面」切回原来的分层俯视图；仪式图（写了 `throw` / `result`）仍只有平面图 | 立体结构图 |
| 2026-10-19 | §1.2 分类 / 条目加 `locked_hint`（被 `advancement` 锁住时显示的解锁提示，代替「完成进度「X」后解锁」；只能和 `advancement` 一起写，中英都要有）；分类 `advancement` 不再是「自带内容不写」，`scrolls` 章各卷用它；§2 骨架加支线 `scrolls`（107）。引擎 `GuideContent` / `GuideContentLoader` / `GuideUnlocks.hint(id, custom)` / `GuideView.lockHint`，`validate_guide.py` 同步（batch12.md §4.1.9） | 第十二批 |
| 2026-10-19 | §2 骨架加第十二批的新条目（不改写法）：`essence` 加 `elemental_decomposer`，`farming` 加 `magical_cultivator`，`gear` 加 `essential_tools`、`ley_detector`、`homeward_compass`，`corruption` 加 `matrix_tuner`，`terminal` 加 `attraction`、`stride`、`steady_hand`、`veil` 四篇模块说明条目（§1.6 的「六篇」随之改成「十篇」）；都是可选步骤或说明条目，不进必做主线、不要迁移项（batch12.md §8） | 第十二批 |
| 2026-10-26 | §2 骨架加入门 `matrix`（6，矩阵阶位速查）、支线 `attunement`（108，支线 · 同调）；第二、五、十、十二、十五、十七、十九、二十一章各加必做步骤（进度 id 见 batch13.md §2.2，条目 id 各章自取，不进骨架），不改写法 | 第十三批 13a |
| 2026-10-26 | §1.4 `{cfg:}` 格式链加 `at:N`（列表键取第 N 项，0 起，只能写在最前面；取不到显示「?」）。引擎 `client/guide/GuideValues`（取值在 `GuideConditions.element`，GameTest 也用它）、`validate_guide.py`（只许用在列表键上、按默认值查下标）同步；矩阵相性、矩阵特性、宅男套装各阶单价这些按级 / 按阶的列表配置改用它，不再写死默认值 | 第十三批 13a |
| 2026-10-26 | §2 支线 `attunement` 分类条件去掉海纳尔之门（只看 `general.attunement.enabled`），加 `mhs_armor` / `wind_armor` / `void_armor` / `inquisitorium_armor` / `ember_sword` / `verdict_blade` 六篇（`void_armor` / `inquisitorium_armor` / `ember_sword` / `verdict_blade` 带门条件，原有的 `computer_armor` 也补上门条件；`verdict_blade` 是可选步骤）；`start` 术语表、速查表和 `crafting` 魔力工作台指向同调的行也只看 `attunement.enabled`，不改写法 | 第十三批 13b |
| 2026-10-26 | §1.4 格式链加 `stage:N`（按阶的列表取第 N 阶，短了沿用最后一项）；§1.6 条件加 `cfg:键\|at:N` / `cfg:键\|stage:N`（列表先取一项再判、再比较），例 `cfg:general.mhs_armor.shares_chestplate\|at:0==2`；取不到（不是列表、`at:N` 越界、空列表）同缺键，按不成立处理。给「照默认数值算的例子只在默认值时显示」和按阶藏行用（按阶的列表配置以前只能写死）。引擎 `client/guide/GuideValues`、`GuideConditions`（`element` / `stageElement`）、`validate_guide.py`（只许用在列表键上、按默认值查下标）、`guide_lib.py`、`check_progression.py` 同步；魔力防护的份额举例、风之护甲的空中跳和偏转间隔按每一阶的值显示和藏行 | 第十三批 13b |
| 2026-10-26 | §1.4 `{cfg:}` 格式链加运算 `addcfg:键`（加上另一项数字配置的值）和 `max:N`（和 `min:N` 对称）：两项配置合成的数（「0 阶的数 + 阶 × 每阶多的数」）不再写死默认值，例 `{cfg:general.verdict_blade.verdict.percent_per_stage|mul:3|addcfg:general.verdict_blade.verdict.percent|pctv}`。引擎 `client/guide/GuideValues`、`validate_guide.py`（`addcfg` 的键要存在、是数字配置）同步；同调一章裁决之刃的阶表改用它 | 第十三批 13b |
| 2026-10-29 | §1.3 `entity` 块加 `nbt?`（预览实体建好后合并进去的 SNBT，用来画精英、风法师的第几种）和 `scale?`（0.25~2，按碰撞箱自动适配框高以后再乘，只给模型比碰撞箱大得多的怪）；§2 骨架加支线 `bestiary`（112，支线 · 异兽录，按维度排：主世界、下界、末地、首领；海纳尔一区 13d 补）。`validate_guide.py` 同步（`nbt` 查括号配对，`scale` 在 0.25~2 之间），引擎随 13c 首领件一起落地 | 第十三批 13c |
| 2026-10-31 | §1.3 `entity` 块加 `name?`（框下名字的语言键）：`nbt` 摆出的变体自己换了名字（风暴术士、精英）时写，不写就用实体类型名，和页面标题对不上。引擎 `GuideContent` / `GuideContentLoader` / `GuideMedia.entityName(id, key)`（键不存在退回类型名、记警告）、`validate_guide.py`（要是中英都有的语言键、四种语言一致）、GameTest `guideEntityPreviewNames`（真建一只、并上 nbt，名字键要和框下写的一致）同步 | 第十三批 13c |
| 2026-11-01 | §2 骨架加支线 `hoanna_explore`（111，支线 · 海纳尔探索，整章 `if cfg:general.hoanna.portal_enabled`）；`bestiary` 在 `void_stalker` 后面加海纳尔八篇。`validate_guide.py` 的 `SKELETON` 同步，另加 `RESERVED`（骨架里先占位、内容后补的条目，缺了不报：13e 的 `sealed_vault` `sunken_district` `inquisitorium_outpost`）；`guide_lib.load_config_keys` 读同目录的 `pending_config_13d.json`（还没合进来的配置项：没有的按清单补上、给一条汇总警告，有了却默认值或类型对不上的报错；各路合齐、跑过 runData 后删掉，batch13.md §12.2） | 第十三批 13d |
| 2026-11-02 | §2 骨架加主线 `shade`（109，第二十二章 · 暗影）、`apex`（110，第二十三章 · 九阶），两章整章 `if cfg:general.hoanna.portal_enabled`；`terminal/epilogue` 改成过渡，主线终点移到 `apex/epilogue`（引擎照旧取最后一个看得见的主线章的最后一条，不改写法）；`hoanna_explore` 的三篇写完，`bestiary` 首领区加三篇。`validate_guide.py` 的 `SKELETON` 同步、`RESERVED` 清空；`guide_lib.load_config_keys` 改读 `pending_config_13e.json`（同 13d 的做法，各路合齐、跑过 runData 后删掉） | 第十三批 13e |

---

## 0. 和旧格式的对照

> 旧格式文件（顶层 `guide/<分类>.json`、`tools/lang/keys` 里的 `guide.essentialcraft5.<分类>.*` 内容键、`tools/guide/README.md`、`check_guide.py`）已在 2026-09-29 删除，要看旧写法去 git 历史（6076df8 及以前）。

| 旧 | 新 |
|---|---|
| `guide/<分类>.json` 只写翻译键，文字在 `tools/lang/keys/guide.json` | `guide/<语言>/<分类>.json`，**文字直接写在文件里**，不再有 `guide.essentialcraft5.<分类>.*` 内容键 |
| 条目 `pages`：`text / items / recipe / ritual / structure / image` 六种页 | 条目 `blocks`：KuvaLich 的 12 种块 + `structure / ritual / image`；`recipe` 扩成 EC5 的配方图 |
| 分类 `flow: true` | 分类 `group: "start" / "main" / "side"` |
| 分类 `description`、条目 `description` | 分类 `subtitle`、条目 `summary` |
| 条目省略 `title` 时用图标物品名 | 统一写 `title`；想用物品名就写 `"title": "{item:<id>}"` |
| `**强调**` | `<em>强调</em>` |
| `{entry:分类/条目}` | `[[分类/条目]]` |
| 「**下一步**：…」段 | 目标正好是全书顺序的下一篇就不写（篇末翻页卡代替）；否则写成 `tip info`，标题「下一步」 |
| 「**做成的标志**：…」段 | `tip good`，标题「做成的标志」 |

---

## 1. 内容文件

```
src/main/resources/assets/essentialcraft5/guide/<语言>/<分类id>.json     # 每个分类一个文件
```

- 语言目录：`zh_cn`（主）、`en_us`（译本）；`zh_tw`、`zh_hk` 由 `make_traditional.py` 从 zh_cn 生成（**别手改**）。
  zh_cn 和 en_us **结构必须完全一致**：同样的分类字段、条目 id、块、块类型与顺序、`if`、`{cfg:…}` 占位符、链接目标、配方 / 结构字段，只有文字不同。
- 语言回退**按分类**：当前语言没有某个分类文件时，`zh_*` 回退 zh_cn → en_us；其它语言回退 en_us → zh_cn。
- 分类 id = 文件名，`[a-z0-9_]+`。
- 同目录的 `src/generated/resources/assets/essentialcraft5/guide/config_keys.json` 是 datagen 导出的配置键表（§1.5），**不是内容文件**，加载器不读（它只读 `guide/<语言>/`）。

### 1.1 翻译成别的语言（D6）

手册文字不在语言文件里，只翻 lang 文件的工具和汉化包翻不到它。要加一种语言：

1. 把 `guide/en_us/` 整个复制成 `guide/<语言代码>/`（例如 `guide/ja_jp/`）。
2. 只翻文字字段（`title`、`subtitle`、`summary`、`locked_hint`、各块的 `text` / `items` / `rows` / `header` / `caption` / 结构层与图例的 `label`）。
   **别动**：`id`、`icon`、`order`、`group`、`if`、`complete`、`optional`、`advancement`、`items`（条目的物品表）、块的 `type` 与物品 / 配方 / 结构字段、
   占位符 `{…}` 里的内容、链接 `[[…]]` 的目标部分（`|` 后面的显示文字可以翻）、标签 `<t>` 等。
3. 没翻完的分类可以先不放，缺哪个分类就按上面的规则回退，不会空。
4. 资源包可以按同样路径整份覆盖某个分类文件。

### 1.2 分类文件结构

```json
{
  "id": "elements",
  "order": 10,
  "group": "main",
  "title": "第一章 · 元素",
  "subtitle": "挖魔晶，做出第一批零件",
  "icon": "essentialcraft5:elemental_gem",
  "if": "cfg:…",
  "advancement": "essentialcraft5:…",
  "_draft": true,
  "entries": [
    {
      "id": "ores",
      "title": "{item:essentialcraft5:fire_infused_ore}",
      "icon": "essentialcraft5:fire_infused_ore",
      "summary": "一行摘要，用在左栏列表与搜索结果（≤ 24 个汉字）",
      "complete": "essentialcraft5:main/root",
      "optional": false,
      "advancement": "essentialcraft5:…",
      "items": ["essentialcraft5:fire_drops", "…"],
      "if": ["cfg:machines.xxx.enabled", "mod:curios"],
      "blocks": [ … ],
      "_src": ["blockentity/HeatGeneratorBlockEntity.java:120 — 每 tick 产出 = …", "…"]
    }
  ]
}
```

**分类字段**

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 等于文件名 |
| `order` | 是 | 首页与全书顺序，小的在前；**不许撞号**（validate 报错） |
| `group` | 是 | `start` 入门（首页顶部横幅）/ `main` 主线（带章号、完成数 x/y）/ `side` 支线。首页和侧栏按这三区分组，区内按 `order` |
| `title` | 是 | 分类标题（可用占位符，显示时去掉颜色标签） |
| `subtitle` | 是 | 分类的一句话简介（≤ 18 个汉字 / 40 个英文字符）。入门横幅上直接显示；主线 / 支线格子放不下，只在悬停提示里显示 |
| `icon` | 是 | 物品 id |
| `if` | 否 | 条件（§1.6），不成立时整个分类不出现 |
| `advancement` | 否 | 完成这个进度才解锁：看得到标题、点不开，悬停写解锁条件（给数据包作者用；自带内容只有 `scrolls` 章用，残卷每卷一篇，读过才开） |
| `locked_hint` | 否 | 被 `advancement` 锁住时显示的解锁提示，代替默认的「完成进度「X」后解锁」（可用占位符和行内标记；悬停提示、动作栏里去掉颜色只留文字，锁着的阅读页照正文排）。隐藏进度（没有 display）默认只能说「完成某个进度」，锁在它上面的要写这句告诉玩家去哪做。**只能和 `advancement` 一起写**，zh_cn / en_us 都要有（validate 查）。一段固定文字，不能分条件 |
| `_draft` | 否 | `true` = 这个分类还是机械转换的草稿：validate 把「缺 `_src`」降成警告，并在结尾列出草稿分类数。第二段改写完删掉 |

**条目字段**

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 分类内唯一，`[a-z0-9_]+`。**骨架里的 id 不许改名**（§2）：进度、Ctrl 查阅、展示厅截图、链接都靠它 |
| `title` | 是 | 条目标题；介绍一台机器的条目写 `"{item:<机器 id>}"`，改名自动跟着变 |
| `icon` | 是 | 物品 id |
| `summary` | 是 | 左栏列表与搜索结果里的一行（≤ 24 个汉字） |
| `complete` | 否 | 进度 id。写了就是一「步」：玩家完成这个进度后侧栏打 ✓、标题块写「✓ 已完成」，首页格子角标计数（§3） |
| `optional` | 否 | `true` = 可选步骤：做完照样打勾，不会被指为「下一步」；分类里有必做步骤时不算进 x/y，**全是可选步骤的分类（支线）格子上的 x/y 数的就是可选步骤**（§3）。也可以写条件（**按条件可选**，写法和 `if` 完全一样，§1.6）：条件成立时是可选步骤，不成立时是必做步骤，例 `"optional": "!cfg:general.hoanna.portal_enabled"` = 海纳尔之门关着时可选、开着时必做。给「只在某个开关开着时才是主线必经」的步骤用：风之药水、受眷顾、风之石板在门开着时是去海纳尔的前提，门关着时第十六章整章藏掉，它们就没用处了——写死 `false` 会让门关着时首页把玩家指去做没用的事，写死 `true` 又会让门开着时首页不指它们。判定不了（键不存在、写错）时和 `if` 一样按不成立处理，也就是当必做。只对写了 `complete` 的条目有意义。正文里门开着、关着两种说法分成两块、各带 `if`（开着那块直接写目标，关着那块写「可选」）。布尔不要加引号（`"true"` 能读，但 validate 报错） |
| `advancement` | 否 | 同分类的 `advancement`，只锁这一条 |
| `locked_hint` | 否 | 同分类的 `locked_hint`，这一条被自己的 `advancement` 锁住时显示（分类锁着时显示分类的） |
| `items` | 否 | 这个条目负责介绍的物品。物品提示上按住 Ctrl、手持手册潜行右键、点正文里的物品，都优先来这里（§1.4 末）。只有一部分物品受开关控制时，把它们放进**条件组**：`"items": ["ns:a", …, {"if": "cfg:general.hoanna.portal_enabled", "items": ["ns:b", …]}]`，`if` 写法同块级（§1.6），不成立时组里的物品不进查阅索引、搜索也搜不到它们的名字；组不能嵌套，必须写 `if`。整篇都受控制就给条目写 `if` |
| `if` | 否 | 条件；不成立时条目不出现、不算步骤、不当「下一步」，指向它的链接退化成普通文字 |
| `blocks` | 是 | 块数组（§1.3） |
| `_src` | zh_cn 必填（`_draft` 分类里缺了只警告）；en_us 可省略 | 本条目每个事实 / 数字的代码出处：`相对 com/flameforge/essentialcraft5 包的路径:行号 — 说明`，数据文件写 `data/…` 或 `assets/…` 路径。加载器忽略所有下划线开头的字段 |

### 1.3 块类型（`blocks` 数组里的元素）

所有块都可以带 `"if"`。所有「文本」字段都支持 §1.4 的行内标记。

**行级条件**：`list` / `steps` 的某一项可以写成 `{"if": 条件, "text": "…"}`；`kv` / `table` 的某一行可以写成
`{"if": 条件, "cells": [...]}`。条件不成立时这一项 / 行不显示（整块一行不剩则整块隐藏）。

**篇末翻页**：每篇末尾自动出现「上一篇 / 下一篇」卡片（按全书顺序：`start` → `main` → `side`，区内按 `order`；跨分类写成「下一章」），不用在内容里写。

| type | 字段 | 说明 |
|---|---|---|
| `h` | `text` | 小标题 |
| `p` | `text` | 段落。`\n` 换行。一段只讲一件事，**显示出来超过 150 字（物品名按游戏里的名字算）validate 给警告** |
| `list` | `items: [文本…]`，`ordered?: bool` | 列表（默认圆点） |
| `steps` | `items: [文本…]` | 操作步骤（带序号徽记的竖向流程），用于「怎么做」 |
| `kv` | `rows: [[标签, 值], …]` | 两列键值表，适合列机器数值 |
| `table` | `header: [文本…]`，`rows: [[文本…]…]`，`widths?: [权重…]` | 多列表格；每行列数 = header 列数 |
| `tip` | `style: "info"\|"warn"\|"good"\|"bad"\|"lore"`，`title?`，`text` | 提示框。`info` 天青、`warn` 杏橙、`good` 薄荷绿、`bad` 蔷薇粉。`lore` =「源质小知识」（原作研究书纸页的米白；所有提示框的底都是同一块比阅读底亮半档的低饱和灰紫 #25212C，样式只体现在左色条、标题、右上切角；不写 title 时自动用这个标题，en「EssentialCraft Lore」）：讲原作里的来历，不承载玩法信息，**每篇最多 1 个**（validate 报错）。KuvaLich 的 `wf` 在 EC5 不认 |
| `formula` | `text` | 公式 / 计算式，单独一框高亮 |
| `items` | `items: ["ns:id", …]`，`caption?`，`per_row?: 整数` | 一排物品图标 + 名称；`per_row` 指定每行几个（默认按宽度排）。悬停出物品提示，**点击跳到介绍它的条目**（§1.4 末的索引） |
| `recipe` | 见下 | EC5 配方图 |
| `structure` | 见下 | 多方块结构图 |
| `ritual` | 见下 | 地面仪式图（俯视单层 + 丢什么、出什么、几率） |
| `image` | `texture`，`width?`，`height?`，`caption?` | 插图 |
| `entity` | `id: "ns:entity_type"`，`height?: 像素(默认 64，24~200)`，`nbt?: "SNBT"`，`scale?: 0.25~2(默认 1)`，`name?: "语言键"` | 实体预览（鼠标在阅读区里时模型随鼠标转头，不在时停在侧身）；失败时退化为名字。`nbt` 在预览实体建好后合并进去，用来摆精英变体、颜色（键名照那只怪自己的存档字段写，同 `/summon` 的 nbt；位置、速度、朝向、UUID、乘客、拴绳、维度这几个键不合并），写错只记一条警告、照默认样子画；`scale` 在自动适配框高之后再乘，模型比碰撞箱大得多的首领填 0.5 之类；框下的名字默认是实体类型名，`nbt` 摆出的变体换了名字的（风法师 `{Type:3b}` 是风暴术士、精英有自己的名字）要写 `name`，填那个变体的名字键（如 `entity.essentialcraft5.wind_mage.storm`），四种语言写同一个键 |
| `divider` | — | 分隔线 |
| `quote` | `text`，`by?` | 题记：章首引一段先辈的残卷 / 碑文，只造气氛、不承载玩法信息。左边一道米白细竖线，正文缩进，出处另起一行右对齐、自动加「—— 」。一章至多一处，放在第一篇最前面 |

#### `recipe`：配方图

```json
{ "type": "recipe", "item": "essentialcraft5:soul_stone" }
{ "type": "recipe", "items": ["essentialcraft5:a", "essentialcraft5:b"] }
{ "type": "recipe", "recipe": "essentialcraft5:magician_table/xxx" }
{ "type": "recipe", "recipe_type": "essentialcraft5:mithriline_furnace", "compact": true }
{ "type": "recipe", "recipe_type": "essentialcraft5:mithriline_furnace", "items": ["minecraft:gold_ingot"] }
{ "type": "recipe", "item": "essentialcraft5:moon_well", "optional": true }
```

| 字段 | 说明 |
|---|---|
| `item` / `items` | 从客户端 RecipeManager 现查**产出**这些物品的配方，全部画出来（`item` 是 `items` 只有一个时的简写；两个都写时合并） |
| `recipe` | 按配方 id 画这一条 |
| `recipe_type` | 列出这个配方类型的全部配方；同时写了 `item(s)` 时只列产出这些物品的 |
| `compact?` | `true` = 登记过画法的配方画成一行一条（原料 → 产物 + 第一行说明），转化表用 |
| `optional?` | `true` = 找不到配方时整块不显示、不报日志（给以后才有配方的东西）。没写又找不到时也整块隐藏，但进服后日志报一次「[指南] 配方块 … 找不到配方」 |
| `caption?` | 图下的一行说明 |

认得的配方类型：工作台、魔力工作台、辐射仓、熔炉（内置，`RecipeFinder`），以及模块用 `GuideRecipeViews.register` 登记过画法的类型
（目前：米斯莱熔炉、矿石加工 `essentialcraft5:ore_smelting`、风）。一条放得下就并排画多个。按产物查靠配方的 `getResultItem`。

#### `structure`：结构图

三种写法，**只选一种**：

```json
{ "type": "structure", "structure": "essentialcraft5:mithriline_furnace" }

{ "type": "structure",
  "key": { "P": "essentialcraft5:mithriline_plating", "F": "essentialcraft5:mithriline_furnace" },
  "anchor": "F",
  "layers": [
    { "pattern": ["  P  ", " PPP ", "PP PP", " PPP ", "  P  "] },
    { "pattern": ["P P P", "     ", "P F P", "     ", "P P P"], "label": "机器层" },
    { "pattern": ["P   P", "     ", "     ", "     ", "P   P"], "key": { "P": "minecraft:gold_block" } }
  ],
  "caption": "…" }

{ "type": "structure", "pattern": ["BBB", "BCB", "BBB"], "key": { "B": "…", "C": "…" } }
```

| 字段 | 说明 |
|---|---|
| `structure` | 画 `StructurePatterns` 里登记的定义（机器判定和书用同一份数据）。按 y 分层（整层没有要求的层跳过，机器所在层总画），层标题写「高度：±n」；可选格子画淡、图例写「（可选）」；图例文字取规则自己的图例键 |
| `layers` | 多层，**从下到上**。各层的行数、最长一行必须一样（空位用空格补齐），validate 报错。层里可以有自己的 `key`（覆盖同名字符）和 `label`（层标题后面的说明） |
| `pattern` | 单层俯视，上北下南，每行一个字符串，空格 = 不用管 |
| `key` | 字符 → 规则。值可以是方块 id、`"#方块标签"`、`"air"`（必须是空的），或对象 `{"block": id 或 [id…], "tag": id, "air": true, "label": 文字}` |
| `anchor?` | 机器所在的字符：那一格四角是静止的品红饰角（阅读栏里不放呼吸之类的常驻动画），别的层在同一竖列四角点淡薰衣草；层标题写相对机器的**高度**。没写时层标题写「第 1 层（共 3 层）」 |
| `caption?` | 图下的一行说明 |

- **`label` 写文字**（按语言写在各自的文件里），也可以写原版或 EC5 的**非手册**语言键（例如 `structure.essentialcraft5.rule.any_block`），界面用 `Language.getOrDefault` 取：是键就翻译，不是键就原样显示。
  **不许写 `guide.essentialcraft5.*` 键**（这些键要删，validate 报错）。
- 排版：默认是**立体图**——斜 45° 俯视，左键拖动旋转，右上角「复位」；超过一层时顶栏有层按钮（「全部」+ 各层，点哪层只画到那一层）；
  悬停一格描框出提示，点一下跳到介绍它的条目；图例在图下方，列全部方块并写「×N」（要几个），悬停图例时模型里这种方块全部描框。
  方块模型是空的、靠方块实体渲染器画的方块（水晶柱、元素水晶、魔力镜）要让渲染器实现 `StaticBlockPreview` 给一帧静止画面（阅读栏里不放常驻动画；元素水晶新建时大小是 1，不实现就几乎看不见）。改了结构图用 `runClient -PecShowroomStruct` 只拍立体图（`ec5_03_guide_<轮>_s3d_*.png`）。
  顶上「平面」切回分层俯视图（全手册共用这个选择）：不超过 2 层时并排；超过 2 层加层切换条（点击或滚轮切层，默认显示 `anchor` / 机器所在的层），图例放在图下方，只列当前层用到的字符。
- 多层图不画 `throw / result`（仪式用 `ritual`）。

#### `ritual`：仪式图

```json
{ "type": "ritual",
  "pattern": [" R ", "RCR", " R "],
  "key": { "C": "minecraft:netherrack", "R": { "block": "minecraft:lava", "label": "流动的也行" } },
  "throw": "minecraft:blaze_powder",
  "result": "essentialcraft5:fire_drops_cluster",
  "chance": 0.8 }
```

`pattern` / `key` 同 `structure` 的单层写法；正中那格高亮。`throw`（要丢的物品，必填）、`result?`（产物）、`chance?`（0~1 的小数，显示成百分比）。

#### `image`：插图

`texture`（资源路径，如 `essentialcraft5:textures/special/bookicons/mru_lowest.png`）、`width` / `height`（贴图里要画的区域像素，默认 256），按阅读宽度等比缩小，不放大。

### 1.4 行内标记

**颜色 / 强调标签**（可嵌套，必须成对闭合；坏标记照字面显示，不会崩）：

| 标签 | 语义（EC5 配色） |
|---|---|
| `<hl>…</hl>` | 关键术语 / 重点（暖白加粗，比正文的米白亮一档、不用纯白，和 `<em>` 看起来一样；品红只留给能点的链接，别滥用） |
| `<t>…</t>` | 冰蓝：数值、名词、界面元素名（和小标题、表头的薰衣草分得开） |
| `<w>…</w>` | 杏橙：注意、代价 |
| `<g>…</g>` | 增益 / 好处（薄荷绿） |
| `<b>…</b>` | 减益 / 坏处 / 危险（蔷薇粉；**和平衡值「混沌」的珊瑚红是两种颜色**，同一段里都可能出现） |
| `<dim>…</dim>` | 次要说明（带一点紫的灰，对阅读底 6.9:1） |
| `<em>…</em>` | 强调（暖白加粗）。旧格式的 `**x**` 一律改成它 |
| `<c:RRGGBB>…</c>` | 指定颜色（**取代码里真实用的颜色**，和物品提示一致） |

**占位符**：

| 写法 | 结果 |
|---|---|
| `{cfg:键}` / `{cfg:键\|格式…}` | 配置值，格式链见下 |
| `{item:ns:id}` | 物品的本地化名称（丁香紫 + 点线下划线）；悬停出物品提示，点击跳到介绍它的条目 |
| `{block:ns:id}` | 方块名，给没有物品形态的方块用（岩浆、水、火）；有物品形态的同样可点 |
| `{balance:frozen}` / `{balance:pure}` / `{balance:chaos}` | 平衡值三态的名字，带各自颜色 |
| `{keybind:按键名}` | 玩家当前绑定的按键（查阅键 `key.essentialcraft5.guide_lookup` 默认不绑定，别在正文里用它） |
| `{lang:翻译键}` | 语言文件里的文字（保证和游戏里的叫法一致）。**不许用来引用 `guide.essentialcraft5.*` 键** |

`{cfg:…}` 的显示与格式链照 KuvaLich：

- 数字默认**最多保留 2 位小数、四舍五入**（HALF_UP），去掉多余的 0；布尔 → 开启 / 关闭（en on/off）；列表 → 用「、」连接（en `, `），空列表 → 无（en none）。
- 格式链从左到右：运算 `mul:N` / `div:N` / `add:N` / `min:N` / `max:N` / `addcfg:键` / `round:D` / `sign`，最后一个是显示格式（省略 = `raw`）。
  `min:N` 取当前值和 N 里小的那个，给代码里有上限的数用（例：模块槽数 `add:1|min:4`，基础槽数配成 4 时阶加成不会写成 5）；`max:N` 反过来，给代码里有下限的数用（例：冷却减到 0 为止 `max:0`）。
  `addcfg:键` 加上另一项数字配置的值，给「0 阶的数 + 阶 × 每阶多的数」这类两项配置合成的数用：
  `{cfg:general.verdict_blade.judgment.cooldown_per_stage|mul:-3|addcfg:general.verdict_blade.judgment.cooldown|max:0|sec}`（裁决之刃 3 阶的断罪冷却）。
- 列表键取其中一项写 `at:N`（N 从 0 数），只能放在格式链最前面，后面的运算和格式都作用在取出的那一项上：
  `{cfg:general.matrix_rank.resonance.trait_level_multipliers|at:1}`（相性 II 的倍数）、`{cfg:general.computer_armor.stage_price_multipliers|at:4|pct}`（宅男套装 4 阶的单价倍数）。
  列表没有这一项时显示警告色的「?」（各配置自己少写一项时怎么补不一样，手册不猜）；`validate_guide.py` 按默认值查下标够不够。
- 代码里按阶取值、列表短了沿用最后一项的配置（风之护甲的 `deflect_cooldown_ticks`、`air_jumps`）写 `stage:N`：写法和位置同 `at:N`，
  列表比 N 短时取最后一项（服主只填 `[0]` 时每一阶都是 0），空列表显示「?」。例：`{cfg:general.wind_armor.air_jumps|stage:2}`（全套 2 阶的空中跳次数）。

| 显示格式 | 例 |
|---|---|
| `raw` | 原样 |
| `int` | 四舍五入取整 |
| `pct` | 值是小数：×100 加 %（0.25 → 25%） |
| `pctv` | 值本身是百分数：直接加 %（25.0 → 25%） |
| `x` | 加 ×（1.5 → 1.5×） |
| `sec` | tick → 秒（200 → 10 秒 / 10s） |
| `bool` | 开启 / 关闭 |
| `yesno` | 是 / 否 |
| `list` | 列表连接 |
| `count` | 列表长度 |

- `round:D` 作用在当前这一步的值上；要按百分比舍入写 `mul:100|round:D|pctv`。`sign` 给正数补「+」，可正可负的加成一律用它，别写死「+」。
- **键名是完整的点分路径**：`general.<小节>.<项>` 或 `machines.<机器>.<项>`（例：`machines.enrichment_chamber.min_size`、`general.baubles.disabled`）。
  全部键列在 `config_keys.json`（§1.5），validate 按它查键名，也查格式和类型对不对得上（数字格式用在布尔键上会报错）。
  **写之前在源码里确认单位**（tick 还是秒、0~1 还是 0~100），选对 `sec` / `pct` / `pctv`。缺键时界面显示警告色的「?」。

**链接**：`[[分类id/条目id]]`（显示目标条目标题）、`[[分类id/条目id|显示文字]]`、`[[分类id]]`（跳到分类的第一个可见条目）。取代旧的 `{entry:…}`。

**自动补空格**：占位符和链接展开出的文字首尾是字母或数字、又紧挨着汉字时，引擎自动补一个半角空格（判断时跨过 `<t>` 这类标签；原文已经有空格的不重复补）。
所以 `做一个{item:essentialcraft5:mru_coil}吧` 直接这样写，**占位符两边不用手动加空格**。普通文字里的中英混排仍照 `tools/lang/glossary.md` 第一节自己写空格。

**转义**：`\{`、`\[`、`\<`（写在 JSON 里是 `"\\{"`）。

**禁止**：`§` 颜色码、`**x**`、`{entry:…}`、帕秋莉的 `$(…)` 宏、HTML。

**物品 → 条目的索引**（Ctrl 查阅、潜行右键、点物品跳转共用）：同一个物品被多处提到时按优先级取第一个——
① 条目 `items` 里写了的（这篇就是介绍它的；条件组不成立的不算）；② `recipe` 块写明的产物（`item` / `items`；只写 `recipe_type` / `recipe` 的块不收）；③ `items` 块、`ritual` 的产物（只是提到）。
同一优先级按全书顺序取先出现的。**只收可见的条目和块**（被 `if` 藏掉的不算），跳转时滚到对应的块并让它亮一下。
想让某个物品的查阅落到哪篇，就把它写进那篇的 `items`。

### 1.5 配置键表 `config_keys.json`

`runData` 时 `datagen/ConfigKeysProvider` 遍历 EC5 的 SERVER 配置规格，导出到
`src/generated/resources/assets/essentialcraft5/guide/config_keys.json`：

```json
{ "_comment": "…", "keys": {
  "machines.enrichment_chamber.min_size": { "type": "int", "default": 3, "min": 3, "max": 32 },
  "general.baubles.disabled": { "type": "list", "default": [] },
  "machines.solar_prism.requires_midday": { "type": "boolean", "default": true }
} }
```

`type` ∈ `boolean / int / long / double / string / enum / list`；数值类有 `min` / `max`（规格里写了范围时），枚举有 `values`，写了注释的有 `comment`（中英两行，常写着单位）。
**加了、改了配置项要重跑一次 runData**，否则 validate 会说键不存在。

### 1.6 条件表达式（`if`）

每一项是下面之一，前面可加 `!` 取反：

| 写法 | 成立条件 |
|---|---|
| `cfg:键` | 布尔为 true / 数字 ≠ 0 / 列表非空 / 字符串非空 |
| `cfg:键>N`（也可 `>=` `<` `<=` `==` `!=`） | 数值比较 |
| `cfg:键\|at:N`、`cfg:键\|stage:N`（后面同样可以接比较，例 `cfg:general.mhs_armor.shares_chestplate\|at:0==2`、`cfg:general.wind_armor.air_jumps\|stage:2>0`） | 列表键先取一项再判，取法同 §1.4 的格式链（`at:N` 取第 N 项、0 起，越界取不到；`stage:N` 短了沿用最后一项）；取不到（不是列表、`at:N` 越界、空列表）按判定不了处理，取反也不成立 |
| `mod:modid` | 客户端装了这个模组（例如 `mod:curios`） |
| `bauble:ns:id` | 这件饰品**没有**被 `general.baubles.disabled` 禁用（id 按 `ResourceLocation` 解析后比较，和游戏里的判定一致）。id 必须是本模组的饰品 |
| `module:ns:id` | 这块宅男套装模块**没有**被 `general.computer_modules.disabled` 关掉（写法同 `bauble:`，id 按 `ResourceLocation` 解析后比较）。id 必须是本模组命名表里的物品（模块的注册名，例 `module:essentialcraft5:rewind_module`）；第二十一章的十篇模块说明各带一个 |
| `gamemode:creative` | 本地玩家现在是创造模式（有创造物品栏、一下拆方块的那套能力；旁观模式不算）。切换游戏模式后书整份重建。给只想让创造模式玩家看到的内容用：带它的分类 / 条目生存玩家首页看不到、按住 Ctrl 也查不到，别的章链过去时链接处也要带这个条件。现在没有内容用它（`creative` 章不带条件） |

- 数组 = 全部成立；一个字符串里用 `||` 连接几项 = 任一成立（每一项仍可带 `!`）。
- 条目的 `optional` 写成条件时（按条件可选，§1.2）用的是同一套写法和求值。
- 常用开关直接用 `cfg:`，例如 `machines.<机器>.generate_corruption`、`machines.mru_coil.hurt_players`、`machines.solar_prism.requires_midday`、
  `machines.cold_distillator.damage_entities_around`、`machines.reactor.damage_mobs`、`general.wind_mage.max_nearby>0`。

用法：能整体关掉的玩法给对应的块 / 条目 / 行加条件，**直接藏掉，不写替代说明**。
关掉后游戏换了一种行为的，用 `"if": "!cfg:…"` 的块**正面描述那种行为**。
正文不写「服务器 / 服主 / 服务端 / 配置 / 同步 / 本服 / 已关闭」这类字眼（单人游戏也会读到），`meta_scan.py` 必须 0 命中。
链接指向的条目带条件时，链接所在的块 / 行也要带上同样的条件（否则目标藏掉后链接退化成普通文字，句子却还在）。

---

## 2. 分类骨架（固定 id）

跨分类链接只能指向这里列出的 id。**条目 id 不许改名**；确认某功能在代码里根本不存在才能删，删了要在汇报里写明。
可以在自己分类里加条目（id 自取），但别的分类不会链接到它们。`validate_guide.py` 顶部的 `SKELETON` 和本表一致。

| 区 | 分类 id（order） | 标题 | 条目 id |
|---|---|---|---|
| 入门 | `start` (5) | 入门 | `overview` 模组概览；`mru`（原 basics/mru）；`mrucu`（原 basics/mrucu）；`glossary` 名词速查；`materials` 材料速查（吸收原 basics/mithriline）；`faq` 常见误区；`numbers` 数值速查（kv 表全用 `{cfg:}`） |
| 入门 | `matrix` (6) | 矩阵阶位速查 | `overview` 你的矩阵；`codex` 矩阵图谱；`crucible` 淬炼台与试炼；`ranks` 各阶的条件（`table`，配置数值走 `{cfg:}`，阶数据里的数照 `matrix_ranks/rank_<n>.json` 写）；`alignment` 相性与矩阵特性；`reservoir` 矩阵容量与虹吸；`tolerance` 耐受与矩阵稳固（第十三批 13a，batch13.md §2.8；全是说明条目，阶的步骤写在各章末尾：第二章 `guide/matrix/crucible`、`guide/matrix/rank_1`，第五、十、十二、十五、十七、十九章各一阶，第十二章另有 `guide/pentacle/condensed`，第十五章的五阶排在 `after_main_line` 前面；阶的步骤带 `if cfg:general.matrix_rank.enabled`，关掉矩阵阶时藏掉、主线照样连通） |
| 主线 | `elements` (10) | 第一章 · 元素 | `book` `ores` `rituals` `elemental_gem` `parts` |
| 主线 | `mru` (20) | 第二章 · 第一份 MRU | `soul_stone` `ubmru` `matrix_absorber` |
| 主线 | `crafting` (30) | 第三章 · 辐射与合成 | `bound_gem` `radiating_chamber` `corruption` `fortified` `magician_table` `magician_crafts` |
| 主线 | `generators` (40) | 第四章 · 产能器 | `heat_generator` `natural_furnace` `projections` `cold_distillator` `ray_tower` |
| 主线 | `storage` (50) | 第五章 · 储存与升级 | `crystals` `soul_storage` `charging_chamber` `upgrades` |
| 主线 | `alloys` (60) | 第六章 · 中期材料 | `magical_essence` `crystal_dust` `purified_alloys` `combined_magical_alloys` `resonating_crystal` `pale` `plating` `crystal_lamps` |
| 主线 | `enrichment` (63) | 第七章 · 富集仓 | `controller` `io` `holding_chamber` `monitoring` `intersector` |
| 主线 | `crystal` (66) | 第八章 · 元素水晶 | `crystal_former` `elemental_crystal` `moon_well` |
| 主线 | `essence` (70) | 第九章 · 精华与焦点 | `crystal_extractor` `essences` `fuel_and_focus` `crystal_controller` `elemental_decomposer` `magical_lantern`（`elemental_decomposer` 第十二批加：元素分解器，可选步骤，`complete` 是 `guide/essence/elemental_decomposer`，batch12.md §4.8.7） |
| 主线 | `solar` (74) | 第十章 · 日光 | `sun_imbued_glass` `sun_ray_absorber` `solar_prism` `ender_generator` |
| 主线 | `mithriline` (78) | 第十一章 · 米斯莱与 ESPE | `mithriline_ingot` `crystal_pillar` `furnace` `conversions` |
| 主线 | `pentacle` (79) | 第十二章 · 腐化与灵魂五芒星 | `pentacle` `cure` `pale_block` `pale_crystal_pillar` `tier_one` `tier_two` `tier_three` `ec_balancer`（`tier_two`、`tier_three` 第七批加，要虚空 / 恶魔柱：都设 `optional: true`、带 `if cfg:general.hoanna.portal_enabled`，这一章排在海纳尔前面，不设可选会被首页指成「下一步」） |
| 主线 | `wind` (80) | 第十三章 · 风 | `wind_rune` `wind_gem` `air_potion` `windbound` `wishes` `wind_horn` `archmage` `wind_tablet`（`wishes`、`wind_horn`、`archmage` 第十批 10b 加：风之心愿、风之号角、召来的大法师，带 `if cfg:general.hoanna.portal_enabled`，排在 `wind_tablet` 前面，batch10.md §5.5） |
| 主线 | `automation` (81) | 第十四章 · 自动化 | `rules` `crafter` `crafting_frame` `assembly_line` `mimic` `redstone_transmitter` `metadata_manager`（`assembly_line` 第十批 10b 加：产线，必做，batch10.md §5.6.3） |
| 主线 | `logistics` (82) | 第十五章 · 物流 | `magical_hopper` `filters` `advanced_filters` `magical_chest` `void_chest` `magical_display` `magical_mirror` `after_main_line`（`advanced_filters`、`void_chest` 第六批 6b 加，要海纳尔的虚空材料：都设 `optional: true`、带 `if cfg:general.hoanna.portal_enabled`，免得首页把它们指成「下一步」） |
| 主线 | `hoanna` (97) | 第十六章 · 海纳尔 | `legend` `wind_name` `open_gate` `crossing` `way_home` `sky` `lands` `flora` `radiation` `events` `ruins` `relics` `void_stone` `void_plating` `collapse`（第六批 6a，batch6a.md §9；order 97 排在主线最后，85 已被 `gear` 占；整章 `if cfg:general.hoanna.portal_enabled`；`collapse` 第十批 10b 加：聚集体坍缩，必做，排在 `void_plating` 之后，batch10.md §5.6.4） |
| 主线 | `demons` (100) | 第十七章 · 恶魔 | `void_crystal_pillar` `demonic_pentacle` `summoning` `demon` `trade` `soul_scriber` `ackronite` `purge` `demonic_plating`（第七批，batch7.md §7；98、99 已被两个支线章占，100 在主线区排在海纳尔后面；整章 `if cfg:general.hoanna.portal_enabled`；暗渎水晶柱只放创造栏、不写条目，恶魔刷怪蛋写进 `demon` 条目的 `items`；`purge` 第十批 10b 加：2 档五芒星，必做，排在 `ackronite` 之后，batch10.md §5.6.5） |
| 主线 | `hologram` (101) | 第十八章 · 全息 | `neuronic_plating` `holopad` `summoning` `hologram` `decoy` `math_cores` `orbital_remote` `divider_gun`（第八批，batch8.md §8.1；整章 `if cfg:general.hoanna.portal_enabled`；`math_cores` 是说明条目；`orbital_remote`、`divider_gun` 只有 Boss 稀有掉落，设 `optional: true`；两个刷怪蛋写进 `hologram` 条目的 `items`（故障核心第十批 10c 起写在 `inquisitorium/glitchy_core`，不再列在这里）；审判庭套装写在 `inquisitorium/armor`；`decoy` 第十批 10b 加：全息诱饵，打倒全息投影以后的可选步骤，batch10.md §5.8.2） |
| 主线 | `inquisitorium` (103) | 第十九章 · 审判庭 | `legend` `glitchy_core` `decoding` `journey` `beacon` `sentinel` `purify` `chip` `armor`（第十批 10c，batch10.md §5.10；副标题「循着回声找到审判庭」、图标 `inquisitorium_chip`，整章 `if cfg:general.hoanna.portal_enabled`；`legend`、`chip` 是说明条目；`purify` 是 3 档五芒星、必做，`complete` 复用 `guide/pentacle/tier3`；`sentinel` 的 `complete` 是 `main/inquisitorium`；审判庭套装生存可得（D22 A），`armor` 是这一章的可选步骤，`complete` 是 `guide/inquisitorium/armor`） |
| 主线 | `computer` (104) | 第二十章 · 宅男套装 | `force_parts` `force_field` `drives` `senses` `computer_engine` `suit` `antigravity_board` `seal_field` `force_dome` `costs` `epilogue`（第八批，batch8.md §8.2；整章 `if cfg:general.hoanna.portal_enabled`；第十批 10c 起 order 102 → 104、标题改「第二十章」，`suit` 不再是主线最后一步（指向第二十一章），`epilogue` 改成过渡；`antigravity_board` 可选、带 `bauble:` 条件；`costs`、`epilogue` 是说明条目；`seal_field` 第十批加：饰品封禁场与宅男终端，可选步骤，带 `if cfg:general.computer_seal.enabled`，`complete` 是 `guide/computer/seal_field`，batch10.md §5.4；`force_dome` 第十批 10c 加：力场穹顶，可选步骤，`complete` 是 `guide/computer/force_dome`，batch10.md §5.13） |
| 主线 | `terminal` (105) | 第二十一章 · 终端与模块 | `legend` `blank_module` `install` `modules` `rewind` `matrix_filter` `matrix_shunt` `accumulator` `shred` `phase_anchor` `attraction` `stride` `steady_hand` `veil` `epilogue`（第十批 10c，batch10.md §4.3、§5.11、§5.12、§5.14，第十二批 batch12.md §4.7.8；副标题「给宅男套装装上本事」、图标 `blank_module`，整章 `if cfg:general.hoanna.portal_enabled`；`blank_module`（`guide/terminal/blank_module`）、`modules`（`guide/terminal/modules`：身上两块模块生效）是必做步骤；`legend`、`install`（怎么装 / 拆 / 开关，配方书里看不到）是说明条目；十块模块各一篇说明条目，各带条件 `module:essentialcraft5:<模块注册名>`；`epilogue` 是尾声；13e 起改成过渡，指第二十二、二十三章） |
| 主线 | `shade` (109) | 第二十二章 · 暗影 | `intro` `attune` `umbral` `shade_core` `shade_pillar` `tier4` `oath` `rank_8`（第十三批 13e，batch13.md §2.2、§2.8、§3.1.3、§3.4、§3.5；副标题「影晶、暗影核心与 4 档五芒星」、图标 `shade_core`，整章 `if cfg:general.hoanna.portal_enabled`；必做步骤 `guide/shade/{attune, umbral, shade_core, shade_pillar, oath}`、`guide/pentacle/tier4`、`guide/matrix/rank_8`（`rank_8` 整篇带 `if cfg:general.matrix_rank.enabled`）；`intro` 是说明条目；`tier4` 的立体图引用结构 `essentialcraft5:player_pentacle_tier4`；`shade` 在 `NEW_CONTENT` 里，不进迁移表） |
| 主线 | `apex` (110) | 第二十三章 · 九阶 | `intro` `beacon` `summon` `fight` `rewards` `rank_9` `ascension` `epilogue`（第十三批 13e，batch13.md §2.2、§2.8、§5.3.5；副标题「天灾信标、天灾之心与九阶」、图标 `calamity_remnant`，整章 `if cfg:general.hoanna.portal_enabled`；必做步骤 `guide/apex/beacon`、`main/calamity_heart`（写在 `fight`，有 display 的挑战进度）、`guide/matrix/rank_9`（`rank_9` 整篇带 `if cfg:general.matrix_rank.enabled`）；`ascension`（同调 5 阶指路，带 `if cfg:general.attunement.enabled`）没有进度、是说明条目；`epilogue` 是尾声、主线终点，首页「主线已完成」门开着时指向它；`apex` 在 `NEW_CONTENT` 里，不进迁移表） |
| 支线 | `ley` (83) | 支线 · 地脉投影 | `ley_projector` `ley_lens` `ley_condenser` `condensing`（后两条第九批加：地脉凝聚器与价格表，都带 `if [cfg:machines.ley_condenser.enabled, cfg:general.hoanna.portal_enabled]`，`ley_condenser` 设 `optional: true`；分类级 `if` 改成 `cfg:machines.ley_projector.enabled\|\|cfg:machines.ley_condenser.enabled`，前两条各自带投影仪的开关） |
| 支线 | `farming` (84) | 支线 · 农牧自动化 | `item_shuffler` `breeder` `shearing_station` `animal_separator` `magical_cultivator`（`magical_cultivator` 第十二批加：魔力栽培器，可选步骤，`complete` 是 `guide/farming/magical_cultivator`，batch12.md §4.2.7） |
| 支线 | `gear` (85) | 支线 · 装备与防护 | `magic_monocle` `mru_mover_t2` `mrucu_container` `mhs_armor` `void_armor` `elemental_tools` `projection_use` `magical_digger` `essential_tools` `pale_gem` `ley_detector` `homeward_compass`（`void_armor` 第七批加，带 `if cfg:general.hoanna.portal_enabled`；`essential_tools`、`ley_detector`、`homeward_compass` 第十二批加（batch12.md §8）：源质镐与源质斧、地脉探测仪、归途罗盘，都是可选步骤，`complete` 分别是 `guide/gear/essential_tools`、`guide/gear/ley_detector`、`guide/gear/homeward_compass`（拿到物品）） |
| 支线 | `devices` (86) | 支线 · 魔力器械 | `magical_repairer` `magical_enchanter` `potion_spreader` `magical_jukebox` |
| 支线 | `magmatic` (87) | 支线 · 矿石加工 | `fortified_furnace` `furnace_upgrades` `smeltery` |
| 支线 | `corruption` (88) | 支线 · 腐化效果 | `matrix_damage` `body` `mind` `matrix` `paradox` `matrix_tuner`（`matrix_tuner` 第十二批加：矩阵调律器，可选步骤，带 `if [cfg:machines.matrix_tuner.enabled, cfg:general.hoanna.portal_enabled]`，`complete` 是 `guide/corruption/matrix_tuner`，batch12.md §4.6.7） |
| 支线 | `charms` (89) | 支线 · 护符与随身物品 | `charms` `consumables` `magical_wings` |
| 支线 | `staffs` (90) | 支线 · 法杖与武器 | `golden_orb` `magmatic_staff` `magnetizing_staff` `staff_of_life` `shields` `matrix_weapons` |
| 支线 | `wind_gear` (91) | 支线 · 风之装备 | `wind_tools` `wind_armor` |
| 支线 | `wind_mage` (92) | 支线 · 风法师 | `wind_mages` `bottled_winds` `wind_mage_armor` `steadfast` `deflect`（后两条第十批 10b 加：风之契约的不动如山、风之偏转，可选步骤，batch10.md §5.8.1） |
| 支线 | `baubles` (93) | 支线 · 特殊饰品 | `finding` `kill_ubmru` `ubmru_other` `combat` `harvest` `wind_corruption` `radiation` `mystical_accessory`（后两条第六批 6a 加：δ ε λ μ ξ、神秘饰品） |
| 支线 | `tesla` (94) | 支线 · 特斯拉：线圈与反应堆 | `mru_magnet` `mru_coil` `player_list` `reactor` |
| 支线 | `guns` (95) | 支线 · 魔力枪械 | `weapon_maker` `parts` `pistol` `rifle` `sniper` `gatling` `firing` `lens_effects`（`lens_effects` 第十批 10b 加：透镜的效果，说明条目，batch10.md §5.7.1） |
| 支线 | `decorations` (96) | 支线 · 装饰 | `magical_chisel` `patterns` `fancy_mimic` |
| 支线 | `void_machines` (98) | 支线 · 虚空机器 | `ultra_heat_generator` `ultra_natural_furnace` `magical_furnace` `magical_teleporter` `magical_builder`（第六批 6b，batch6b.md §7；紧接 `hoanna`、整章 `if cfg:general.hoanna.portal_enabled`；虚空岩浆熔炉进 `magmatic/furnace_upgrades`，虚空箱、高级过滤器进 `logistics`；MRU 跨维度收发器只放创造栏、不写条目；`ember_forge`、`ember_sword` 第七批加；`ember_inlay` 第十批 10b 加：余烬嵌刻，batch10.md §5.7.2） |
| 支线 | `creative` (99) | 支线 · 无尽道具 | `creative_items`（第六批 6b M5：没有配方、只放创造栏的五件无尽道具，生存里照常能用。独立成章、正文写明「没有合成配方」，不带游戏模式条件，所有玩家都看得到；条目没有 `complete`，不算步骤） |
| 支线 | `converter` (106) | 支线 · MRU 转换器 | `mru_converter` `energy` `mana` `upgrades`（2026-10-13，mru_converter.md §7：把 MRU 换成能量（FE）和植物魔法的魔力，本模组唯一的联动；整章 `if cfg:machines.mru_converter.enabled`；`mru_converter` 是唯一的步骤、可选，`complete` 是 `guide/converter/mru_converter`（四档任一）；`energy` 带 `cfg:machines.mru_converter.fe_per_1000_mru>0`，`mana` 带 `[mod:botania, cfg:machines.mru_converter.mana_per_1000_mru>0]`，魔力相关的句子都带 `mod:botania`；`upgrades` 是四档对照与配方；无尽档写在 `creative/creative_items`） |
| 支线 | `scrolls` (107) | 支线 · 古卷 | `scrolls` `preface` `book_1` `book_2` `book_3` `book_4` `book_5` `book_6` `complete_set`（第十二批，batch12.md §4.1.9、§10.1 D1：整章 `if cfg:general.ancient_scrolls.enabled`；`scrolls` 是说明条目，`items` 写 `ancient_scroll`；各卷是可选步骤，`complete` 和 `advancement` 都是 `guide/scrolls/<id>`（代码发的隐藏进度）、带 `locked_hint`，读过那一卷才打开；`book_5`、`book_6` 带 `if cfg:general.hoanna.portal_enabled`；`complete_set` 是可选步骤，`complete` 是 `guide/scrolls/complete`、不锁；门开 8 步 / 门关 6 步） |
| 支线 | `attunement` (108) | 支线 · 同调：让装备随你成长 | `overview` 同调是什么；`points` 同调度从哪来；`budget` 同调预算；`stage_up` 升阶；`transfer` 同调转移；`mhs_armor` 魔力防护套装的同调；`wind_armor` 风之护甲的同调；`void_armor` 虚空套装的同调；`inquisitorium_armor` 审判庭套装的同调；`computer_armor` 宅男套装的同调；`ember_sword` 余烬元素剑的同调；`verdict_blade` 裁决之刃（可选步骤，complete `guide/attunement/verdict_blade`）（第十三批 13a / 13b，batch13.md §2.8、§3.3、§4；条目 id 用同调家族名（标签 `essentialcraft5:attunement/<家族>` 的 `<家族>`）；分类级 `if` 只看 `cfg:general.attunement.enabled`（魔力防护、风之护甲门关着也做得出来），`void_armor`、`inquisitorium_armor`、`computer_armor`、`ember_sword`、`verdict_blade` 五篇各带 `if cfg:general.hoanna.portal_enabled`；第二十一章的必做步骤 `guide/terminal/attuned`（宅男四件都到同调 2 阶）写在 `terminal`、排在 `epilogue` 前面，带 `if cfg:general.attunement.enabled`；`terminal` 在 `NEW_CONTENT` 里，不进迁移表） |
| 支线 | `hoanna_explore` (111) | 支线 · 海纳尔探索 | `overview` 怎么用这一章（老存档走过的区块不补新东西）；`tide` 魔力潮汐；`specialties` 七种地貌的特产（可选步骤，complete `guide/hoanna_explore/specialty`，物品标签 `hoanna_specialties`）；`ley_storm` 地脉风暴（可选步骤 `guide/hoanna_explore/starfall`，带 `if [ley_storm_enabled, ley_storm_chance>0, hoanna_tide.enabled]`）；`calamity_windows` 天灾里的收成；`hotspots` 辐射热点；`survey` 勘测图与里程碑（可选步骤 `guide/hoanna_explore/survey_map`）；`survey_trip` 照着图走到（可选步骤 `guide/hoanna_explore/survey_arrive`）；`abyssal_salt` 渊盐；`midscapes` 营地、裂隙和陨坑；`nests` 巢穴（可选步骤 `guide/hoanna_explore/nest`）；`sealed_vault` 封印宝库、`sunken_district` 沉没城区、`inquisitorium_outpost` 审判庭前哨（13e，说明条目，没有进度：勘测图到点只发 `survey_arrive`、不分种类）（第十三批 13d，batch13.md §6、§9.4；整章 `if cfg:general.hoanna.portal_enabled`；全是可选步骤，不进主线、不出迁移表；五个进度都没有 display，`survey_arrive` / `starfall` / `nest` 由代码发） |
| 支线 | `bestiary` (112) | 支线 · 异兽录 | `overview` 掉落与精英（自己动手打死野外的怪才掉、同一类怪杀得越多掉得越少、精英、首领）；`elemental_wisp` `matrix_wraith` `storm_sorcerer`（主世界）；`nether_imp`（下界）；`void_stalker`（末地）；`frostbitten` `mana_prism` `chaos_aberrant` `ember_spirit` `hoanna_scavenger` `abyssal_eel` `inquisitorium_patroller` `catacomb_revenant`（海纳尔，13d，各带 `if cfg:general.hoanna.portal_enabled`；畸变幼体写在 `chaos_aberrant` 里，没有蛋）；`mru_golem` `matrix_shade`（首领，可选步骤，complete `guide/bestiary/<id>`，进度由代码发给打出一成以上伤害的参战者）（第十三批 13c，batch13.md §5.5；一种怪一篇说明条目：实体预览、出没、怎么打、掉落、精英；风暴术士是风法师的第四种，预览写 `id essentialcraft5:wind_mage` + `nbt {Type:3b}`；13d 在 `void_stalker` 后面加海纳尔一区；13e 在 `matrix_shade` 后面加 `town_sentry` `abyss_tyrant` `calamity_heart`（各带 `if cfg:general.hoanna.portal_enabled`、可选步骤 `guide/bestiary/<id>`），新条目接在本区末尾） |
| 支线 | `from_ec4` (120) | 附录 · 与原作的不同 | `overview` `where` `new` `changed` `removed` `fixed` `how`（第十批，batch10.md §5.2：写给玩过 1.12 原作的人，排在支线最后；全是说明条目，没有 `complete`；**不写条目 `items`、不用 `items` 块**，免得把物品的 Ctrl 查阅抢到附录来，物品一律写 `{item:}`；正文写原作叫法，搜原名能搜到；差异主表 `tools/plan/ec4_diff.md`） |

旧 `basics` 分类已并进 `start`：`basics/mru` → `start/mru`、`basics/mrucu` → `start/mrucu`、`basics/mithriline` → `start/materials`。

第一段（机械转换）时入门分类的内容是临时的，第二段 W1 重写：`start/materials` 还是原 basics/mithriline 的标题「米斯莱」和正文；
`overview`、`glossary`（7 行表格）、`faq`（5 条）、`numbers`（三张 kv 表，全用 `{cfg:}`，线圈两行带行级 `if`）是转换脚本里写的骨架，
内容只取自现有正文和配置注释，主要给第一段截图看 table / kv / cfg / 行级条件的版式。

---

## 3. 流程教程：打勾与「下一步」

- `main` 区的分类是教程的一章，章与章首尾相接；`side` 区是支线，`start` 区是入门与速查。
- 条目写了 `complete` 就是一步（进度一般用 `inventory_changed`「拿到过某物品」，放在 `data/essentialcraft5/advancements/guide/*.json`，没有 display；也可以直接用 `main/` 下的进度）。
  玩家完成后：侧栏 ✓、标题块「✓ 已完成」、首页格子角标 x/y；全书第一个没完成、没被藏掉、不是 `optional` 的步骤是「下一步」，首页底部条写「下一步：〈步骤〉（第 N 章）」。
- `optional: true` 的步骤：做完打勾，不当「下一步」（二选一的产能器、按需的射线塔）。分类里有必做步骤时，x/y 只数必做步骤；
  分类里全是可选步骤（支线）时，格子上的 x/y 数的是全部可选步骤；一步都没有的分类写「n 篇」。
  按条件可选的步骤（§1.2）按当前配置算：同一章在门开、门关时的 y 可以不一样。规则在 `client/guide/GuideSteps`，GameTest（`GuideDataTests`）用的是同一份。
- 只做标记，**不锁任何东西**（`advancement` 字段除外；自带内容只有 `scrolls` 章的各卷用它，`complete` 和 `advancement` 写同一个进度，读过那一卷才打开、同时打勾）。
- **老存档迁移**：主线加了新的必做步骤（新条目、可选改必做、换完成进度）以后，已经走过那一段的老玩家登录时会补上这些步骤的勾
  （`player/GuideMigration`，只补勾、不给物品）。迁移表 `data/essentialcraft5/guide_migration/v<N>.json` 由 `tools/guide/gen_migration.py`
  从 zh_cn 手册生成，基线是 `tools/guide/baseline_steps.txt`（4dacc01 时的 64 个必做步骤）；改了主线必做步骤就重跑它、提交生成的文件，
  漏了的话 `check_progression.py` 的迁移表覆盖检查会报。已经发出去的版本别改，要补的放进下一版（`GuideMigration.CURRENT_VERSION` 加一）。
- 每一步的正文结构：**目标 → 准备（配方 / 物品）→ 怎么做（`steps`）→ 做成的标志（`tip good`）→ 坑（`tip warn`）→ 进阶细节 → 想深入（链接）**。
  - 「做成的标志」写成 `{"type": "tip", "style": "good", "title": "做成的标志", "text": "…"}`（en 标题 `Done when`）。
  - 「下一步」**正好是全书顺序的下一篇时不写**（篇末翻页卡已经指过去了）；要指到别处时写成
    `{"type": "tip", "style": "info", "title": "下一步", "text": "…[[分类/条目]]…"}`（en 标题 `Next`）。

---

## 4. 写作要求（第二段改写）

详见方案 §3.4，要点：

- **以代码为准**，每个数字、每条规则在 `_src` 里写出处。
- **数值来自配置的一律 `{cfg:…}`**，只有代码里写死的常量才写字面数。`tools/guide/prep_rewrite.py` 会预先列出「字面数等于某个配置默认值」的候选和 `_src` 桩。
- 读者是整合包里的普通玩家，大多没玩过原作。原作术语第一次出现时用一句大白话讲清，或链到 `[[start/glossary]]`；原作来历放 `tip lore`，每篇最多 1 个。
- 每段 3 行左右，一段只讲一件事；能用 `steps` / `table` / `kv` 的别写成大段文字。机器数值用 `kv`，对照类内容用 `table`。
- 不写类名、方法名、NBT 键（只放 `_src`）；不写服主 / 配置相关的话。
- 英文和中文同一遍写完，照同一结构翻，术语照 `tools/lang/glossary.md`。
- 写完删掉分类上的 `_draft`，跑 validate / meta_scan 清零。

---

## 5. 工具（`tools/guide/`）

| 脚本 | 作用 |
|---|---|
| `guide_lib.py` | 下面几个脚本共用的名单与规则（路径、物品 / 方块 / 实体 / 饰品 / 配置键、纯文字化、字面数对照、条目 → 相关配置小节），不单独跑 |
| `validate_guide.py [分类…]` | 结构、中英一致、骨架、链接可达与条件一致、物品链接有落脚处（附录报错、别处警告）、附录三列表每格 ≤ 30 字、标签成对、占位符与格式、`if` 语法、配置键（读 `config_keys.json`）、物品 / 方块 / 实体 id、`bauble:` id、进度文件、结构 id、层尺寸、仪式字段、`order` 撞号、中文排版、`tip lore` 数、长段落、字面数等于配置默认值（警告）、结构标签不许是 `guide.essentialcraft5.*` 键。有错误退出码 1 |
| `meta_scan.py [分类…]` | 正文禁用词（服务器 / 服务端 / 服主 / 本服 / 配置 / 同步 / 已关闭…，中英）；另扫语言文件里的 `guide.essentialcraft5.*` 界面字样。必须 0 命中。另报警告（不算命中）：`optional: true` 的步骤首段写了「要用 / 要做完 / 必须」却没写「可选」 |
| `gen_migration.py [--dry-run \| --check] [--version N] [--book-dir 目录]` | 老存档迁移表 `data/essentialcraft5/guide_migration/v<N>.json`（版本号默认读 `GuideMigration.CURRENT_VERSION`）；规则写在脚本开头。`--make-baseline <提交>` 重新生成 `baseline_steps.txt`（一次性，已生成） |
| `make_traditional.py [--guide-only \| --lang-only] [--dry-run] [-v]` | `guide/zh_cn` → `guide/zh_tw`、`guide/zh_hk`，以及 datagen 生成的 `lang/zh_cn.json` → `src/main/resources/.../lang/zh_tw.json`、`zh_hk.json`（OpenCC，默认两样都做；**每次 runData 后重跑**）。转错的词写进脚本的 `OVERRIDES`，`-v` 列出没命中的 |
| `convert_guide.py` | 一次性：旧格式 → 新格式（第一段已跑过）。**旧格式文件 2026-09-29 已删，现在跑不了**，只留作记录；要重跑得先从 git 历史（6076df8）取回旧文件 |
| `prep_rewrite.py [分类…] [--out 目录]` | 第二段改写的输入清单：每条目的相关配置小节、`_src` 桩（类文件、配置定义处行号）、`{cfg}` 候选（字面数、上下文、建议写法、默认值、配置注释）。默认写到系统临时目录的 `ec5_guide_prep/`，改写时一起用，不进工程 |
