package cn.blockforge.generated.silentknock.logic;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.config.ToastMode;
import cn.blockforge.generated.silentknock.gui.SpaToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 敲门状态反馈：聊天栏 + 弹窗 + 提示音，以及连接失败时改写的断开界面文案。
 *
 * <p>「弹不弹、刷不刷」统一由配置里的 {@link ToastMode} 决定（游戏里按 ESC →「选项」→
 * 「SilentSPA」就能改）。连接失败时<b>不再</b>弹右上角的框：原因会写进原版的
 * 「连接失败」界面，见 {@link #decorateDisconnect}，没必要再弹一次。
 *
 * <p>所有文案都走翻译键，跟随游戏语言设置。
 *
 * <p>除 {@link #decorateDisconnect} 之外，其余方法只允许在渲染主线程调用。
 */
public final class SpaFeedback {
    private SpaFeedback() {
    }

    /** 开始敲门。 */
    public static void sending(String serverName) {
        Text message = Text.translatable("silent_knock.msg.sending", serverName);
        show(message);
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2f);
    }

    /** 包已发出，即将放行连接。 */
    public static void sent(String serverName, long delayMs) {
        Text message = Text.translatable("silent_knock.msg.sent", Long.toString(delayMs), serverName);
        show(message);
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    /** 临时敲门（语音模组之类要的额外端口）开始。 */
    public static void extraSending(String ports) {
        show(Text.translatable("silent_knock.msg.extra_sending", ports));
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2f);
    }

    /** 临时敲门的包发出去了，门会开一小会儿。 */
    public static void extraSent(String ports) {
        show(Text.translatable("silent_knock.msg.extra_sent", ports));
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    /** 临时敲门失败。 */
    public static void extraFailed(Text reason) {
        showChat(Text.translatable("silent_knock.msg.extra_failed", reason));
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7f);
    }

    /** 刚刚敲过同一个端口，还没过冷却。 */
    public static void extraCooldown(String entry, long seconds) {
        showChat(Text.translatable("silent_knock.msg.extra_cooldown", entry, Long.toString(seconds)));
    }

    /** 当前服务器没绑定密钥文件，没必要敲门。 */
    public static void extraNoKey(String serverName) {
        showChat(Text.translatable("silent_knock.msg.extra_no_key", serverName));
    }

    /** 不在多人服务器里（单机 / 标题界面）。 */
    public static void extraNoServer() {
        showChat(Text.translatable("silent_knock.msg.extra_no_server"));
    }

    /**
     * 敲门失败，已回退直连。
     *
     * <p>只往聊天栏写一行（并记日志）；右上角的失败框已经去掉了，
     * 具体原因会在「连接失败」界面里说明。
     */
    public static void failed(String serverName, Text reason) {
        showChat(Text.translatable("silent_knock.msg.failed", reason, serverName));
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7f);
    }

    /** 刚选中 SPA 配置文件时告诉玩家绑定成功。 */
    public static void keyBound(String fileName) {
        show(Text.translatable("silent_knock.msg.bound", fileName));
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.5f);
    }

    /** 模组自己新建了一个 SPA 配置文件。 */
    public static void keyCreated(String fileName) {
        show(Text.translatable("silent_knock.msg.created", fileName));
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
    }

    /** 解除了服务器和密钥文件的绑定（文件还在电脑里）。 */
    public static void keyUnbound(String serverName) {
        show(Text.translatable("silent_knock.msg.unbound", serverName));
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.0f);
    }

    /** 选中的配置文件解析失败，提前提醒玩家。 */
    public static void keyInvalid(Text reason) {
        show(Text.translatable("silent_knock.msg.key_invalid", reason));
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7f);
    }

    /**
     * 本机系统时间和网络时间差得太多，敲门包可能被服务器直接丢掉。
     *
     * <p>只会提醒，不会去改本机时间：电脑的时间一般要玩家自己在系统设置里同步。
     *
     * @param seconds   偏差多少秒（绝对值）
     * @param tolerance 服务器允许的偏差（秒）
     * @param ahead     true 表示本机时间比网络时间快
     */
    public static void clockSkew(long seconds, long tolerance, boolean ahead) {
        Text message = Text.translatable(
                ahead ? "silent_knock.msg.clock_skew.ahead" : "silent_knock.msg.clock_skew.behind",
                Long.toString(seconds), Long.toString(tolerance));
        show(message);
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.8f);
    }

    /**
     * 自动识别到需要额外端口的模组、并且已经把它的端口一起敲开时弹的提示：
     * “XXX mod detected, knocked xxx automatically.”
     *
     * <p>只弹窗 + 记日志，不往聊天栏刷屏；每个模组一次游戏里只会弹一次。
     */
    public static void autoKnockToast(String modName, String ports) {
        Text line = Text.translatable("silent_knock.toast.auto_knock.line", ports);
        SilentSpaClient.LOGGER.info("[SPA] 检测到 {} 模组，已自动敲门 {}", modName, ports);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getToastManager() == null || !toastAllowed()) {
            return;
        }
        client.getToastManager().add(SpaToast.autoKnock(
                Text.translatable("silent_knock.toast.auto_knock.title", modName),
                line,
                SpaConfig.get().toastDurationMs,
                soundAllowed()));
    }

    /**
     * 把原版“连接失败”界面的原因改写成能看懂的样子。
     *
     * <p>fwknopd 是静默丢弃的，客户端无法知道服务器到底验没验；所以这里只根据
     * “本地敲门包有没有发出去”给出两种解释，再把原版原因附在最后，方便对日志。
     *
     * @param vanillaReason 原版原本要显示的那行原因
     * @param knockFailed   敲门包是否压根没发出去（密钥错误 / UDP 发送失败）
     * @param knockReason   敲门失败的具体原因，可用 null
     */
    public static Text decorateDisconnect(Text vanillaReason, boolean knockFailed, Text knockReason) {
        MutableText result = Text.empty();
        if (knockFailed) {
            result.append(Text.translatable("silent_knock.disconnect.knock_failed",
                    knockReason == null
                            ? Text.translatable("silent_knock.disconnect.unknown_reason")
                            : knockReason).formatted(Formatting.RED));
            result.append(Text.literal("\n"));
            result.append(Text.translatable("silent_knock.disconnect.knock_failed_hint")
                    .formatted(Formatting.YELLOW));
        } else {
            result.append(Text.translatable("silent_knock.disconnect.knock_sent_hint")
                    .formatted(Formatting.YELLOW));
        }

        if (ClockCheck.isSkewed()) {
            // 电脑时间不准时，fwknopd 会把包当重放包丢掉，这也是“连不上”的常见原因之一
            result.append(Text.literal("\n"));
            result.append(Text.translatable(
                            ClockCheck.skewMillis() > 0
                                    ? "silent_knock.disconnect.clock_skew.ahead"
                                    : "silent_knock.disconnect.clock_skew.behind",
                            Long.toString(ClockCheck.skewSeconds()),
                            Long.toString(Math.max(1, SpaConfig.get().timeToleranceSeconds)))
                    .formatted(Formatting.GOLD));
        }

        result.append(Text.literal("\n\n"));
        result.append(Text.translatable("silent_knock.disconnect.vanilla_reason", vanillaReason)
                .formatted(Formatting.GRAY));
        return result;
    }

    /** 当前提示档位（配置里被手改坏时按“全部提示”算）。 */
    public static ToastMode toastMode() {
        return ToastMode.orDefault(SpaConfig.get().toastMode);
    }

    /** 现在这个界面允不允许弹右上角的提示。 */
    public static boolean toastAllowed() {
        boolean inGame = inGame();
        switch (toastMode()) {
            case SILENT:
                return false;
            case INGAME_OFF:
            case INGAME_CHAT_ONLY:
                // 玩游戏时不弹框，只在标题界面 / 连接界面弹
                return !inGame;
            case ALL:
            default:
                return true;
        }
    }

    /**
     * 现在允不允许往聊天栏刷一行。
     *
     * <p>聊天栏本来就只有进了世界才有，所以“游戏中不提示”等于游戏里什么都不刷；
     * “游戏中只走聊天栏”在游戏里刷、菜单里不刷（菜单里没有聊天栏可刷）。
     */
    public static boolean chatAllowed() {
        if (!SpaConfig.get().chatFeedback) {
            return false;
        }
        switch (toastMode()) {
            case SILENT:
                // 静默 = 什么都不提示
                return false;
            case INGAME_OFF:
                return !inGame();
            case ALL:
            case INGAME_CHAT_ONLY:
            default:
                return true;
        }
    }

    /** 现在允不允许响提示音（静默档连声音也一起关掉）。 */
    public static boolean soundAllowed() {
        return SpaConfig.get().soundFeedback && toastMode() != ToastMode.SILENT;
    }

    /** 已经进了世界（有玩家、有世界）才算“游戏中”。 */
    private static boolean inGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.world != null;
    }

    /** 聊天栏 + 系统小弹窗（普通的“正在敲门 / 已发出”之类）。 */
    private static void show(Text message) {
        if (!onMainThread(() -> show(message))) {
            return;
        }
        showChat(message);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !toastAllowed() || client.getToastManager() == null) {
            return;
        }
        SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.translatable("silent_knock.msg.toast_title"), message);
    }

    private static void showChat(Text message) {
        if (!onMainThread(() -> showChat(message))) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (chatAllowed() && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(message);
        }
        SilentSpaClient.LOGGER.info("[SPA] {}", message.getString());
    }

    /**
     * 消息 / 弹窗 / 音效都只能在渲染主线程碰。
     *
     * <p>服务器列表 Ping 的入口在原版的后台线程上，别处也可能有模组在自己的线程里调
     * {@link ExtraKnock}，所以这里兜一道底：不在主线程就把动作排回去。
     *
     * @return true 表示当前就在主线程，调用方可以继续
     */
    private static boolean onMainThread(Runnable again) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        if (client.isOnThread()) {
            return true;
        }
        client.execute(again);
        return false;
    }

    private static void play(net.minecraft.registry.entry.RegistryEntry<SoundEvent> sound, float pitch) {
        if (!soundAllowed()) {
            return;
        }
        if (!onMainThread(() -> play(sound, pitch))) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    private static void play(SoundEvent sound, float pitch) {
        if (!soundAllowed()) {
            return;
        }
        if (!onMainThread(() -> play(sound, pitch))) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }
}
