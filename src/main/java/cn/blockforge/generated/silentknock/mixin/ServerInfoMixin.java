package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.SpaHooks;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给原版 {@link ServerInfo} 加上 SPA 绑定字段，并写进 servers.dat。
 *
 * <p>NBT 里只存 <b>绝对路径</b> 和可选端口，密钥内容永远不落盘。
 */
@Mixin(ServerInfo.class)
public abstract class ServerInfoMixin implements SpaHooks {
    @Unique
    private String silentspa$keyPath;

    @Unique
    private int silentspa$spaPort = -1;

    @Override
    public String silentspa$getKeyPath() {
        return this.silentspa$keyPath;
    }

    @Override
    public void silentspa$setKeyPath(String path) {
        this.silentspa$keyPath = path == null || path.isBlank() ? null : path.strip();
    }

    @Override
    public int silentspa$getSpaPort() {
        return this.silentspa$spaPort;
    }

    @Override
    public void silentspa$setSpaPort(int port) {
        this.silentspa$spaPort = port > 0 && port <= 65535 ? port : -1;
    }

    @Inject(method = "toNbt", at = @At("RETURN"))
    private void silentspa$writeNbt(CallbackInfoReturnable<NbtCompound> cir) {
        NbtCompound nbt = cir.getReturnValue();
        if (nbt == null) {
            return;
        }
        if (this.silentspa$keyPath != null) {
            nbt.putString("SpaKeyPath", this.silentspa$keyPath);
        }
        if (this.silentspa$spaPort > 0) {
            nbt.putInt("SpaPort", this.silentspa$spaPort);
        }
    }

    @Inject(method = "fromNbt", at = @At("RETURN"))
    private static void silentspa$readNbt(NbtCompound nbt, CallbackInfoReturnable<ServerInfo> cir) {
        ServerInfo info = cir.getReturnValue();
        if (info == null || nbt == null) {
            return;
        }
        SpaHooks hooks = (SpaHooks) (Object) info;
        if (nbt.contains("SpaKeyPath")) {
            hooks.silentspa$setKeyPath(nbt.getString("SpaKeyPath", ""));
        }
        if (nbt.contains("SpaPort")) {
            hooks.silentspa$setSpaPort(nbt.getInt("SpaPort", -1));
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void silentspa$copyFrom(ServerInfo other, CallbackInfo ci) {
        if (other == null) {
            return;
        }
        SpaHooks source = (SpaHooks) (Object) other;
        this.silentspa$keyPath = source.silentspa$getKeyPath();
        this.silentspa$spaPort = source.silentspa$getSpaPort();
    }
}
