package com.jade.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 额外开放一个只绑本机的明文 HTTP 连接器，供隧道服务转发。
 *
 * <p>为什么需要：对外端口已启用 TLS，而隧道（serveo / localhost.run 之类）在自己的边缘
 * 终结证书，回源时发的是明文 HTTP。若直接指到 8080，握手会失败。
 *
 * <p>安全：地址固定 127.0.0.1，公网访问不到；端口由 {@code jade.http-port} 控制，
 * 默认 0 表示不启用，线上不受影响。
 */
@Configuration
public class ExtraHttpConnectorConfig {

    @Value("${jade.http-port:0}")
    private int httpPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> extraHttpConnector() {
        return factory -> {
            if (httpPort <= 0) {
                return;
            }
            Connect c = new Connect();
            factory.addAdditionalTomcatConnectors(c.build(httpPort));
        };
    }

    private static class Connect {
        Connector build(int port) {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(port);
            connector.setProperty("address", "127.0.0.1"); // 只允许本机，隧道从本机转发进来
            connector.setScheme("http");
            connector.setSecure(false);
            // server.compression 只作用于自动配置的主连接器；隧道流量从这个连接器走，
            // 必须单独开压缩，否则静态库 145KB 明文出境，慢链路要等十几秒
            connector.setProperty("compression", "on");
            connector.setProperty("compressionMinSize", "1024");
            connector.setProperty("compressibleMimeType",
                    "text/html,text/css,text/javascript,application/javascript,application/json,image/svg+xml");
            return connector;
        }
    }
}
