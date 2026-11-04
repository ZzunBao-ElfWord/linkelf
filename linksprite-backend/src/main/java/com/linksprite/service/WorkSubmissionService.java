package com.linksprite.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linksprite.dto.request.WorkSubmissionRequest;
import com.linksprite.entity.WorkSubmission;
import com.linksprite.mapper.WorkSubmissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 作品提交信息收单服务
 *
 * 幂等：以 seriesId 为幂等键，同一系列重复提交只落一条，返回既有记录 ID。
 */
@Service
@ConditionalOnProperty(prefix = "linksprite.work-submission", name = "enabled", havingValue = "true")
public class WorkSubmissionService {

    @Autowired
    private WorkSubmissionMapper mapper;

    /**
     * 收单：落库并返回记录 ID
     */
    public Long submit(WorkSubmissionRequest req) {
        WorkSubmission existing = findBySeriesId(req.getSeriesId());
        if (existing != null) {
            return existing.getId();
        }

        WorkSubmission entity = new WorkSubmission();
        entity.setSeriesId(req.getSeriesId());
        entity.setGeneratorJobId(req.getGeneratorJobId());
        entity.setTotalVersions(req.getTotalVersions());
        entity.setFormat(req.getFormat());
        entity.setCoverUrl(req.getCoverUrl());
        entity.setVersionsJson(toJson(req.getVersions()));

        entity.setCreatorName(req.getCreatorName());
        entity.setCreatorAvatarUrl(req.getCreatorAvatarUrl());
        entity.setCreatorIntro(req.getCreatorIntro());

        entity.setIssueType(req.getIssueType());
        entity.setProductName(req.getProductName());
        entity.setBlindBoxCount(req.getBlindBoxCount());
        entity.setPriceTier(req.getPriceTier());
        entity.setPresaleMonth(req.getPresaleMonth());
        entity.setPresaleThresholdQty(req.getPresaleThresholdQty());
        entity.setPresaleRuleHtml(req.getPresaleRuleHtml());

        entity.setWorksJson(toJson(req.getWorks()));
        entity.setSettlementJson(toJson(req.getSettlementTiers()));
        entity.setCommissionJson(toJson(req.getCommission()));

        entity.setRawPayload(toJson(req));
        entity.setStatus("received");

        try {
            mapper.insert(entity);
            return entity.getId();
        } catch (DuplicateKeyException e) {
            // 并发下唯一键冲突：回读既有记录，保证幂等
            WorkSubmission raced = findBySeriesId(req.getSeriesId());
            if (raced != null) {
                return raced.getId();
            }
            throw e;
        }
    }

    private WorkSubmission findBySeriesId(String seriesId) {
        return mapper.selectOne(
                new LambdaQueryWrapper<WorkSubmission>().eq(WorkSubmission::getSeriesId, seriesId));
    }

    private String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }
}
