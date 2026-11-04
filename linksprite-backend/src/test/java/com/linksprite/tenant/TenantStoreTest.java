package com.linksprite.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantStore 单元测试（不启动 Spring 上下文，避免触发 FISCO 连接）。
 */
class TenantStoreTest {

    @TempDir
    Path tempDir;

    private String filePath;
    private TenantStore store;

    @BeforeEach
    void setUp() {
        filePath = tempDir.resolve("tenants.json").toString();
        store = new TenantStore(filePath);
    }

    @Test
    void createThenFindAndList() {
        String key = store.create("openelf", "OpenElf", 50, 100000, List.of("nft", "read"));

        // key 格式：lsk_<productId>_<32 hex>
        assertTrue(key.startsWith("lsk_openelf_"));
        String hex = key.substring(key.lastIndexOf('_') + 1);
        assertEquals(32, hex.length());
        assertTrue(hex.matches("[0-9a-f]{32}"));
        assertTrue(Files.exists(tempDir.resolve("tenants.json")));

        Tenant tenant = store.findByKeyHash(TenantStore.sha256Hex(key));
        assertNotNull(tenant);
        assertEquals("openelf", tenant.getProductId());
        assertEquals("OpenElf", tenant.getName());
        assertEquals(Tenant.STATUS_ACTIVE, tenant.getStatus());
        assertEquals(List.of("nft", "read"), tenant.getScopes());
        // keyHint：前6…后4
        assertEquals(key.substring(0, 6) + "…" + key.substring(key.length() - 4), tenant.getKeyHint());

        // 列表不暴露完整 keyHash，只保留 keyHint
        List<Tenant> list = store.list();
        assertEquals(1, list.size());
        assertNull(list.get(0).getKeyHash());
        assertNotNull(list.get(0).getKeyHint());
    }

    @Test
    void rotateInvalidatesOldKey() {
        String oldKey = store.create("koko", "KOKO", 50, 100000, List.of("mint", "read"));
        String newKey = store.rotate("koko");

        assertNotEquals(oldKey, newKey);
        assertNull(store.findByKeyHash(TenantStore.sha256Hex(oldKey)));

        Tenant tenant = store.findByKeyHash(TenantStore.sha256Hex(newKey));
        assertNotNull(tenant);
        assertNotNull(tenant.getRotatedAt());

        // 轮换结果已持久化：重新加载后新 key 可用、旧 key 失效
        TenantStore reopened = new TenantStore(filePath);
        assertNotNull(reopened.findByKeyHash(TenantStore.sha256Hex(newKey)));
        assertNull(reopened.findByKeyHash(TenantStore.sha256Hex(oldKey)));
    }

    @Test
    void revokeTenant() {
        String key = store.create("openelf", "OpenElf", 50, 100000, List.of("read"));
        store.revoke("openelf");

        Tenant tenant = store.findByKeyHash(TenantStore.sha256Hex(key));
        assertNotNull(tenant);
        assertEquals(Tenant.STATUS_REVOKED, tenant.getStatus());
    }

    @Test
    void reloadFromFile() {
        String key = store.create("openelf", "OpenElf", 50, 100000, List.of("read"));

        // 模拟进程重启：重新构造实例即从磁盘加载
        TenantStore reopened = new TenantStore(filePath);
        Tenant tenant = reopened.findByKeyHash(TenantStore.sha256Hex(key));
        assertNotNull(tenant);
        assertEquals("openelf", tenant.getProductId());
    }

    @Test
    void missingFileTreatedAsEmpty() {
        TenantStore emptyStore = new TenantStore(tempDir.resolve("not-exists.json").toString());
        assertTrue(emptyStore.list().isEmpty());
    }

    @Test
    void duplicateProductIdConflicts() {
        store.create("openelf", "OpenElf", 50, 100000, List.of("read"));
        TenantException exception = assertThrows(TenantException.class,
                () -> store.create("openelf", "OpenElf 2", 50, 100000, List.of("read")));
        assertEquals(409, exception.getStatus());
    }

    @Test
    void reloadDiscardsInMemoryChanges() {
        store.create("openelf", "OpenElf", 50, 100000, List.of("read"));
        // 删除磁盘文件后 reload：视为空列表
        try {
            Files.delete(tempDir.resolve("tenants.json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        store.reload();
        assertTrue(store.list().isEmpty());
    }
}
