package com.stonewu.fusion.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

/**
 * 用户第三方账号绑定实体
 */
@TableName("sys_user_third_party_binding")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserThirdPartyBinding extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 本地用户ID */
    private Long userId;

    /** 第三方提供者标识 */
    private String provider;

    /** 第三方用户ID */
    private Long thirdPartyUserId;

    /** 第三方用户名 */
    private String thirdPartyUsername;

    /** 第三方邮箱 */
    private String thirdPartyEmail;

    /** 第三方会话 session */
    private String sessionValue;

    /** NewAPI 个人 default 令牌 ID */
    private Long newapiTokenId;

    /** NewAPI 个人 default 令牌明文 */
    private String newapiTokenKey;
}
