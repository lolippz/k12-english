package com.k12english.config;

import java.awt.Desktop;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.k12english.repo.UserRepository;

/**
 * 启动完成后打印访问地址，并按配置自动打开浏览器。
 *
 * <p>端口从 {@link Environment} 读实际生效值（而不是配置里的默认值），
 * 这样 {@code --server.port=0} 随机端口或命令行覆盖端口时也能打开正确的地址。
 */
@Component
public class StartupRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    /** 源码目录下的前端产物位置，与 application.yml 里追加的静态资源位置保持一致。 */
    private static final Path DEV_STATIC = Path.of("frontend", "dist");

    private final AppProperties props;
    private final Environment env;
    private final UserRepository users;

    public StartupRunner(AppProperties props, Environment env, UserRepository users) {
        this.props = props;
        this.env = env;
        this.users = users;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warnIfFrontendMissing();

        String port = env.getProperty("local.server.port", "8080");
        String url = "http://127.0.0.1:" + port;
        log.info("英语学习平台已就绪，本机访问 {}", url);
        log.info("学习进度库: {}", props.databaseFile().toAbsolutePath());
        logAccessInfo(port);

        if (!props.isOpenBrowser()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                log.info("当前环境不支持自动打开浏览器，请手动访问 {}", url);
            }
        } catch (Exception e) {
            // 打不开浏览器不影响使用，用户手动访问即可
            log.warn("自动打开浏览器失败，请手动访问 {}（{}）", url, e.getMessage());
        }
    }

    /**
     * 打印监听范围与账号状态。
     *
     * <p>监听地址是 {@code 127.0.0.1} 时不打印局域网地址：那种情况下
     * 同网段的其他机器根本连不上，打印出来只会误导。
     */
    private void logAccessInfo(String port) {
        String bind = env.getProperty("server.address", "");
        boolean loopbackOnly = "127.0.0.1".equals(bind)
                || "::1".equals(bind)
                || "localhost".equalsIgnoreCase(bind);

        if (loopbackOnly) {
            log.info("监听地址: {}（仅本机可访问，同网段的其他机器看不到这个端口）", bind);
        } else {
            log.info("监听地址: {}（已开放局域网，同网段设备可访问）", bind.isEmpty() ? "0.0.0.0" : bind);
            List<String> lan = lanAddresses();
            if (lan.isEmpty()) {
                log.info("没检测到局域网 IPv4 地址（可能没连网络）");
            } else {
                for (String ip : lan) {
                    log.info("局域网访问: http://{}:{}  （手机/平板连同一 WiFi 可直接打开）", ip, port);
                }
            }
        }

        long n = users.countUsers();
        if (n == 0) {
            log.info("还没有账号：打开页面注册第一个账号，它会自动成为管理员，无需邀请码");
        } else {
            log.info("已有 {} 个账号，打开页面登录即可", n);
        }
    }

    /** 列出本机所有非回环、非虚拟网卡的 IPv4 地址。 */
    private static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("枚举网卡失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 前端产物缺失时大声报警。
     *
     * <p>Maven 的 {@code with-frontend} profile 是按「frontend/dist 是否存在」条件激活的，
     * 所以没构建前端时 {@code mvn package} 也能过——只是打出来的 jar 里没有界面。
     * 这种情况必须在启动时说清楚，否则用户只会看到一个 404 首页，无从排查。
     *
     * <p>注意：{@code application.yml} 把 {@code file:frontend/dist/} 追加成了静态资源位置，
     * 所以从 IDEA 直接 Run（classpath 里没有 static/，但源码目录有产物）时界面是正常的，
     * 这里不能误报。
     */
    private void warnIfFrontendMissing() {
        if (new ClassPathResource("static/index.html").exists()) {
            return;
        }
        if (Files.isReadable(DEV_STATIC.resolve("index.html"))) {
            log.info("classpath 里没有前端产物，改用源码目录 {}（开发模式）", DEV_STATIC.toAbsolutePath());
            return;
        }
        log.warn("""

                ============================================================
                警告：jar 里没有前端界面（classpath:static/index.html 不存在），
                源码目录 {} 下也没有产物，首页会返回 404。
                构建前端产物后再重新打包即可：

                    cd frontend && npm install && npm run build
                    cd .. && ./mvnw package

                ============================================================""", DEV_STATIC.toAbsolutePath());
    }
}
