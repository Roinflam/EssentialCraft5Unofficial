package com.flameforge.essentialcraft5.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.UUID;

/**
 * 某名玩家开始 / 不再被饰品封禁场（宅男套装）压制。两端都发，在 Forge 总线上（{@code MinecraftForge.EVENT_BUS}）：
 * <ul>
 *   <li>服务端：压制状态翻转的那一刻（{@code ServerTickEvent} 末尾结算时）。{@link #getSourceIds()} 是压制他的穿戴者。</li>
 *   <li>客户端：收到压制状态包、状态和本地记的不一样时。{@link #getSourceNames()} 只有本人那一份带名字（至多 3 个，
 *       余下的人数在 {@link #getMoreSources()}），看别人时是空的；{@link #getSourceIds()} 总是空。</li>
 * </ul>
 * 压制期间饰品栏里的东西一格都不动，只是不生效，Curios 自己发的 {@code CurioChangeEvent} 也照常有「有 → 空」「空 → 有」两次。
 * 按饰品栏内容版本号长效缓存查询结果的模组看不见压制（内容没变），听这个事件把这名玩家的缓存作废即可。不可取消。
 */
public class CurioSealEvent extends PlayerEvent {

    private final boolean sealed;
    private final List<UUID> sourceIds;
    private final List<String> sourceNames;
    private final int moreSources;

    public CurioSealEvent(Player player, boolean sealed, List<UUID> sourceIds, List<String> sourceNames, int moreSources) {
        super(player);
        this.sealed = sealed;
        this.sourceIds = List.copyOf(sourceIds);
        this.sourceNames = List.copyOf(sourceNames);
        this.moreSources = moreSources;
    }

    /** true = 这一刻起被压制；false = 这一刻起恢复 */
    public boolean isSealed() {
        return sealed;
    }

    /** 服务端：压制他的穿戴者（恢复时为空）；客户端总是空 */
    public List<UUID> getSourceIds() {
        return sourceIds;
    }

    /** 压制他的穿戴者名字（至多 3 个）：服务端是全部来源的前 3 个，客户端只有本人那份有 */
    public List<String> getSourceNames() {
        return sourceNames;
    }

    /** 名字没列出来的来源还有几个 */
    public int getMoreSources() {
        return moreSources;
    }
}
