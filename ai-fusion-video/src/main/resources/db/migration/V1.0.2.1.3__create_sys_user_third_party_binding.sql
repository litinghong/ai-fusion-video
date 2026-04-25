CREATE TABLE IF NOT EXISTS `sys_user_third_party_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '本地用户ID',
  `provider` varchar(32) NOT NULL COMMENT '第三方提供者标识，如 newapi',
  `third_party_user_id` bigint DEFAULT NULL COMMENT '第三方用户ID',
  `third_party_username` varchar(64) DEFAULT NULL COMMENT '第三方用户名',
  `third_party_email` varchar(128) DEFAULT NULL COMMENT '第三方邮箱',
  `session_value` varchar(1024) DEFAULT NULL COMMENT '第三方会话 session 值',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_provider` (`user_id`, `provider`),
  UNIQUE KEY `uk_provider_third_user` (`provider`, `third_party_user_id`),
  KEY `idx_provider_username` (`provider`, `third_party_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方账号绑定表';
