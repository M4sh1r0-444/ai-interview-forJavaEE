package com.example.interview.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.auth.entity.SysUser;
import com.example.interview.auth.mapper.UserMapper;
import com.example.interview.auth.security.CurrentUser;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理端用户管理（计划 §5.5 #31★）：用户列表（keyword 模糊 username/realName）
 * 与账号启停（status 0/1；禁用后登录 10003）。禁止管理员停用自己，防锁死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;

    public Page<UserVO> page(String keyword, int page, int size) {
        Page<SysUser> p = userMapper.selectPage(Page.of(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .and(keyword != null && !keyword.isBlank(), q -> q
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getRealName, keyword))
                        .orderByDesc(SysUser::getId));
        Page<UserVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(UserVO::from).toList());
        return result;
    }

    public UserVO updateStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "status 只能为 0（禁用）或 1（启用）");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN, "用户不存在");
        }
        if (status == 0 && id.equals(CurrentUser.currentUserId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "不能禁用当前登录的管理员账号");
        }
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setStatus(status);
        upd.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(upd);
        log.info("管理端调整用户状态 id={} status={}", id, status);
        return UserVO.from(userMapper.selectById(id));
    }
}
