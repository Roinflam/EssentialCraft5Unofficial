package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalJukeboxMenu;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力唱片机。对应原作 TileMagicalJukebox（秘密唱片的彩蛋随唱片一起删掉了，约 110 行）。
 * <p>
 * 0 号槽绑定宝石，1 号槽放音乐唱片。放进唱片、有 {@code mru_usage}（默认 500）MRU 时自动放一遍；
 * 放完不会自己重放。给一个红石信号（上升沿）就花 {@code mru_usage} 从头再放一遍，两次开始之间至少隔 {@value #RESTART_COOLDOWN} tick。
 * 拿走唱片就停。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>红石只在<b>上升沿</b>重放（原作红石常亮时每 5 秒重放一次、每次 500 MRU）。冷却中收到的上升沿会记着，冷却一过就放。</li>
 *   <li>漏斗、管道只能在唱片<b>放完以后</b>把它抽走（原作随时能抽，放一半就被抽走）；还没放过的（MRU 不够、冷却中等着放）
 *       也抽不走，留在里面等着。往里送只收音乐唱片。这样漏斗链可以当自动点歌机：放完一张抽走一张、送进下一张。</li>
 *   <li>照原版唱片机：记录这张唱片放到哪了（存盘），放着的时候每秒发一次 jukebox_play 游戏事件，放完发 jukebox_stop_play。
 *       原版里只有悦灵听这两个事件，可悦灵和鹦鹉都写死了只认 Blocks.JUKEBOX（鹦鹉每 tick 查、立刻不跳；
 *       悦灵收到事件会跳，到下一次每秒一查就停，看着一闪一闪），幽匿感测体也不认（这两个事件不在 minecraft:vibrations 标签里）。
 *       事件照发，留给别的模组用。
 *       原作不管唱片放没放完，一直当作「在放」。</li>
 *   <li>配置 max_mru 真的生效（原作构造器里写死了 5000）。</li>
 * </ul>
 */
public class MagicalJukeboxBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.magicaljukebox */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_jukebox");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used each time a record starts playing.",
                            "每开始放一次唱片消耗的 MRU")
                    .defineInRange("mru_usage", 500, 0, 1_000_000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_RECORD = 1;
    public static final int SLOTS = 2;

    /** 两次开始播放之间至少隔多少 tick（原作 recordCooldownTime = 100） */
    public static final int RESTART_COOLDOWN = 100;
    /** 放着的时候多少 tick 发一次 jukebox_play（原版 20） */
    private static final int EVENT_INTERVAL = 20;

    private static final String TAG_PLAYED = "played";
    private static final String TAG_SONG_LEFT = "song_ticks_left";
    private static final String TAG_COOLDOWN = "cooldown";
    private static final String TAG_POWERED = "powered";
    private static final String TAG_RESTART = "restart_requested";
    private static final String TAG_PLAYING = "playing";

    /** 这张唱片放进来以后放过了（原作 recordPlayed）；拿走唱片清掉 */
    private boolean played;
    /** 这一遍还剩多少 tick 放完；0 表示没在放 */
    private int songTicksLeft;
    private int cooldown;
    /** 上一 tick 有没有红石信号（判上升沿用，存盘，免得读档时误判一次） */
    private boolean powered;
    /** 收到了上升沿、还没来得及重放（冷却中或 MRU 不够） */
    private boolean restartRequested;
    private int eventTimer;
    /** 上一 tick 槽里的唱片（按对象比较，判断换没换） */
    private ItemStack trackedRecord = ItemStack.EMPTY;
    /** 客户端：收到的「在放」状态（冒音符用） */
    private boolean clientPlaying;

    public MagicalJukeboxBlockEntity(BlockPos pos, BlockState state) {
        super(ECDevices.MAGICAL_JUKEBOX_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号只放音乐唱片（原作 SlotRecord） */
    public static boolean slotRule(int slot, ItemStack stack) {
        if (slot == SLOT_RECORD) {
            return stack.getItem() instanceof RecordItem;
        }
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /**
     * 自动化只能在唱片<b>放过一遍、而且放完了</b>以后把它抽走。还没放过的唱片（刚送进来、本机还没 tick 到；
     * MRU 不够没开放；冷却中等着放）都抽不走，否则下面的漏斗会把歌单整张整张地冲走。
     * 按对象比较 trackedRecord：同一 tick 里旧唱片刚被抽走、新唱片刚送进来而本机还没 tick 到时，
     * played 还是上一张留下的 true，靠这一条挡住新唱片。
     */
    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_RECORD && played && !isPlaying() && inventory.getStackInSlot(SLOT_RECORD) == trackedRecord;
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        boolean nowPowered = neighborPowered();
        if (nowPowered && !powered) {
            restartRequested = true;
        }
        if (nowPowered != powered) {
            powered = nowPowered;
            markSaved();
        }
        if (cooldown > 0) {
            cooldown--;
        }

        ItemStack stack = inventory.getStackInSlot(SLOT_RECORD);
        // 按对象判断唱片换没换：漏斗在同一 tick 里先抽走旧的、再送进新的，也算换了（新唱片要自动放）
        if (stack != trackedRecord) {
            trackedRecord = stack;
            if (played || songTicksLeft > 0 || restartRequested) {
                stopPlaying();
                played = false;
                restartRequested = false;
                markSaved();
            }
        }
        if (!(stack.getItem() instanceof RecordItem record)) {
            return;
        }

        int cost = ECConfig.get(Config.mruUsage);
        if (!played) {
            // 刚放进来的唱片：自动放一遍（放进来之前收到的红石不算）
            restartRequested = false;
            if (cooldown == 0 && mru.getMRU() >= cost) {
                start(record, cost);
            }
        } else if (restartRequested && cooldown == 0 && mru.getMRU() >= cost) {
            restartRequested = false;
            start(record, cost);
        }

        if (songTicksLeft > 0) {
            songTicksLeft--;
            if (songTicksLeft == 0) {
                level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, worldPosition, GameEvent.Context.of(getBlockState()));
                syncPlaying();
            } else if (++eventTimer >= EVENT_INTERVAL) {
                eventTimer = 0;
                level.gameEvent(GameEvent.JUKEBOX_PLAY, worldPosition, GameEvent.Context.of(getBlockState()));
            }
            markSaved();
        }
    }

    /** 从头放一遍（原作：扣 500 MRU、recordPlayed = 1、冷却 100、playEvent 1010） */
    private void start(RecordItem record, int cost) {
        mru.extractMRU(cost, true);
        played = true;
        cooldown = RESTART_COOLDOWN;
        // 原版唱片机：放完再多等 20 tick 才算停
        songTicksLeft = Math.max(1, record.getLengthInTicks() + 20);
        eventTimer = 0;
        // 客户端收到 1010 时会先停掉这个位置上正在放的，再从头放
        level.levelEvent(null, LevelEvent.SOUND_PLAY_JUKEBOX_SONG, worldPosition, Item.getId(record));
        level.gameEvent(GameEvent.JUKEBOX_PLAY, worldPosition, GameEvent.Context.of(getBlockState()));
        syncPlaying();
        markSaved();
    }

    /** 停止播放（拿走唱片、挖掉方块时调）。只在服务端调 */
    public void stopPlaying() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasPlaying = songTicksLeft > 0;
        songTicksLeft = 0;
        level.levelEvent(LevelEvent.SOUND_STOP_JUKEBOX_SONG, worldPosition, 0);
        if (wasPlaying) {
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, worldPosition, GameEvent.Context.of(getBlockState()));
            syncPlaying();
        }
        markSaved();
    }

    /** 这一遍还没放完 */
    public boolean isPlaying() {
        return level != null && level.isClientSide ? clientPlaying : songTicksLeft > 0;
    }

    /** 放进来以后放过了（下一遍要靠红石） */
    public boolean hasPlayed() {
        return played;
    }

    private void syncPlaying() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void markSaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 原作：在放的时候 1/3 的几率冒一个音符 */
    @Override
    protected void clientTick() {
        if (level == null || !clientPlaying || level.random.nextFloat() > 0.33F) {
            return;
        }
        level.addParticle(ParticleTypes.NOTE,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 1.2D,
                worldPosition.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) / 2D,
                level.random.nextInt(25) / 24D, 0D, 0D);
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_PLAYED, played);
        tag.putInt(TAG_SONG_LEFT, songTicksLeft);
        tag.putInt(TAG_COOLDOWN, cooldown);
        tag.putBoolean(TAG_POWERED, powered);
        tag.putBoolean(TAG_RESTART, restartRequested);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        played = tag.getBoolean(TAG_PLAYED);
        songTicksLeft = Math.max(0, tag.getInt(TAG_SONG_LEFT));
        cooldown = Math.max(0, tag.getInt(TAG_COOLDOWN));
        powered = tag.getBoolean(TAG_POWERED);
        restartRequested = tag.getBoolean(TAG_RESTART);
        // 读出来的唱片就是存盘时放着的那张，不算换过
        trackedRecord = inventory.getStackInSlot(SLOT_RECORD);
        // 发给客户端的更新包里有 playing；存档里没有，就按剩余时长算
        clientPlaying = tag.contains(TAG_PLAYING) ? tag.getBoolean(TAG_PLAYING) : songTicksLeft > 0;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_PLAYING, songTicksLeft > 0);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalJukeboxMenu(id, inventory, this);
    }
}
