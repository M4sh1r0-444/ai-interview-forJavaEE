-- ============================================================
-- V6：学习资源种子（计划 §3.4-6 / §3.7）
-- 两岗位各 14 条（7 技能 × 2），类型覆盖 ARTICLE/QUESTION/PRACTICE/PROJECT；
-- ARTICLE 优先使用真实公开链接（官方文档/权威站点），
-- QUESTION/PRACTICE/PROJECT 为练习任务型，content_url 允许 NULL
-- ============================================================

INSERT INTO learning_resource (position_id, skill_code, title, resource_type, content_url, difficulty, enabled) VALUES

-- ========== 岗位 1：Java 后端（14 条） ==========
(1, 'java_basics',      'Java 官方教程：面向对象编程概念（封装/继承/多态）', 'ARTICLE',   'https://dev.java/learn/oop/', 2, 1),
(1, 'java_basics',      '集合框架高频面试题精练：HashMap/ArrayList/ConcurrentHashMap 20 问', 'QUESTION', NULL, 3, 1),
(1, 'concurrency_jvm',  'Java 官方教程：并发编程（线程、锁与线程池）', 'ARTICLE',   'https://dev.java/learn/concurrency/', 4, 1),
(1, 'concurrency_jvm',  '动手练习：手写一个支持拒绝策略与关闭语义的简易线程池', 'PRACTICE', NULL, 4, 1),
(1, 'spring',           'Spring Framework 官方参考：Core（IOC 容器与 AOP）', 'ARTICLE',   'https://docs.spring.io/spring-framework/reference/core.html', 3, 1),
(1, 'spring',           'Spring 面试题辨析：Bean 生命周期、作用域与事务传播行为', 'QUESTION', NULL, 3, 1),
(1, 'mysql',            'MySQL 8.0 官方参考手册：优化与索引', 'ARTICLE',   'https://dev.mysql.com/doc/refman/8.0/en/optimization.html', 3, 1),
(1, 'mysql',            '慢 SQL 排查实战：对 employees 示例库执行 EXPLAIN 并优化三条慢查询', 'PRACTICE', 'https://github.com/datacharmer/test_db', 3, 1),
(1, 'redis_mq',         'Redis 官方文档：数据类型概览与选型', 'ARTICLE',   'https://redis.io/docs/latest/develop/data-types/', 3, 1),
(1, 'redis_mq',         '缓存场景推演题：穿透/击穿/雪崩的识别与对策（互斥锁、逻辑过期、随机 TTL）', 'QUESTION', NULL, 4, 1),
(1, 'project_design',   '系统设计入门（System Design Primer）：从 QPS 估算到存储选型', 'ARTICLE',   'https://github.com/donnemartin/system-design-primer', 4, 1),
(1, 'project_design',   '项目实战：设计一个短链接服务并写出一页设计文档（容量估算+存储+跳转链路）', 'PROJECT',  NULL, 4, 1),
(1, 'communication',    '结构化表达：用金字塔原理组织面试回答（结论先行、分点展开）', 'ARTICLE',   NULL, 2, 1),
(1, 'communication',    '行为面试练习：按 STAR 法则准备 5 个项目故事并做互评', 'QUESTION', NULL, 2, 1),

-- ========== 岗位 2：Web 前端（14 条） ==========
(2, 'html_css_js',      'MDN：CSS 盒模型入门（标准盒模型与 IE 盒模型）', 'ARTICLE',   'https://developer.mozilla.org/zh-CN/docs/Web/CSS/CSS_box_model/Introduction_to_the_CSS_box_model', 2, 1),
(2, 'html_css_js',      'JavaScript 高频面试题：事件循环、闭包与 this 20 问', 'QUESTION', NULL, 3, 1),
(2, 'browser_network',  'MDN：HTTP 缓存（强缓存与协商缓存）', 'ARTICLE',   'https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Caching', 3, 1),
(2, 'browser_network',  'DevTools 实战：用 Network 面板分析一个真实页面的强缓存与协商缓存命中', 'PRACTICE', NULL, 3, 1),
(2, 'vue3',             'Vue 3 官方文档：深入响应式系统（Proxy 方案与依赖收集）', 'ARTICLE', 'https://cn.vuejs.org/guide/extras/reactivity-in-depth.html', 3, 1),
(2, 'vue3',             '动手练习：用 Pinia 重构一个多层组件树的状态管理并与 props 方案对比', 'PRACTICE', 'https://pinia.vuejs.org/zh/', 3, 1),
(2, 'engineering',      'Vite 官方文档：为什么选择 Vite（ESM 与打包构建的差异）', 'ARTICLE', 'https://cn.vitejs.dev/guide/why.html', 3, 1),
(2, 'engineering',      '项目实战：为课程项目搭建 GitHub Actions 自动构建与预览部署', 'PROJECT',  'https://docs.github.com/zh/actions', 3, 1),
(2, 'performance',      'web.dev：优化 Largest Contentful Paint（首屏性能）', 'ARTICLE', 'https://web.dev/articles/optimize-lcp', 3, 1),
(2, 'performance',      '动手练习：长列表虚拟滚动改造——从 1 万条数据卡顿到流畅滚动', 'PRACTICE', NULL, 4, 1),
(2, 'security',         'OWASP：跨站请求伪造（CSRF）原理与防护指南', 'ARTICLE',   'https://owasp.org/www-community/attacks/csrf', 3, 1),
(2, 'security',         'XSS 场景辨析题：给出 5 段前端代码判断风险并写出修复方案', 'QUESTION', NULL, 4, 1),
(2, 'project_ux',       'Vue 官方文档：规模化应用（组件设计与状态管理选型）', 'ARTICLE', 'https://cn.vuejs.org/guide/scaling-up/', 3, 1),
(2, 'project_ux',       '项目实战：复刻一个中后台 Dashboard，完成组件抽象与多机型适配方案', 'PROJECT', NULL, 4, 1);
