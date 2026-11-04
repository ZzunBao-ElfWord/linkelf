package com.linksprite.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.model.ConfigProperty;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FISCO BCOS SDK 核心配置
 * <p>
 * 真实上链硬前置条件（任一不满足则直接失败）：
 * 1. 节点可达（peer 端口监听存在）
 * 2. SDK 配置可解析（依赖齐全、字段正确）
 * 3. 证书与节点匹配（ca.crt/sdk.crt/sdk.key 来自真实节点）
 * <p>
 * 不允许"模拟成功"，SDK 未初始化时直接抛异常终止启动。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "fisco", name = "enabled", havingValue = "true")
public class FiscoBcosClientConfig {

    @Autowired
    private FiscoBcosConfig fiscoBcosConfig;

    @Autowired
    private ResourceLoader resourceLoader;

    private Client client;
    private CryptoKeyPair cryptoKeyPair;

    @PostConstruct
    public void init() {
        log.info("========== 初始化 FISCO BCOS SDK ==========");
        log.info("加密类型: {}, 群组: {}, 链ID: {}",
                fiscoBcosConfig.getCryptoType(),
                fiscoBcosConfig.getGroupId(),
                fiscoBcosConfig.getChainId());
        log.info("节点列表: {}", Arrays.toString(fiscoBcosConfig.getPeers()));

        try {
            // 前置检查 1：证书目录与文件
            String certDir = getCertAbsolutePath();
            checkCertFiles(certDir);
            log.info("证书目录校验通过: {}", certDir);

            // 前置检查 2：节点可达性
            if (fiscoBcosConfig.isCheckNodeOnStartup()) {
                checkNodesReachable();
                log.info("节点可达性检查通过");
            }

            // 构建 SDK 配置
            ConfigProperty configProperty = buildConfigProperty(certDir);
            ConfigOption configOption;
            try {
                configOption = new ConfigOption(configProperty);
            } catch (Exception e) {
                // 配置解析失败通常是字段名/类型错误或依赖缺失（如 toml4j）
                log.error("SDK 配置解析失败，请检查配置字段与依赖。根因: {}",
                        unwrapCause(e).getMessage(), e);
                throw new IllegalStateException("SDK 配置解析失败: " + unwrapCause(e).getMessage(), e);
            }

            // 创建 BcosSDK
            BcosSDK bcosSDK;
            try {
                bcosSDK = new BcosSDK(configOption);
            } catch (Exception e) {
                Throwable root = unwrapCause(e);
                log.error("BcosSDK 创建失败，根因: {} - {}", root.getClass().getName(), root.getMessage());
                // 常见根因：toml4j 等依赖缺失、证书格式错误
                throw new IllegalStateException("BcosSDK 创建失败: " + root.getMessage(), e);
            }

            // 获取 Client（SDK 3.x 群组 ID 为字符串，如 group0）
            this.client = bcosSDK.getClient(fiscoBcosConfig.getGroupId());
            log.info("Client 获取成功，群组: {}", fiscoBcosConfig.getGroupId());

            // 加载账户密钥
            this.cryptoKeyPair = loadCryptoKeyPair(certDir);
            log.info("账户加载成功，地址: {}", cryptoKeyPair.getAddress());

            log.info("==========================================");
            log.info("FISCO BCOS SDK 初始化完成");
            log.info("==========================================");

        } catch (Exception e) {
            Throwable root = unwrapCause(e);
            log.error("FISCO BCOS SDK 初始化失败，根因: {} - {}",
                    root.getClass().getSimpleName(), root.getMessage(), e);
            // 不做任何降级或模拟，直接失败
            throw new IllegalStateException(
                    "FISCO BCOS SDK 初始化失败: " + root.getMessage()
                            + "。请检查：节点是否启动、证书是否匹配、加密类型是否一致", e);
        }
    }

    /**
     * 构建 SDK 配置（SDK 3.x ConfigProperty 结构）
     */
    private ConfigProperty buildConfigProperty(String certDir) {
        ConfigProperty property = new ConfigProperty();
        boolean sm = "SM".equalsIgnoreCase(fiscoBcosConfig.getCryptoType());

        // 网络配置
        Map<String, Object> network = new HashMap<>();
        network.put("peers", new ArrayList<>(Arrays.asList(fiscoBcosConfig.getPeers())));
        network.put("defaultGroup", fiscoBcosConfig.getGroupId());
        network.put("messageTimeout", String.valueOf(fiscoBcosConfig.getConnectTimeout()));
        property.setNetwork(network);

        // 证书与加密材料
        Map<String, Object> cryptoMaterial = new HashMap<>();
        cryptoMaterial.put("certPath", certDir);
        cryptoMaterial.put("useSmCrypto", String.valueOf(sm));
        if (sm) {
            // 国密证书
            cryptoMaterial.put("caCert", new File(certDir, "sm_ca.crt").getAbsolutePath());
            cryptoMaterial.put("sslCert", new File(certDir, "sm_sdk.crt").getAbsolutePath());
            cryptoMaterial.put("sslKey", new File(certDir, "sm_sdk.key").getAbsolutePath());
            cryptoMaterial.put("enSslCert", new File(certDir, "sm_ensdk.crt").getAbsolutePath());
            cryptoMaterial.put("enSslKey", new File(certDir, "sm_ensdk.key").getAbsolutePath());
        } else {
            cryptoMaterial.put("caCert", new File(certDir, "ca.crt").getAbsolutePath());
            cryptoMaterial.put("sslCert", new File(certDir, "sdk.crt").getAbsolutePath());
            cryptoMaterial.put("sslKey", new File(certDir, "sdk.key").getAbsolutePath());
        }
        property.setCryptoMaterial(cryptoMaterial);

        // 账户配置
        Map<String, Object> account = new HashMap<>();
        account.put("keyStoreDir", certDir);
        account.put("accountFileFormat", fiscoBcosConfig.getAccount().getFileFormat());
        account.put("accountFilePath",
                new File(certDir, fiscoBcosConfig.getAccount().getAccountFile()).getAbsolutePath());
        account.put("accountAddress",
                fiscoBcosConfig.getAccount().getAccountAddress() == null ? "" : fiscoBcosConfig.getAccount().getAccountAddress());
        account.put("accountPassword",
                fiscoBcosConfig.getAccount().getPassword() == null ? "" : fiscoBcosConfig.getAccount().getPassword());
        property.setAccount(account);

        // AMOP 配置（暂不使用，传空列表）
        property.setAmop(new ArrayList<>());

        // 线程池配置
        Map<String, Object> threadPool = new HashMap<>();
        threadPool.put("threadPoolSize",
                String.valueOf(fiscoBcosConfig.getThreadPool() != null
                        ? fiscoBcosConfig.getThreadPool().getMaxPoolSize() : 16));
        property.setThreadPool(threadPool);

        return property;
    }

    /**
     * 加载账户密钥对
     * SDK 3.x：cryptoSuite.loadAccount("pem", path, null) 从 PEM 文件加载账户，
     * 再通过 getCryptoKeyPair() 获取密钥对（loadKeyPair(String) 接收的是 hex 私钥，非文件路径）
     */
    private CryptoKeyPair loadCryptoKeyPair(String certDir) throws IOException {
        String accountFilePath = certDir + File.separator + fiscoBcosConfig.getAccount().getAccountFile();
        File pemFile = new File(accountFilePath);

        if (!pemFile.exists()) {
            throw new IllegalStateException("账户文件不存在: " + accountFilePath
                    + "，请将节点 SDK 证书目录中的 sdk.pem 放入证书目录");
        }

        CryptoSuite cryptoSuite = client.getCryptoSuite();
        try {
            cryptoSuite.loadAccount(fiscoBcosConfig.getAccount().getFileFormat(),
                    accountFilePath,
                    fiscoBcosConfig.getAccount().getPassword());
            CryptoKeyPair keyPair = cryptoSuite.getCryptoKeyPair();
            if (keyPair == null) {
                throw new IllegalStateException("loadAccount 后未获取到密钥对: " + accountFilePath);
            }
            return keyPair;
        } catch (Exception e) {
            log.error("从 PEM 文件加载密钥对失败: {}", e.getMessage());
            // 备选：配置了账户地址时按地址加载
            if (fiscoBcosConfig.getAccount().getAccountAddress() != null
                    && !fiscoBcosConfig.getAccount().getAccountAddress().isEmpty()) {
                log.info("尝试用配置的账户地址加载: {}", fiscoBcosConfig.getAccount().getAccountAddress());
                cryptoSuite.loadAccount(fiscoBcosConfig.getAccount().getFileFormat(),
                        fiscoBcosConfig.getAccount().getAccountAddress(),
                        fiscoBcosConfig.getAccount().getPassword());
                return cryptoSuite.getCryptoKeyPair();
            }
            throw new IllegalStateException("无法加载账户密钥对: " + accountFilePath, e);
        }
    }

    /**
     * 校验证书文件是否齐全
     */
    private void checkCertFiles(String certDir) {
        boolean sm = "SM".equalsIgnoreCase(fiscoBcosConfig.getCryptoType());
        String[] requiredFiles;
        if (sm) {
            requiredFiles = new String[]{
                    "sm_ca.crt", "sm_sdk.crt", "sm_sdk.key", "sm_ensdk.crt", "sm_ensdk.key",
                    fiscoBcosConfig.getAccount().getAccountFile()};
        } else {
            requiredFiles = new String[]{
                    "ca.crt", "sdk.crt", "sdk.key",
                    fiscoBcosConfig.getAccount().getAccountFile()};
        }
        for (String fileName : requiredFiles) {
            File file = new File(certDir, fileName);
            if (!file.exists()) {
                throw new IllegalStateException("证书文件缺失: " + file.getAbsolutePath()
                        + "。请从 FISCO BCOS 节点 SDK 目录复制完整证书集");
            }
        }
    }

    /**
     * 检查节点可达性
     * 至少有一个节点可达才继续；全部不可达则失败
     */
    private void checkNodesReachable() {
        String[] peers = fiscoBcosConfig.getPeers();
        if (peers == null || peers.length == 0) {
            throw new IllegalStateException("未配置任何节点（fisco.peers）");
        }

        boolean anyReachable = false;
        for (String peer : peers) {
            String[] parts = peer.split(":");
            if (parts.length != 2) {
                log.warn("节点地址格式错误（应为 IP:Port）: {}", peer);
                continue;
            }
            String host = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("节点端口格式错误: {}", peer);
                continue;
            }

            if (isPortReachable(host, port)) {
                log.info("节点可达: {}:{}", host, port);
                anyReachable = true;
            } else {
                log.warn("节点不可达: {}:{}", host, port);
            }
        }

        if (!anyReachable) {
            throw new IllegalStateException("所有节点均不可达，请确认 FISCO BCOS 节点已启动且端口正确。"
                    + "配置的节点: " + Arrays.toString(peers));
        }
    }

    /**
     * 检测端口是否可达
     */
    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), fiscoBcosConfig.getConnectTimeout());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取证书目录绝对路径
     */
    private String getCertAbsolutePath() throws IOException {
        Resource resource = resourceLoader.getResource(fiscoBcosConfig.getCertPath());
        if (resource.exists()) {
            return resource.getFile().getAbsolutePath();
        }
        throw new IllegalStateException("证书目录不存在: " + fiscoBcosConfig.getCertPath()
                + "，请将 ca.crt/sdk.crt/sdk.key/sdk.pem 放入该目录");
    }

    /**
     * 解包异常根因
     * 处理 InvocationTargetException 和嵌套异常，获取真正的错误信息
     */
    private Throwable unwrapCause(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof InvocationTargetException ite
                    && ite.getTargetException() != null) {
                current = ite.getTargetException();
            } else if (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            } else {
                break;
            }
            depth++;
        }
        return current;
    }

    @Bean
    public Client bcosClient() {
        return client;
    }

    @Bean
    public CryptoKeyPair cryptoKeyPair() {
        return cryptoKeyPair;
    }
}
