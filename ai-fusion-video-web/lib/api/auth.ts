import { http } from "./client";
import type {
  LoginReqVO,
  RegisterReqVO,
  LoginRespVO,
  UserRespVO,
  ProfileUpdateReq,
  ChangePasswordReq,
  ThirdPartyNewApiStatusResp,
  ThirdPartyVerificationReqVO,
} from "./types";

// 认证相关 API

/**
 * 登录
 */
export function login(data: LoginReqVO): Promise<LoginRespVO> {
  return http.post<never, LoginRespVO>("/auth/login", data);
}

/**
 * 注册
 */
export function register(data: RegisterReqVO): Promise<LoginRespVO> {
  return http.post<never, LoginRespVO>("/auth/register", data);
}

/**
 * 获取当前用户信息
 */
export function getUserInfo(): Promise<UserRespVO> {
  return http.get<never, UserRespVO>("/auth/user-info");
}

/**
 * 登出
 */
export function logout(): Promise<boolean> {
  return http.post<never, boolean>("/auth/logout");
}

/**
 * 使用 refresh_token 刷新令牌
 */
export function refreshToken(refreshToken: string): Promise<LoginRespVO> {
  return http.post<never, LoginRespVO>("/auth/refresh", { refreshToken });
}

/**
 * 更新个人资料
 */
export function updateProfile(data: ProfileUpdateReq): Promise<boolean> {
  return http.put<never, boolean>("/auth/profile", data);
}

/**
 * 修改密码
 */
export function changePassword(data: ChangePasswordReq): Promise<boolean> {
  return http.put<never, boolean>("/auth/change-password", data);
}

/**
 * 获取 NewAPI 第三方登录状态
 */
export function getThirdPartyNewApiStatus(): Promise<ThirdPartyNewApiStatusResp> {
  return http.get<never, ThirdPartyNewApiStatusResp>("/auth/third-party/newapi/status");
}

/**
 * 发送 NewAPI 邮箱验证码
 */
export function sendThirdPartyVerification(data: ThirdPartyVerificationReqVO): Promise<boolean> {
  return http.post<never, boolean>("/auth/third-party/newapi/send-verification", data);
}
