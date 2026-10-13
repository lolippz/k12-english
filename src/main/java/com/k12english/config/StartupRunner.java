package com.k12english.config;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 启动完成后打印访问地址，并按配置自动打开浏览器。
 *
 * <p>端口从 {@link Environment} 读实际生效值（而不是配置里的默认值），
 * 这样 {@code --server.port=0} 随机端口或命令行覆盖端口时也能打开正确的地址。
 */
@Component
public class StartupRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final AppProperties props;
    private final Environment env;

    public StartupRunner(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warnIfFrontendMissing();

        String url = "http://127.0.0.1:" + env.getProperty("local.server.port", "8080");
        log.info("英语学习平台已就绪，浏览器访问 {}", url);
        log.info("学习进度库: {}", props.databaseFile().toAbsolutePath());

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
     * 前端产物缺失时大声报警。
     *
     * <p>Maven 的 {@code with-frontend} profile 是按「frontend/dist 是否存在」条件激活的，
     * 所以没构建前端时 {@code mvn package} 也能过——只是打出来的 jar 里没有界面。
     * 这种情况必须在启动时说清楚，否则用户只会看到一个 404 首页，无从排查。
     */
    private void warnIfFrontendMissing() {
        if (new ClassPathResource("static/index.html").exists()) {
            return;
        }
        log.warn("""

                ============================================================
                警告：jar 里没有前端界面（classpath:static/index.html 不存在）
                首页会返回 404。构建前端产物后再重新打包即可：

                    cd frontend && npm install && npm run build
                    cd .. && ./mvnw package

                ============================================================"""); 
    }
}
