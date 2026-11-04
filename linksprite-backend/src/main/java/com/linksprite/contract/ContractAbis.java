package com.linksprite.contract;

import com.linksprite.common.ChainException;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合约构建产物（ABI / BIN）加载工具
 * <p>
 * - ABI 以各合约封装类内嵌常量为唯一事实来源，通过反射读取
 * - BIN（部署字节码）来自 solc 编译产物 classpath:/contracts/&lt;合约名&gt;.bin
 *   未编译部署前 BIN 不存在，此时部署会明确失败，绝不伪造
 */
public final class ContractAbis {

    /** 部署顺序（被依赖者在前） */
    public static final String[] DEPLOY_ORDER = {
            "KYCRegistry",
            "TransferPolicy",
            "AuthorizationRegistry",
            "LinkSpriteNFT",
            "SpriteComputeToken",
            "AIAccountFactory",
            "Paymaster"
    };

    private static final Map<String, Class<? extends BaseContract>> WRAPPERS = new LinkedHashMap<>();

    static {
        WRAPPERS.put("KYCRegistry", KYCRegistry.class);
        WRAPPERS.put("TransferPolicy", TransferPolicy.class);
        WRAPPERS.put("AuthorizationRegistry", AuthorizationRegistry.class);
        WRAPPERS.put("LinkSpriteNFT", LinkSpriteNFT.class);
        WRAPPERS.put("SpriteComputeToken", SpriteComputeToken.class);
        WRAPPERS.put("AIAccountFactory", AIAccountFactory.class);
        WRAPPERS.put("Paymaster", Paymaster.class);
    }

    private ContractAbis() {
    }

    /**
     * 读取合约封装类内嵌的 ABI 常量
     */
    public static String getAbi(String contractName) {
        Class<?> clazz = WRAPPERS.get(contractName);
        if (clazz == null) {
            throw new ChainException("未知合约: " + contractName);
        }
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField("ABI");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ChainException("读取合约 ABI 失败: " + contractName, e);
        }
    }

    /**
     * 加载 solc 编译产物 BIN（classpath:/contracts/&lt;name&gt;.bin）
     * 不存在或为空时抛出可操作的异常
     */
    public static String getBin(String contractName) {
        String resourcePath = "contracts/" + contractName + ".bin";
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new ChainException(
                    "缺少合约字节码文件 " + resourcePath
                            + "，请先在部署机运行 deploy/compile_contracts.sh（或 compile_contracts.ps1）编译 Solidity 合约");
        }
        try (InputStream in = resource.getInputStream()) {
            String bin = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (bin.isEmpty() || "[{...}]".equals(bin)) {
                throw new ChainException("合约字节码为空: " + resourcePath + "，请重新编译合约");
            }
            // 兼容 solc 输出是否带 0x 前缀，部署接口两种都接受，这里统一去除空白
            return bin;
        } catch (ChainException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainException("读取合约字节码失败: " + resourcePath, e);
        }
    }

    /**
     * BIN 资源是否已就位（用于部署前置检查）
     */
    public static boolean binExists(String contractName) {
        return new ClassPathResource("contracts/" + contractName + ".bin").exists();
    }

    public static Map<String, Class<? extends BaseContract>> wrappers() {
        return WRAPPERS;
    }
}
