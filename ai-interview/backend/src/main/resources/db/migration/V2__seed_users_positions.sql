-- ============================================================
-- V2：种子用户与岗位（计划 §3.4-2）
-- 账号：admin / Admin@123456（管理员）；demo01 / 123456（演示学生）
-- 密码 BCrypt(强度10) 散列由 spring-security-crypto 生成并本地验证通过
-- ============================================================

INSERT INTO sys_user (username, password_hash, real_name, role, major, status) VALUES
('admin',  '$2a$10$/XSQ/vxGFx86h6k19EHEMOT0mwlCoFxcQv5uhy92x5l8YisB4D0qu', '系统管理员', 'ADMIN',   '计算机科学与技术', 1),
('demo01', '$2a$10$rrPxlpyqs1Fm.pnoRrvA8.Y7VsbfzyPHiM2b9B4TwDgofuy8GXxC2', '演示学生',   'STUDENT', '软件工程', 1);

-- ========== 岗位 1：Java 后端（技能权重之和 = 1） ==========
INSERT INTO `position` (code, name, description, competency_model_json, default_rounds, enabled) VALUES
('JAVA_BACKEND', 'Java 后端开发工程师',
 '面向 Java 后端岗位的模拟面试，覆盖 Java 语言基础、并发与 JVM、Spring 生态、MySQL、Redis 与消息队列、项目设计等考察维度。',
 '{"skills":[{"skillCode":"java_basics","skillName":"Java基础与面向对象","description":"集合、异常、面向对象设计","weight":0.15,"requiredLevel":70},{"skillCode":"concurrency_jvm","skillName":"并发编程与JVM","description":"线程池、锁、JVM内存模型与调优","weight":0.15,"requiredLevel":70},{"skillCode":"spring","skillName":"Spring生态","description":"IOC/AOP、事务、Spring Boot自动装配","weight":0.15,"requiredLevel":70},{"skillCode":"mysql","skillName":"MySQL数据库","description":"索引、事务、SQL优化","weight":0.15,"requiredLevel":70},{"skillCode":"redis_mq","skillName":"Redis与消息队列","description":"缓存三兄弟、持久化、MQ可靠投递","weight":0.15,"requiredLevel":70},{"skillCode":"project_design","skillName":"项目与系统设计","description":"复杂度应对、架构权衡、排查思路","weight":0.15,"requiredLevel":70},{"skillCode":"communication","skillName":"沟通表达","description":"结构化表达、协作与学习方法","weight":0.10,"requiredLevel":65}],"scoreWeights":{"content":{"technical":0.30,"depth":0.25,"logic":0.20,"evidence":0.15,"match":0.10},"expression":{"clarity":0.30,"fluency":0.20,"pace":0.20,"confidence":0.15,"completeness":0.15},"total":{"content":0.50,"expression":0.30,"adaptability":0.10,"match":0.10}}}',
 6, 1);

-- ========== 岗位 2：Web 前端（技能权重之和 = 1） ==========
INSERT INTO `position` (code, name, description, competency_model_json, default_rounds, enabled) VALUES
('WEB_FRONTEND', 'Web 前端开发工程师',
 '面向 Web 前端岗位的模拟面试，覆盖 HTML/CSS/JS 基础、浏览器与网络、Vue3 框架、工程化、性能优化、安全等考察维度。',
 '{"skills":[{"skillCode":"html_css_js","skillName":"HTML/CSS/JS基础","description":"盒模型、事件循环、闭包、ES6+","weight":0.15,"requiredLevel":70},{"skillCode":"browser_network","skillName":"浏览器与网络","description":"渲染流程、同源策略、HTTP缓存","weight":0.15,"requiredLevel":70},{"skillCode":"vue3","skillName":"Vue3框架","description":"响应式原理、组件通信、Pinia状态管理","weight":0.15,"requiredLevel":70},{"skillCode":"engineering","skillName":"前端工程化","description":"Vite/Webpack、环境管理、构建发布","weight":0.15,"requiredLevel":70},{"skillCode":"performance","skillName":"性能优化","description":"首屏、长列表、包体积优化","weight":0.15,"requiredLevel":70},{"skillCode":"security","skillName":"前端安全","description":"XSS/CSRF 原理与防御","weight":0.10,"requiredLevel":65},{"skillCode":"project_ux","skillName":"项目与交互实现","description":"组件设计、联调协作、适配方案","weight":0.15,"requiredLevel":70}],"scoreWeights":{"content":{"technical":0.30,"depth":0.25,"logic":0.20,"evidence":0.15,"match":0.10},"expression":{"clarity":0.30,"fluency":0.20,"pace":0.20,"confidence":0.15,"completeness":0.15},"total":{"content":0.50,"expression":0.30,"adaptability":0.10,"match":0.10}}}',
 6, 1);
