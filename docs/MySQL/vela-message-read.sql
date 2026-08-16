-- ---------------------------------------------------------------------
-- 增量建表脚本：vela_message_read — 消息已读成员记录表
-- 用途：记录每条消息（单聊/群聊）被哪些成员已读，支撑"已读成员列表"功能
-- 依赖：MessageReadService.markRead 的并发幂等依赖唯一索引 uk_message_member
-- 说明：请执行本脚本后再部署包含已读成员列表的服务
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `vela_message_read`;

CREATE TABLE `vela_message_read` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id`      INT(11)      NOT NULL COMMENT '应用ID',
  `group_id`    VARCHAR(50)           DEFAULT NULL COMMENT '群组ID（单聊为空）',
  `message_key` BIGINT(20)   NOT NULL COMMENT '消息Key',
  `member_id`   VARCHAR(50)  NOT NULL COMMENT '已读成员ID',
  `read_time`   BIGINT(20)   NOT NULL COMMENT '已读时间戳（毫秒）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_member` (`message_key`, `member_id`),
  KEY `idx_app_group` (`app_id`, `group_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息已读成员记录表';
