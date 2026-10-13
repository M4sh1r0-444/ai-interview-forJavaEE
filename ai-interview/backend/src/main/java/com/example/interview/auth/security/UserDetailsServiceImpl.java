package com.example.interview.auth.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.auth.entity.SysUser;
import com.example.interview.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 按 username 装载用户与角色（ROLE_STUDENT / ROLE_ADMIN）。 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new AuthUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.getStatus() != null && user.getStatus() == 1);
    }
}
