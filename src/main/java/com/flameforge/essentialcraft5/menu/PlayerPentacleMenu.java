package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 灵魂五芒星的界面（原作 ContainerPlayerPentacle 是空容器、canInteractWith 恒为 true；界面是 GuiPlayerPentacle）。没有槽位，
 * 同步两个数：档位、有效 ESPE（服务端算，见 {@link PlayerPentacleBlockEntity#effectiveESPE}）。身上的腐化效果、矩阵损伤走玩家数据同步，不在这里。
 *
 * <h2>按钮（porting_guide §9，框架 G5）</h2>
 * 界面调 {@code gameMode.handleInventoryButtonClick(containerId, 按钮号)}，服务端原版先查过「打开的就是这个菜单、不是旁观者、stillValid（8 格内、方块还在）」，
 * 这里再查一遍距离，然后校验按钮号、效果、档位、ESPE，创造模式只看服务端的 {@code isCreative()}：
 * <ul>
 *   <li>{@link #BUTTON_CURE} + i（0 ~ 24）：花 ESPE 治腐化效果表里序号为 i 的效果（{@link CorruptionEffects#byIndex}）。按效果本身编号，不按「身上第几个」，
 *       效果列表同步有延迟时也不会治错；身上没有这个效果就不做。</li>
 *   <li>{@link #BUTTON_CURE_FREE} + i（32 ~ 56）：创造模式免费治（原作按住 Shift 点），不看档位、不花 ESPE；服务端判定不是创造模式就拒绝
 *       （原作按钮包里带着客户端自报的 isCreative，免费清效果，batch3 §3.7 修 1）。</li>
 * </ul>
 * 按钮号在包里是一个字节（ServerboundContainerButtonClickPacket 用 writeByte），不能超过 127。
 * 本界面只发包，客户端菜单上的 clickMenuButton 直接返回 false，不做预测。
 */
public class PlayerPentacleMenu extends MachineMenu {

    /** 花 ESPE 治：基数 + 效果序号 */
    public static final int BUTTON_CURE = 0;
    /** 创造模式免费治：基数 + 效果序号 */
    public static final int BUTTON_CURE_FREE = 32;
    /** 效果表的大小（25），两段按钮的范围都是 [基数, 基数 + 25) */
    public static final int MAX_EFFECTS = CorruptionEffects.ALL.size();

    /** 额外数值：档位 + 1（ContainerData 按 short 同步，−1 也能传，这里加 1 只为读着清楚） */
    public static final int EXTRA_TIER = 0;
    /** 额外数值：有效 ESPE（拆两格） */
    public static final int EXTRA_ESPE = 1;
    private static final int EXTRA_COUNT = 3;

    /** 服务端才有 */
    @Nullable
    private final PlayerPentacleBlockEntity pentacle;

    public PlayerPentacleMenu(int id, Inventory playerInventory, PlayerPentacleBlockEntity be) {
        super(ECPentacle.PLAYER_PENTACLE_MENU.get(), id, playerInventory, be.getBlockPos(), serverData(be),
                ECPentacle.PLAYER_PENTACLE.get(), be);
        this.pentacle = be;
    }

    public PlayerPentacleMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ECPentacle.PLAYER_PENTACLE_MENU.get(), id, playerInventory, buf.readBlockPos(), MachineData.client(EXTRA_COUNT),
                ECPentacle.PLAYER_PENTACLE.get(), null);
        this.pentacle = null;
    }

    private static MachineData serverData(PlayerPentacleBlockEntity be) {
        IntSupplier[] espe = MachineData.wide(be::effectiveESPE);
        return MachineData.server(be, () -> be.getTier() + 1, espe[0], espe[1]);
    }

    /** 服务端菜单对应的五芒星；客户端菜单为 null（打开界面以后记档位用，见 corruption/CorruptionGate） */
    @Nullable
    public PlayerPentacleBlockEntity pentacle() {
        return pentacle;
    }

    /** −1 = 结构不完整，0 ~ 3 */
    public int tier() {
        return data.extra(EXTRA_TIER) - 1;
    }

    /** 能用的 ESPE（已经乘过 0.36） */
    public int effectiveESPE() {
        return data.extraWide(EXTRA_ESPE);
    }

    /** 这个按钮号是不是在两段合法范围里 */
    public static boolean isValidButton(int id) {
        return id >= BUTTON_CURE && id < BUTTON_CURE + MAX_EFFECTS || id >= BUTTON_CURE_FREE && id < BUTTON_CURE_FREE + MAX_EFFECTS;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // 客户端（或客户端菜单，没有方块实体）：什么都不做，只由服务端判
        if (!(player instanceof ServerPlayer serverPlayer) || pentacle == null || pentacle.isRemoved()) {
            return false;
        }
        // 原版 handleContainerButtonClick 已经查过 stillValid，这里再查一遍（直接调用时也安全）
        if (!isValidButton(id) || !stillValid(serverPlayer)) {
            return false;
        }
        boolean free = id >= BUTTON_CURE_FREE;
        if (free && !serverPlayer.isCreative()) {
            return false;
        }
        CorruptionEffects.Effect effect = CorruptionEffects.byIndex(free ? id - BUTTON_CURE_FREE : id - BUTTON_CURE);
        return pentacle.cure(serverPlayer, effect, free);
    }
}
