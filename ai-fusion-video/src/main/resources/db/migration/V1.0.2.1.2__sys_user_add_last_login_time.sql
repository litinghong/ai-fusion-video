ALTER TABLE `sys_user`
    ADD COLUMN `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间' AFTER `status`;
