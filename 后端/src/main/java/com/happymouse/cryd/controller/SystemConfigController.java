package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.SystemConfig;
import com.happymouse.cryd.repository.SystemConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器 — 大模型参数、智能体调度、资源生成阈值
 */
@RestController
@RequestMapping("/api/admin/config")
public class SystemConfigController {

    private final SystemConfigRepository configRepo;

    public SystemConfigController(SystemConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    // 获取所有配置
    @GetMapping
    public Result<List<SystemConfig>> getAll() {
        return Result.success(configRepo.findAll());
    }

    // 按分类获取
    @GetMapping("/category/{category}")
    public Result<List<SystemConfig>> getByCategory(@PathVariable String category) {
        return Result.success(configRepo.findByCategory(category));
    }

    // 获取单个配置
    @GetMapping("/{key}")
    public Result<SystemConfig> getByKey(@PathVariable("key") String key) {
        return Result.success(configRepo.findByConfigKey(key).orElse(null));
    }

    // 更新或创建配置
    @PutMapping
    public Result<SystemConfig> save(@RequestBody Map<String, Object> body) {
        String key = (String) body.get("configKey");
        String value = (String) body.get("configValue");
        String desc = (String) body.get("description");
        String category = (String) body.get("category");

        SystemConfig config = configRepo.findByConfigKey(key).orElse(new SystemConfig());
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(desc);
        config.setCategory(category);
        return Result.success(configRepo.save(config));
    }

    // 批量保存
    @PutMapping("/batch")
    public Result<?> saveBatch(@RequestBody List<Map<String, Object>> configs) {
        for (Map<String, Object> body : configs) {
            save(body);
        }
        return Result.success("ok");
    }
}
