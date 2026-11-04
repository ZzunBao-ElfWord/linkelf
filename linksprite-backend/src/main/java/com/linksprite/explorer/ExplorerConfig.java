package com.linksprite.explorer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览器装配。
 * <p>
 * 仅在 explorer.enabled=true 时启用定时任务与事务模板，
 * 未开启时既不注册调度器，也不创建任何浏览器 Bean，对现有服务零影响。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "explorer", name = "enabled", havingValue = "true")
public class ExplorerConfig {

    /** 每区块一条事务：区块 + 交易 + 日志 + 事件解析结果 */
    @Bean
    public TransactionTemplate explorerTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /** 链上只读查询线程池：用于 tokenURI 等带超时的调用，避免阻塞请求线程 */
    @Bean(name = "explorerChainExecutor", destroyMethod = "shutdown")
    public ExecutorService explorerChainExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "explorer-chain-query");
            thread.setDaemon(true);
            return thread;
        });
    }
}
