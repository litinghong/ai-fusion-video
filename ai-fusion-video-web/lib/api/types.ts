// API 响应类型定义

// 后端统一响应格式
export interface CommonResult<T> {
  code: number;
  msg: string;
  data: T;
}

// 登录请求
export interface LoginReqVO {
  username: string;
  password: string;
  turnstile?: string;
}

// 注册请求
export interface RegisterReqVO {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  emailCode?: string;
  turnstile?: string;
}

// 登录响应
export interface LoginRespVO {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  username: string;
  nickname: string;
}

// 用户信息响应
export interface UserRespVO {
  id: number;
  username: string;
  nickname: string;
  avatar: string | null;
  email: string | null;
  phone: string | null;
  status: number;
  createTime: string;
  lastLoginTime: string | null;
  roles: string[];
}

// 个人资料更新请求
export interface ProfileUpdateReq {
  nickname?: string;
  email?: string;
  phone?: string;
}

// 修改密码请求
export interface ChangePasswordReq {
  oldPassword: string;
  newPassword: string;
}

export interface ThirdPartyNewApiStatusResp {
  enabled: boolean;
  emailVerificationEnabled: boolean;
  userModelConfigDisabled: boolean;
}

export interface ThirdPartyVerificationReqVO {
  email: string;
  turnstile?: string;
}
