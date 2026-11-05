package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 四种 MRU 射线枪（第五批框架 F5-5，原作 {@code ItemGun.gunType} 字符串 + {@code TileWeaponMaker.index} 0~3）。
 * 枪物品（ECGuns）、武器工作台（ECWeaponMaker）、属性计算（{@link GunStats}）共用这一份数据；工作台按 {@link #itemId()} 从注册表取枪物品，
 * 不 import 枪物品的类。
 * <p>
 * 每种枪带：注册名、工作台注册名、每发 MRU 系数（原作 {@code ItemGun}：手枪 / 狙击 伤害 × 10，步枪 × 3，加特林 × 2，取整）、
 * 开火方式（单发 / 按住连发的间隔 / 加特林的预转）、有没有后坐（只有手枪、狙击会把枪口抬高）、瞄具种类、属性修正（{@link GunStats}）、
 * 工作台的槽位布局（{@link #layout()}，照原作 {@code ContainerWeaponBench}）。
 */
public enum GunType {

    /** 手枪：单发，射完一匣装填时间减半（原作 calculateGunStats：reload ÷ 2），可选透镜、可选普通瞄具 */
    PISTOL("pistol", 10, 0, 0, true, ScopeKind.NORMAL_OPTIONAL, Layout.builder()
            .slot(SlotRole.CORE, 40, 37)
            .slot(SlotRole.SOUL, 60, 57)
            .slot(SlotRole.LENS_OPT, 20, 17)
            .slot(SlotRole.SCOPE_OPT, 60, 17)
            .slot(SlotRole.BASE, 40, 17).slot(SlotRole.BASE, 60, 37).slot(SlotRole.BASE, 80, 37)
            .slot(SlotRole.DEVICE, 80, 17)
            .slot(SlotRole.HANDLE, 80, 57)
            .build()),
    /** 步枪：按住每 3 tick 一发，每发 伤害 × 3 MRU；可选透镜、可选普通瞄具 */
    RIFLE("rifle", 3, 3, 0, false, ScopeKind.NORMAL_OPTIONAL, Layout.builder()
            .slot(SlotRole.CORE, 40, 37)
            .slot(SlotRole.SOUL, 60, 37)
            .slot(SlotRole.LENS_OPT, 20, 37)
            .slot(SlotRole.SCOPE_OPT, 40, 17)
            .slot(SlotRole.BASE, 20, 17).slot(SlotRole.BASE, 60, 17).slot(SlotRole.BASE, 80, 37).slot(SlotRole.BASE, 60, 57)
            .slot(SlotRole.DEVICE, 80, 17)
            .slot(SlotRole.HANDLE, 20, 57).slot(SlotRole.HANDLE, 40, 57).slot(SlotRole.HANDLE, 80, 57)
            .build()),
    /** 狙击步枪：单发，必须有镜座（魔力纯化玻璃合金）+ 两格同种狙击镜材；可选透镜 */
    SNIPER("sniper", 10, 0, 0, true, ScopeKind.SNIPER_REQUIRED, Layout.builder()
            .slot(SlotRole.CORE, 60, 37)
            .slot(SlotRole.SOUL, 40, 57)
            .slot(SlotRole.LENS_OPT, 20, 37)
            .slot(SlotRole.SCOPE_HOUSING, 60, 17)
            .slot(SlotRole.SCOPE, 40, 17).slot(SlotRole.SCOPE, 80, 17)
            .slot(SlotRole.BASE, 40, 37).slot(SlotRole.BASE, 80, 37).slot(SlotRole.BASE, 100, 37)
            .slot(SlotRole.DEVICE, 100, 17).slot(SlotRole.DEVICE, 60, 57)
            .slot(SlotRole.HANDLE, 80, 57).slot(SlotRole.HANDLE, 100, 57)
            .build()),
    /** 加特林机枪：先转 60 tick，之后每 tick 一发、每发 伤害 × 2 MRU；必须有透镜座（日光玻璃）+ 两格同种透镜；没有瞄具 */
    GATLING("gatling", 2, 1, 60, false, ScopeKind.NONE, Layout.builder()
            .slot(SlotRole.CORE, 80, 37)
            .slot(SlotRole.SOUL, 40, 57)
            .slot(SlotRole.LENS_HOUSING, 20, 37)
            .slot(SlotRole.LENS, 20, 17).slot(SlotRole.LENS, 20, 57)
            .slot(SlotRole.BASE, 40, 17).slot(SlotRole.BASE, 60, 17).slot(SlotRole.BASE, 80, 17).slot(SlotRole.BASE, 100, 17)
            .slot(SlotRole.BASE, 120, 17).slot(SlotRole.BASE, 40, 37).slot(SlotRole.BASE, 120, 37)
            .slot(SlotRole.DEVICE, 60, 37).slot(SlotRole.DEVICE, 100, 37)
            .slot(SlotRole.HANDLE, 60, 57).slot(SlotRole.HANDLE, 80, 57).slot(SlotRole.HANDLE, 100, 57).slot(SlotRole.HANDLE, 120, 57)
            .build());

    /** 工作台输出格（0 号格，四种共用，原作 (152, 37)） */
    public static final int OUTPUT_SLOT = 0;

    private final String id;
    private final int mruFactor;
    private final int autoInterval;
    private final int spinUpTicks;
    private final boolean recoil;
    private final ScopeKind scopeKind;
    private final Layout layout;
    private final ResourceLocation itemId;
    private final ResourceLocation makerBlockId;

    GunType(String id, int mruFactor, int autoInterval, int spinUpTicks, boolean recoil, ScopeKind scopeKind, Layout layout) {
        this.id = id;
        this.mruFactor = mruFactor;
        this.autoInterval = autoInterval;
        this.spinUpTicks = spinUpTicks;
        this.recoil = recoil;
        this.scopeKind = scopeKind;
        this.layout = layout;
        this.itemId = EssentialCraft5.id(id);
        this.makerBlockId = EssentialCraft5.id(id + "_weapon_maker");
    }

    /** 小写名（pistol / rifle / sniper / gatling），也是贴图目录 {@code item/guns/<id>/} 的名字 */
    public String id() {
        return id;
    }

    /** 枪物品的注册名（{@code essentialcraft5:pistol} …） */
    public ResourceLocation itemId() {
        return itemId;
    }

    /** 武器工作台的注册名（{@code essentialcraft5:pistol_weapon_maker} …） */
    public ResourceLocation makerBlockId() {
        return makerBlockId;
    }

    /** 每发 MRU 的系数：每发扣 {@code (int)(damage × 系数)}（{@link #mruPerShot}） */
    public int mruFactor() {
        return mruFactor;
    }

    /** 每发扣的 MRU（原作 {@code (int)(damage * 10)} 这类写法：向零取整） */
    public int mruPerShot(GunStats stats) {
        return (int) (stats.damage() * mruFactor);
    }

    /** 按住连发（步枪、加特林）；手枪、狙击是点一下打一发 */
    public boolean automatic() {
        return autoInterval > 0;
    }

    /** 按住时每几 tick 一发（步枪 3、加特林 1；单发的枪是 0） */
    public int autoInterval() {
        return autoInterval;
    }

    /** 按住以后先空转几 tick 才开火（加特林 60，其余 0） */
    public int spinUpTicks() {
        return spinUpTicks;
    }

    /** 开火时枪口上抬（后坐，原作 knockback 属性；只有手枪、狙击） */
    public boolean hasRecoil() {
        return recoil;
    }

    public ScopeKind scopeKind() {
        return scopeKind;
    }

    /** 工作台槽位布局（含 0 号输出格） */
    public Layout layout() {
        return layout;
    }

    /** 按小写名找（NBT、指令用）；找不到返回 null */
    @Nullable
    public static GunType byId(String id) {
        for (GunType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** 这个物品是哪种枪（按注册名认，不依赖枪物品的类） */
    public static Optional<GunType> of(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (GunType type : values()) {
            if (type.itemId.equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 这个方块注册名是哪种枪的工作台 */
    public static Optional<GunType> ofMaker(@Nullable ResourceLocation blockId) {
        for (GunType type : values()) {
            if (type.makerBlockId.equals(blockId)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 瞄具种类 */
    public enum ScopeKind {
        /** 没有瞄具（加特林） */
        NONE,
        /** 可选的普通瞄具（手枪、步枪：{@link GunMaterials#SCOPES}，放一格） */
        NORMAL_OPTIONAL,
        /** 必须有狙击镜（镜座 {@link GunMaterials#SNIPER_SCOPE_HOUSING} + 两格同种 {@link GunMaterials#SNIPER_SCOPES}） */
        SNIPER_REQUIRED
    }

    /** 工作台每一格的用途（原作 ContainerWeaponBench 的注释 Elemental Core / Soul Sphere / Lens / Scope / Base / Mechanism / Handle） */
    public enum SlotRole {
        /** 0 号输出格 */
        OUTPUT,
        /** 核心（{@code #essentialcraft5:cores/magic}），组装时扣 1 */
        CORE,
        /** 灵魂格物品（灵魂球等 MRU 储存物品，标签由工作台模块建），组装时扣 1 */
        SOUL,
        /** 可选透镜（手枪、步枪、狙击；空着或放一个 {@link GunMaterials#LENSES}） */
        LENS_OPT,
        /** 加特林的透镜座（必须是 {@link GunMaterials#GATLING_LENS_HOUSING}） */
        LENS_HOUSING,
        /** 加特林的透镜（两格都要有、同一种） */
        LENS,
        /** 可选普通瞄具（手枪、步枪） */
        SCOPE_OPT,
        /** 狙击的镜座（必须是 {@link GunMaterials#SNIPER_SCOPE_HOUSING}） */
        SCOPE_HOUSING,
        /** 狙击镜材（两格都要有、同一种） */
        SCOPE,
        /** 枪托（原作 Base；主导 reload / speed / shots） */
        BASE,
        /** 机栓（原作 Mechanism / device；主导 damage / spread） */
        DEVICE,
        /** 握把（原作 Handle；主导 durability / knockback） */
        HANDLE
    }

    /** 一格：序号（= 方块实体物品栏的下标）、用途、界面坐标 */
    public record Slot(int index, SlotRole role, int x, int y) {
    }

    /** 一种工作台的槽位布局。0 号是输出格 (152, 37)，其余按原作 ContainerWeaponBench 的顺序 */
    public static final class Layout {

        private final List<Slot> slots;

        private Layout(List<Slot> slots) {
            this.slots = Collections.unmodifiableList(slots);
        }

        static Builder builder() {
            return new Builder();
        }

        /** 格数（含输出格）：手枪 10、步枪 13、狙击 14、加特林 19 */
        public int size() {
            return slots.size();
        }

        public List<Slot> slots() {
            return slots;
        }

        public Slot slot(int index) {
            return slots.get(index);
        }

        /** 下标越界返回 null */
        @Nullable
        public SlotRole role(int index) {
            return index >= 0 && index < slots.size() ? slots.get(index).role() : null;
        }

        /** 这种用途的所有格子下标（升序） */
        public int[] indicesOf(SlotRole role) {
            return slots.stream().filter(slot -> slot.role() == role).mapToInt(Slot::index).toArray();
        }

        public boolean has(SlotRole role) {
            return indicesOf(role).length > 0;
        }

        static final class Builder {
            private final List<Slot> slots = new ArrayList<>(List.of(new Slot(OUTPUT_SLOT, SlotRole.OUTPUT, 152, 37)));

            Builder slot(SlotRole role, int x, int y) {
                slots.add(new Slot(slots.size(), role, x, y));
                return this;
            }

            Layout build() {
                return new Layout(slots);
            }
        }
    }
}
