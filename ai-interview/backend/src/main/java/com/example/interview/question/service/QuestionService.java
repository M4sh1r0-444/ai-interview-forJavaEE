package com.example.interview.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import com.example.interview.interview.service.PaperAssembler;
import com.example.interview.question.dto.QuestionAdminVO;
import com.example.interview.question.dto.QuestionUpsertRequest;
import com.example.interview.question.entity.Question;
import com.example.interview.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 题库服务：M1 承载管理端 CRUD（含乐观锁）；
 * M2 补 assemblePaper 组卷（计划 §6.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final PaperAssembler paperAssembler = new PaperAssembler();

    /** M2-2.2 组卷：取岗位全部 ENABLED 题目，交纯逻辑组卷器选题（计划 §6.3）。 */
    public List<Question> assemblePaper(Long positionId, int difficulty, int rounds) {
        List<Question> candidates = questionMapper.selectList(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getPositionId, positionId)
                        .eq(Question::getStatus, "ENABLED"));
        return paperAssembler.assemble(candidates, difficulty, rounds);
    }

    /** 用例 21：分页查询（positionId/type/difficulty 可选过滤）。 */
    public Page<QuestionAdminVO> adminPage(Long positionId, String type, Integer difficulty,
                                           int page, int size) {
        Page<Question> p = questionMapper.selectPage(
                Page.of(page, size),
                Wrappers.<Question>lambdaQuery()
                        .eq(positionId != null, Question::getPositionId, positionId)
                        .eq(type != null && !type.isBlank(), Question::getType, type)
                        .eq(difficulty != null, Question::getDifficulty, difficulty)
                        .orderByDesc(Question::getId));
        Page<QuestionAdminVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(QuestionAdminVO::from).toList());
        return result;
    }

    /** 用例 22：新增。 */
    public QuestionAdminVO create(QuestionUpsertRequest req) {
        Question q = new Question();
        copyFields(req, q);
        q.setStatus("ENABLED");
        questionMapper.insert(q);
        log.info("管理端新增题目 id={} positionId={} type={}", q.getId(), q.getPositionId(), q.getType());
        return QuestionAdminVO.from(questionMapper.selectById(q.getId()));
    }

    /** 用例 23：修改（version 乐观锁，冲突→40003）。 */
    public QuestionAdminVO update(Long id, QuestionUpsertRequest req) {
        if (req.getVersion() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "version: 修改时版本号必填");
        }
        Question exist = questionMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        Question q = new Question();
        q.setId(id);
        q.setVersion(req.getVersion());
        copyFields(req, q);
        int rows = questionMapper.updateById(q);
        if (rows == 0) {
            throw new BizException(ResultCode.VERSION_CONFLICT);
        }
        log.info("管理端修改题目 id={} version->{}", id, req.getVersion() + 1);
        return QuestionAdminVO.from(questionMapper.selectById(id));
    }

    /** 用例 24：逻辑删（status→DISABLED），保护已引用历史。 */
    public void delete(Long id) {
        Question exist = questionMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        Question q = new Question();
        q.setId(id);
        q.setStatus("DISABLED");
        questionMapper.updateById(q);
        log.info("管理端逻辑删除题目 id={}", id);
    }

    private void copyFields(QuestionUpsertRequest req, Question q) {
        q.setPositionId(req.getPositionId());
        q.setType(req.getType());
        q.setContent(req.getContent());
        q.setBackground(req.getBackground());
        q.setDifficulty(req.getDifficulty());
        q.setSkillTags(req.getSkillTags());
        q.setReferenceAnswer(req.getReferenceAnswer());
        q.setKeyPoints(req.getKeyPoints());
        q.setDeductionPoints(req.getDeductionPoints());
        q.setFollowupTemplates(req.getFollowupTemplates());
    }
}
