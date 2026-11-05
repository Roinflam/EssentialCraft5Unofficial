#!/usr/bin/env python3
"""
生成米斯莱熔炉的转化表（配方类型 essentialcraft5:mithriline_furnace）：

    python tools/gen/mithriline_furnace_recipes.py

输出到 src/main/resources/data/essentialcraft5/recipes/mithriline/furnace/<原料>_to_<产物>.json，脚本和结果都提交，改表后重跑。
先删掉目录里旧的 *.json 再写，表里删掉的条目不会留下旧文件。

来源：原作 common/registry/RecipesCore.registerMithrilineFurnace（L101-202，102 条），数值照抄：
  原料（矿辞 → 标签 / 物品，按 tools/oredict_map.json）、原料个数（原作 stackSize）、产物和个数、所需 ESPE（原作 energy）。
1.12 的 meta 物品逐条换成 1.20 的 id：木板 / 树苗 / 原木 / 树叶 0~5、红花 0~8、生鱼 0~3、熟鱼 0~1、头颅 0~4、
泥土:2（灰化土）、煤炭:1（木炭）、MELON_BLOCK（西瓜，方块）、REEDS（甘蔗）、12 张唱片。
按 D11 补 1.20 的新变种：木板 / 树苗 / 原木 / 树叶的循环里加红树林、樱花木（接在深色橡木后面），
花的循环里加矢车菊、铃兰（接在滨菊后面）；唱片只留原作 12 张。
黯淡水晶、黯淡水晶粉这一批不注册，产出或用到它们的两条带 forge:item_exists 预写，跟着以后的批次生效。

生成前检查：每种原料只出现在一条配方里（熔炉按原料找配方）；原料、产物个数不超过堆叠上限；
没有能刷数量的循环（沿着配方转一圈回来，数量只能相等或变少）。
"""
import glob
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'mithriline', 'furnace')

EC = 'essentialcraft5:'
MC = 'minecraft:'

# 以 # 开头的原料是物品标签（后面写代表物品，只用来起文件名和查循环），否则是物品 id
TAGS = {
    '#forge:dusts/glowstone': MC + 'glowstone_dust',
    '#forge:ingots/iron': MC + 'iron_ingot',
    '#forge:ingots/gold': MC + 'gold_ingot',
    '#forge:gems/diamond': MC + 'diamond',
    '#forge:gems/emerald': MC + 'emerald',
}

# 这一批还没注册的物品：用到它们的配方加 forge:item_exists 条件
NOT_YET = {EC + 'fading_crystal', EC + 'fading_dust'}
# 别的二批模块注册的物品：同样加条件，那个模块没接线时这条配方安静地不加载
OTHER_MODULES = {EC + 'mru_resonating_crystal'}

# 堆叠上限不是 64 的物品（查原料、产物个数）
STACK = {MC + 'ender_pearl': 16, MC + 'egg': 16}
DISCS = ['13', 'cat', 'blocks', 'chirp', 'far', 'mall', 'mellohi', 'stal', 'strad', 'ward', '11', 'wait']
for _disc in DISCS:
    STACK[MC + 'music_disc_' + _disc] = 1

# (原料, 原料个数, 产物, 产物个数, ESPE)
TABLE = []


def add(ingredient, count, result, result_count, espe):
    TABLE.append((ingredient, count, result, result_count, espe))


def cycle(ids, espe):
    """一圈一换一：ids[0] → ids[1] → … → ids[0]"""
    for i, item in enumerate(ids):
        add(item, 1, ids[(i + 1) % len(ids)], 1, espe)


def woods(suffix, overrides=None):
    # 原作 0~5：橡木、云杉、白桦、丛林、金合欢、深色橡木；D11 补红树林、樱花木
    names = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry']
    overrides = overrides or {}
    return [MC + overrides.get(n, n + suffix) for n in names]


# ---- 材料（原作前 9 条）----
add(EC + 'mithriline_dust', 1, EC + 'mithriline_ingot', 1, 60)
add(EC + 'mru_resonating_crystal', 1, EC + 'mithriline_crystal', 1, 120)
add(EC + 'mithriline_crystal', 1, EC + 'fading_crystal', 1, 240)
add(EC + 'fading_crystal', 1, EC + 'fading_dust', 8, 480)
add('#forge:dusts/glowstone', 1, EC + 'mithriline_dust', 1, 32)
add('#forge:ingots/iron', 8, MC + 'gold_ingot', 1, 64)
add('#forge:ingots/gold', 1, MC + 'iron_ingot', 8, 64)
add('#forge:gems/diamond', 2, MC + 'emerald', 1, 512)
add('#forge:gems/emerald', 1, MC + 'diamond', 2, 512)

# ---- 木头、植物 ----
cycle(woods('_planks'), 1)
cycle(woods('_sapling', {'mangrove': 'mangrove_propagule'}), 1)
cycle(woods('_log'), 1)
cycle(woods('_leaves'), 1)
cycle([MC + 'melon', MC + 'pumpkin'], 1)
# 蒲公英 → 红花 0~8（虞美人、兰花、绒球葱、蓝花美耳草、四色郁金香、滨菊）→ D11 补矢车菊、铃兰 → 蒲公英
cycle([MC + n for n in ('dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'red_tulip', 'orange_tulip',
                        'white_tulip', 'pink_tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley')], 1)
cycle([MC + 'red_mushroom', MC + 'brown_mushroom'], 1)

# ---- 成对互换 ----
add(MC + 'ender_pearl', 3, MC + 'blaze_rod', 2, 128)
add(MC + 'blaze_rod', 2, MC + 'ender_pearl', 3, 128)
add(MC + 'redstone', 64, MC + 'ghast_tear', 1, 1024)
add(MC + 'ghast_tear', 1, MC + 'redstone', 64, 1024)
add(MC + 'clay_ball', 12, MC + 'gunpowder', 1, 32)
add(MC + 'gunpowder', 1, MC + 'clay_ball', 12, 32)

# ---- 生肉：猪 → 牛 → 鸡 → 鳕鱼（原作生鱼 0）→ 腐肉 ×2 →（2 个换 1 只）兔 → 羊 → 猪 ----
add(MC + 'porkchop', 1, MC + 'beef', 1, 16)
add(MC + 'beef', 1, MC + 'chicken', 1, 16)
add(MC + 'chicken', 1, MC + 'cod', 1, 16)
add(MC + 'cod', 1, MC + 'rotten_flesh', 2, 16)
add(MC + 'rotten_flesh', 2, MC + 'rabbit', 1, 16)
add(MC + 'rabbit', 1, MC + 'mutton', 1, 16)
add(MC + 'mutton', 1, MC + 'porkchop', 1, 16)
# ---- 熟肉：猪 → 牛 → 鸡 → 熟鳕鱼 → 熟鲑鱼（原作熟鱼 0、1）→ 兔 → 羊 → 猪 ----
cycle([MC + n for n in ('cooked_porkchop', 'cooked_beef', 'cooked_chicken', 'cooked_cod', 'cooked_salmon',
                        'cooked_rabbit', 'cooked_mutton')], 16)

# ---- 唱片（原作 12 张的顺序）----
cycle([MC + 'music_disc_' + d for d in DISCS], 256)

add(MC + 'sugar', 3, MC + 'slime_ball', 4, 16)
add(MC + 'slime_ball', 4, MC + 'sugar', 3, 16)
add(MC + 'egg', 9, MC + 'bone', 2, 48)
add(MC + 'bone', 2, MC + 'egg', 9, 48)
# ---- 头颅 0~4：骷髅 ×3 → 凋灵骷髅 → 僵尸 ×3 → 玩家 → 苦力怕 → 骷髅 ----
add(MC + 'skeleton_skull', 3, MC + 'wither_skeleton_skull', 1, 64)
add(MC + 'wither_skeleton_skull', 1, MC + 'zombie_head', 3, 64)
add(MC + 'zombie_head', 1, MC + 'player_head', 1, 64)
add(MC + 'player_head', 1, MC + 'creeper_head', 1, 64)
add(MC + 'creeper_head', 1, MC + 'skeleton_skull', 1, 64)
add(MC + 'wheat', 3, MC + 'leather', 1, 128)
add(MC + 'leather', 1, MC + 'wheat', 3, 128)
# ---- 生鱼 1~3：鲑鱼 → 热带鱼 → 河豚 → 鲑鱼 ----
cycle([MC + 'salmon', MC + 'tropical_fish', MC + 'pufferfish'], 24)
add(MC + 'sugar_cane', 3, MC + 'feather', 2, 64)
add(MC + 'feather', 2, MC + 'sugar_cane', 3, 64)
cycle([MC + 'coal', MC + 'charcoal'], 1)
# ---- 冰 → 草方块 → 泥土 → 灰化土 → 玻璃 → 冰 ----
cycle([MC + n for n in ('ice', 'grass_block', 'dirt', 'podzol', 'glass')], 1)


def rep(ref):
    """原料的代表物品（标签取表里写的那个）"""
    return TAGS[ref] if ref.startswith('#') else ref


def short(item_id):
    return item_id.split(':', 1)[1]


def check():
    errors = []
    seen = {}
    for ingredient, count, result, result_count, _ in TABLE:
        key = rep(ingredient)
        if key in seen:
            errors.append(f'原料 {ingredient} 出现在两条配方里（{seen[key]} 和 {result}）')
        seen[key] = result
        if count > STACK.get(key, 64):
            errors.append(f'{ingredient} 要 {count} 个，超过堆叠上限 {STACK.get(key, 64)}')
        if result_count > STACK.get(result, 64):
            errors.append(f'{result} 出 {result_count} 个，超过堆叠上限 {STACK.get(result, 64)}')
    # 刷数量的循环：边 原料 → 产物，权重 log(产物个数 / 原料个数)，找权重和为正的环（Bellman-Ford 求最长路还能松弛就有）
    nodes = sorted({rep(i) for i, *_ in TABLE} | {r for _, _, r, _, _ in TABLE})
    edges = [(rep(i), r, math.log(rc / c)) for i, c, r, rc, _ in TABLE]
    dist = {n: 0.0 for n in nodes}
    for _ in range(len(nodes)):
        changed = False
        for a, b, w in edges:
            if dist[a] + w > dist[b] + 1e-9:
                dist[b] = dist[a] + w
                changed = True
        if not changed:
            break
    else:
        errors.append('转化表里有能刷数量的循环')
    return errors


def main():
    errors = check()
    if errors:
        for e in errors:
            print('错误：' + e, file=sys.stderr)
        sys.exit(1)
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, '*.json')):
        os.remove(old)
    for ingredient, count, result, result_count, espe in TABLE:
        recipe = {}
        needs = sorted({x for x in (rep(ingredient), result) if x in NOT_YET or x in OTHER_MODULES})
        if needs:
            recipe['conditions'] = [{'type': 'forge:item_exists', 'item': x} for x in needs]
        recipe['type'] = 'essentialcraft5:mithriline_furnace'
        recipe['ingredient'] = {'tag': ingredient[1:]} if ingredient.startswith('#') else {'item': ingredient}
        if count != 1:
            recipe['count'] = count
        recipe['result'] = {'item': result}
        if result_count != 1:
            recipe['result']['count'] = result_count
        recipe['espe'] = espe
        name = f'{short(rep(ingredient))}_to_{short(result)}'
        with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(recipe, f, indent=2, ensure_ascii=False)
            f.write('\n')
    print(f'写了 {len(TABLE)} 条配方到 {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
