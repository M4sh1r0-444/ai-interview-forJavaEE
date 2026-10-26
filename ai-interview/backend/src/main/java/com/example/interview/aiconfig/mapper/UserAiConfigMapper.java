package com.example.interview.aiconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.aiconfig.entity.UserAiConfig;

/** user_ai_config（V9）：每人至多一条，按 user_id 唯一键 upsert。 */
public interface UserAiConfigMapper extends BaseMapper<UserAiConfig> {
}
