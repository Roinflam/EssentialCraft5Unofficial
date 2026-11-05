package com.flameforge.essentialcraft5.gun;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 一把枪的属性（第五批框架 F5-5），由 {@link #of(GunType, GunParts)} 按部件现算，不存 NBT。
 * <ul>
 *   <li>{@code damage}：每发伤害（生命值）；{@code durability}：耐久（磨损到这个数以后每发 1/4 概率卡壳）；</li>
 *   <li>{@code reload}：装填秒数（{@link #reloadTicks}）；{@code recoil}：后坐，开火时枪口上抬的度数（原作叫 knockback，只有手枪、狙击生效，{@link GunType#hasRecoil}）；</li>
 *   <li>{@code speed}：射击间隔的一半（原作 cool = speed × 2 tick，{@link #cooldownTicks}；步枪、加特林恒为 0，靠 {@link GunType#autoInterval}）；</li>
 *   <li>{@code spread}：散布角（度，射线方向两个轴各加 ±spread / 2）；{@code shots}：一匣的发数（取整见 {@link #magazine}）；</li>
 *   <li>{@code zoom}：瞄具倍率（0 = 没有瞄具）；{@code balance}：透镜（0 中性、1 混沌、2 冰冻、3 魔力、4 暗影；决定光束颜色，混沌 / 冰冻 / 魔力另有命中效果，见 {@link LensEffects}）。</li>
 * </ul>
 * <b>算法照原作 {@code ItemGun.calculateGunStats}（L196~421）</b>：每个枪身部件的七项里，自己主导的几项取全值，其余 ÷ 3，三个部件相加——
 * 枪托（base）主导 reload / speed / shots，握把（handle）主导 durability / knockback，机栓（device）主导 damage / spread；
 * 透镜只贡献 balance、瞄具只贡献 zoom。然后按枪种修正：加特林 speed = 0、spread × 4.5、shots × 20；步枪 speed = 0、spread × 1.3、shots × 6；
 * 狙击 speed × 9、spread ÷ 10、damage × 2；手枪 reload ÷ 2。
 * <p>
 * <b>全程用 float、按原作的相加顺序算</b>（base → handle → device）：弹量是 shots 向下取整，double 会把魔力锭步枪的 3.333… × 6 算成 19.999… → 19 发，
 * 原作 float 是 20 发。
 * <p>
 * 例（三个部件同材料）：魔力锭手枪伤害 13.333、耐久 200、装填 4.1667 秒、后坐 10°、speed 5（射击间隔 10 tick）、散布 5°、shots 3.333（弹量 3）、
 * 每发 133 MRU；米斯莱狙击伤害 46.667、间隔 180 tick；恶魔狙击 86.667；加特林魔力锭弹量 66、米斯莱 400、恶魔 666。
 */
public record GunStats(float damage, float durability, float reload, float recoil, float speed, float spread, float shots,
                       float zoom, int balance) {

    /** 射线射程（格，原作 128 步 × 0.5 格） */
    public static final double RAY_RANGE = 64D;

    /**
     * 手枪、狙击（非连发的枪）装填完以后另加的冷却（tick，原作 cool = 60）。枪（GunItem）和武器工作台的预览提示都用这一个常量（集成时从两处合并过来）
     */
    public static final int RELOAD_COOLDOWN_TICKS = 60;

    /** 一匣几发：{@code max(1, floor(shots))} */
    public int magazine() {
        return Math.max(1, Mth.floor(shots));
    }

    /** 两发之间的冷却（tick）：{@code ceil(speed × 2)}，speed 为 0（步枪、加特林）时 0 */
    public int cooldownTicks() {
        return speed <= 0F ? 0 : Mth.ceil(speed * 2F);
    }

    /** 装填用时（tick）：{@code floor(reload × 20)}，至少 1 */
    public int reloadTicks() {
        return Math.max(1, Mth.floor(reload * 20F));
    }

    /** 耐久取整（磨损到这个数以后开始卡壳） */
    public int durabilityInt() {
        return Mth.floor(durability);
    }

    /**
     * 按部件算属性。部件 id 在材料表里找不到的（数据被改过、以后删了的材料）当那个部件不存在（贡献 0），不抛异常；
     * 材料开没开放不影响（NBT 里写着的照算）。
     */
    public static GunStats of(GunType type, GunParts parts) {
        float damage = 0F;
        float durability = 0F;
        float reload = 0F;
        float knockback = 0F;
        float speed = 0F;
        float spread = 0F;
        float shots = 0F;

        GunMaterials.Body base = GunMaterials.body(parts.base()).orElse(null);
        if (base != null) {               // 枪托：reload、speed、shots 全值
            durability += base.durability() / 3;
            damage += base.damage() / 3;
            reload += base.reload();
            knockback += base.knockback() / 3;
            spread += base.spread() / 3;
            speed += base.speed();
            shots += base.shots();
        }
        GunMaterials.Body handle = GunMaterials.body(parts.handle()).orElse(null);
        if (handle != null) {             // 握把：durability、knockback 全值
            durability += handle.durability();
            damage += handle.damage() / 3;
            reload += handle.reload() / 3;
            knockback += handle.knockback();
            spread += handle.spread() / 3;
            speed += handle.speed() / 3;
            shots += handle.shots() / 3;
        }
        GunMaterials.Body device = GunMaterials.body(parts.device()).orElse(null);
        if (device != null) {             // 机栓：damage、spread 全值
            durability += device.durability() / 3;
            damage += device.damage();
            reload += device.reload() / 3;
            knockback += device.knockback() / 3;
            spread += device.spread();
            speed += device.speed() / 3;
            shots += device.shots() / 3;
        }
        int balance = parts.lens() == null ? 0 : GunMaterials.lens(parts.lens()).map(GunMaterials.Lens::balance).orElse(0);
        float zoom = parts.scope() == null ? 0F : GunMaterials.scope(type, parts.scope()).map(GunMaterials.Scope::zoom).orElse(0F);

        switch (type) {
            case GATLING -> {
                speed = 0F;
                spread *= 4.5F;
                shots *= 20;
            }
            case RIFLE -> {
                speed = 0F;
                spread *= 1.3F;
                shots *= 6;
            }
            case SNIPER -> {
                speed *= 9;
                spread /= 10;
                damage *= 2;
            }
            case PISTOL -> reload /= 2;
        }
        return new GunStats(damage, durability, reload, knockback, speed, spread, shots, zoom, balance);
    }

    /** 物品是组装好的枪就算它的属性（枪种按注册名认），否则为空 */
    public static Optional<GunStats> of(ItemStack stack) {
        Optional<GunType> type = GunType.of(stack);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        return GunParts.read(stack).map(parts -> of(type.get(), parts));
    }
}
