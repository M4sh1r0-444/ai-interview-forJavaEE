package com.example.interview.auth.service;

import com.example.interview.auth.dto.UserUpdateRequest;
import com.example.interview.auth.dto.UserVO;
import com.example.interview.auth.entity.SysUser;
import com.example.interview.auth.mapper.UserMapper;
import com.example.interview.common.exception.BizException;
import com.example.interview.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 个人资料查询/修改（计划 §4.3）。 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserVO getMe(Long uid) {
        return UserVO.from(requireUser(uid));
    }

    public UserVO updateMe(Long uid, UserUpdateRequest req) {
        SysUser user = requireUser(uid);
        user.setRealName(req.getRealName());
        user.setMajor(req.getMajor());
        userMapper.updateById(user);
        return UserVO.from(requireUser(uid));
    }

    private SysUser requireUser(Long uid) {
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND_OR_FORBIDDEN);
        }
        return user;
    }
}
