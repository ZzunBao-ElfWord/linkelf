package com.linksprite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * FISCO BCOS 链连接配置
 * 所有链连接参数集中在此，从 application.yml 读取
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "fisco")
public class FiscoBcosConfig {

    /** 是否启用区块链连接（dev 环境无节点时设为 false） */
    private boolean enabled = false;

    /** 加密类型：ECDSA 或 SM（国密），必须与链节点一致 */
    private String cryptoType = "SM";

    /** 节点列表（IP:ChannelPort） */
    private String[] peers;

    /** 群组 ID（SDK 3.x 使用字符串群组名，如 group0） */
    private String groupId = "group0";

    /** 链 ID */
    private String chainId = "linksprite_chain";

    /** 证书目录路径 */
    private String certPath = "classpath:cert";

    /** 账户配置 */
    private Account account = new Account();

    /** 线程池配置 */
    private ThreadPool threadPool = new ThreadPool();

    /** 是否在启动时检查节点可达性 */
    private boolean checkNodeOnStartup = true;

    /** 节点连接超时（毫秒） */
    private int connectTimeout = 5000;

    @Data
    public static class Account {
        /** 账户文件格式：pem 或 p12 */
        private String fileFormat = "pem";
        /** 账户文件路径（相对于证书目录） */
        private String accountFile = "sdk.pem";
        /** 账户地址（留空则从账户文件读取） */
        private String accountAddress;
        /** 账户密码（p12 格式时使用） */
        private String password = "";
    }

    @Data
    public static class ThreadPool {
        private int corePoolSize = 10;
        private int maxPoolSize = 50;
        private int queueCapacity = 100;
    }
}
