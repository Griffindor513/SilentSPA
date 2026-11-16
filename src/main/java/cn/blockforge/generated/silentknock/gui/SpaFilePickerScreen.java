package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.spa.SpaKeyVault;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 模组内置的文件选择界面（纯 Minecraft UI，不依赖系统对话框，跨平台不会崩）。
 *
 * <p>点目录进入，点文件立即绑定并把绝对路径交回调用方。
 *
 * <p>底下还留了一个「新建 fwknoprc」入口：不用玩家自己去别处准备文件，
 * 直接在游戏里填好两把密钥和选项，存成 fwknoprc 再绑定。
 * 列表里 {@code .fwknoprc} 结尾的文件会排在最前面。
 */
public final class SpaFilePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 56;
    private static final int MAX_ENTRIES = 2000;

    private final Screen parent;
    private final Consumer<Path> onPick;
    /** 上一个界面里填的服务器地址，新建密钥文件时用作默认文件名。 */
    private final String suggestedServer;
    private final List<Path> entries = new ArrayList<>();

    private Path directory;
    private int page;
    private Text error;

    public SpaFilePickerScreen(Screen parent, Consumer<Path> onPick) {
        this(parent, "", onPick);
    }

    public SpaFilePickerScreen(Screen parent, String suggestedServer, Consumer<Path> onPick) {
        super(Text.translatable("silent_knock.picker.title"));
        this.parent = parent;
        this.onPick = onPick;
        this.suggestedServer = suggestedServer == null ? "" : suggestedServer;
        this.directory = initialDirectory();
    }

    private static Path initialDirectory() {
        String last = SpaConfig.get().lastKeyDir;
        if (last != null && !last.isBlank()) {
            try {
                Path candidate = Path.of(last).toAbsolutePath().normalize();
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // 配置里的路径坏了就继续往下退
            }
        }
        // 没选过文件的话，先落到模组自己放密钥文件的目录（新建过密钥的玩家会在这里）
        Path keys = SpaKeyVault.keysDirectory();
        if (Files.isDirectory(keys)) {
            return keys;
        }
        return Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        this.clearChildren();
        this.loadEntries();

        int centerX = this.width / 2;
        int rows = this.rowsPerPage();
        int from = Math.min(this.entries.size(), this.page * rows);
        int to = Math.min(this.entries.size(), from + rows);
        for (int i = from; i < to; i++) {
            Path path = this.entries.get(i);
            boolean isDir = Files.isDirectory(path);
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String label = (isDir ? "> " : "  ") + name;
            ButtonWidget row = ButtonWidget.builder(Text.literal(label), button -> this.onEntry(path, isDir))
                    .dimensions(centerX - 150, LIST_TOP + (i - from) * ROW_HEIGHT, 300, 20)
                    .build();
            this.addDrawableChild(row);
        }

        int bottom = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("silent_knock.picker.up"),
                        button -> this.goUp())
                .dimensions(centerX - 150, bottom, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("silent_knock.picker.create"),
                        button -> this.openCreate())
                .dimensions(centerX - 58, bottom, 120, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.picker.create.tooltip")))
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("silent_knock.picker.cancel"),
                        button -> this.close())
                .dimensions(centerX + 70, bottom, 80, 20)
                .build());

        ButtonWidget prev = ButtonWidget.builder(Text.translatable("silent_knock.picker.prev"),
                        button -> this.changePage(-1))
                .dimensions(centerX - 150, this.height - 52, 60, 20)
                .build();
        prev.active = this.page > 0;
        this.addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.translatable("silent_knock.picker.next"),
                        button -> this.changePage(1))
                .dimensions(centerX + 90, this.height - 52, 60, 20)
                .build();
        next.active = (this.page + 1) * rows < this.entries.size();
        this.addDrawableChild(next);
    }

    private int rowsPerPage() {
        return Math.max(1, (this.height - LIST_TOP - 76) / ROW_HEIGHT);
    }

    private void loadEntries() {
        this.entries.clear();
        this.error = null;
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory)) {
            int seen = 0;
            for (Path path : stream) {
                if (++seen > MAX_ENTRIES) {
                    break;
                }
                if (Files.isDirectory(path)) {
                    dirs.add(path);
                } else {
                    files.add(path);
                }
            }
        } catch (Exception e) {
            this.error = Text.translatable("silent_knock.picker.error",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        Comparator<Path> byName = Comparator
                .comparingInt((Path p) -> silentspa$isFwknoprc(p) ? 0 : 1)
                .thenComparing(p -> p.getFileName() == null
                        ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT));
        dirs.sort(byName);
        files.sort(byName);
        this.entries.addAll(dirs);
        this.entries.addAll(files);

        int maxPage = Math.max(0, (this.entries.size() - 1) / this.rowsPerPage());
        if (this.page > maxPage) {
            this.page = maxPage;
        }
    }

    /** {@code .fwknoprc} 结尾的文件排前面，方便直接点。 */
    private static boolean silentspa$isFwknoprc(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".fwknoprc");
    }

    private void onEntry(Path path, boolean isDir) {
        if (isDir) {
            this.directory = path;
            this.page = 0;
            this.rebuildLater();
            return;
        }
        this.pick(path);
    }

    private void pick(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        SpaConfig config = SpaConfig.get();
        Path parentDir = absolute.getParent();
        config.lastKeyDir = parentDir == null ? this.directory.toString() : parentDir.toString();
        config.save();
        this.onPick.accept(absolute);
        this.client.setScreen(this.parent);
    }

    /** 打开“新建 fwknoprc”界面；保存后由它把路径交回 {@link #onPick}。 */
    private void openCreate() {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new SpaKeyCreateScreen(this.parent, this, this.suggestedServer, this.onPick));
    }

    private void goUp() {
        Path parent = this.directory.getParent();
        if (parent == null) {
            return;
        }
        this.directory = parent;
        this.page = 0;
        this.rebuildLater();
    }

    private void changePage(int delta) {
        int target = this.page + delta;
        int rows = this.rowsPerPage();
        int maxPage = Math.max(0, (this.entries.size() - 1) / rows);
        if (target < 0 || target > maxPage) {
            return;
        }
        this.page = target;
        this.rebuildLater();
    }

    /**
     * 按钮回调发生在 {@code Screen.mouseClicked} 遍历子控件的过程中，
     * 此时直接 clearChildren 会触发 ConcurrentModificationException，
     * 所以把重建推迟到下一次主线程任务。
     */
    private void rebuildLater() {
        if (this.client == null) {
            return;
        }
        this.client.send(() -> {
            if (this.client.currentScreen == this) {
                this.init(this.client, this.width, this.height);
            }
        });
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景（含模糊）由 Screen.renderWithTooltip 在每帧统一画一次；
        // 这里再调一次 renderBackground 会触发 “Can only blur once per frame” 崩溃。
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 12, 0xFFFFFFFF);
        String pathText = this.textRenderer.trimToWidth(this.directory.toString(), Math.max(40, this.width - 40));
        context.drawCenteredTextWithShadow(this.textRenderer, pathText, centerX, 30, 0xFF9E9E9E);

        Text hint;
        int color;
        if (this.error != null) {
            hint = this.error;
            color = 0xFFFF6B6B;
        } else if (this.entries.isEmpty()) {
            hint = Text.translatable("silent_knock.picker.empty");
            color = 0xFFB0B0B0;
        } else {
            hint = Text.translatable("silent_knock.picker.hint");
            color = 0xFF808080;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, hint, centerX, 44, color);
    }
}
