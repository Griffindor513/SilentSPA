package cn.blockforge.generated.silentknock.spa;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 异步 UDP 发送器。
 *
 * <p>所有 socket 的创建、发送、关闭都发生在专门的守护线程池里，
 * 绝不会碰渲染主线程；每个操作都有硬超时，超时后按失败处理。
 */
public final class SpaSender {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "SilentSPA-UDP");
        thread.setDaemon(true);
        return thread;
    });

    private SpaSender() {
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR);
    }

    /**
     * 发送一个 UDP 包，返回的 Future 在发送成功或失败时完成。
     *
     * @param timeoutMs 超时上限，超时后即使底层线程还卡着，调用链也会继续往下走
     */
    public static CompletableFuture<Void> send(String host, int port, byte[] payload, int timeoutMs) {
        long deadline = Math.max(100, timeoutMs);
        return CompletableFuture.runAsync(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(Math.max(100, timeoutMs));
                InetAddress address = InetAddress.getByName(host);
                DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
                socket.send(packet);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR).orTimeout(deadline, TimeUnit.MILLISECONDS);
    }
}
