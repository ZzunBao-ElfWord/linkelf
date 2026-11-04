package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.request.WorkSubmissionRequest;
import com.linksprite.service.WorkSubmissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作品提交信息收单 API
 *
 * 对外路径：POST /api/work-submissions（context-path=/api）
 */
@RestController
@RequestMapping("/work-submissions")
@ConditionalOnProperty(prefix = "linksprite.work-submission", name = "enabled", havingValue = "true")
public class WorkSubmissionController {

    @Autowired
    private WorkSubmissionService workSubmissionService;

    /**
     * 创作者提交作品信息（收单），返回记录 ID
     */
    @PostMapping
    public Result<Map<String, Object>> submit(@Valid @RequestBody WorkSubmissionRequest request) {
        Long id = workSubmissionService.submit(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("seriesId", request.getSeriesId());
        return Result.success(data);
    }
}
