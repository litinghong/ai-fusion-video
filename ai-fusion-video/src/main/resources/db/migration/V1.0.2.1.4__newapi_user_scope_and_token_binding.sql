SET @fv_admin_user_id := (
    SELECT ur.user_id
    FROM sys_user_role ur
    INNER JOIN sys_role r ON r.id = ur.role_id
    INNER JOIN sys_user u ON u.id = ur.user_id
    WHERE ur.deleted = 0
      AND r.deleted = 0
      AND u.deleted = 0
      AND r.code = 'admin'
    ORDER BY ur.user_id ASC
    LIMIT 1
);
SET @fv_admin_user_id := IFNULL(@fv_admin_user_id, 1);

ALTER TABLE `afv_api_config`
  ADD COLUMN `user_id` bigint NULL COMMENT '所属用户ID' AFTER `id`;

UPDATE `afv_api_config`
SET `user_id` = @fv_admin_user_id
WHERE `user_id` IS NULL;

ALTER TABLE `afv_api_config`
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '所属用户ID',
  ADD INDEX `idx_api_config_user_status`(`user_id` ASC, `status` ASC) USING BTREE,
  ADD INDEX `idx_api_config_user_platform_status`(`user_id` ASC, `platform` ASC, `status` ASC) USING BTREE;

ALTER TABLE `afv_ai_model`
  ADD COLUMN `user_id` bigint NULL COMMENT '所属用户ID' AFTER `id`;

UPDATE `afv_ai_model` m
LEFT JOIN `afv_api_config` c ON c.`id` = m.`api_config_id` AND c.`deleted` = 0
SET m.`user_id` = COALESCE(c.`user_id`, @fv_admin_user_id)
WHERE m.`user_id` IS NULL;

ALTER TABLE `afv_ai_model`
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '所属用户ID',
  DROP INDEX `uk_api_config_code`,
  ADD UNIQUE INDEX `uk_user_api_config_code`(`user_id` ASC, `api_config_id` ASC, `code` ASC, `deleted_id` ASC) USING BTREE,
  ADD INDEX `idx_ai_model_user_status`(`user_id` ASC, `status` ASC) USING BTREE,
  ADD INDEX `idx_ai_model_user_type_status`(`user_id` ASC, `model_type` ASC, `status` ASC) USING BTREE,
  ADD INDEX `idx_ai_model_user_default_type`(`user_id` ASC, `model_type` ASC, `default_model` ASC, `status` ASC) USING BTREE;

ALTER TABLE `sys_user_third_party_binding`
  ADD COLUMN `newapi_token_id` bigint DEFAULT NULL COMMENT 'NewAPI 个人 default 令牌ID' AFTER `session_value`,
  ADD COLUMN `newapi_token_key` varchar(512) DEFAULT NULL COMMENT 'NewAPI 个人 default 令牌明文' AFTER `newapi_token_id`;
