package com.example.interview.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 6：知识片段（轻量 RAG 数据源）。keywords/skill_tags 为逗号分隔 VARCHAR
 * （JSON 列不能建 FULLTEXT 索引，计划 §3.1），与 content 同属 ngram 联合全文索引。
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long positionId;
    private String title;
    /** 300~800 字知识片段 */
    private String content;
    /** 逗号分隔关键词（8~15 个），参与全文检索 */
    private String keywords;
    /** 逗号分隔技能编码，与能力模型 skill_code 对齐 */
    private String skillTags;
    /** MANUAL / DOC_SLICE */
    private String sourceType;
    /** ENABLED / DISABLED */
    private String status;
    private LocalDateTime createdAt;
}
