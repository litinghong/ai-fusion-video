import { http } from "./client";

export interface SystemUser {
  id: number;
  username: string;
  nickname: string | null;
  avatar: string | null;
  email: string | null;
  phone: string | null;
  status: number;
  createTime: string;
  lastLoginTime: string | null;
  roles: string[];
}

export interface UserPageResp {
  list: SystemUser[];
  total: number;
}

export interface UserPageParams {
  pageNo?: number;
  pageSize?: number;
  username?: string;
  nickname?: string;
  status?: number;
}

export interface UserSetRolesReq {
  userId: number;
  roleCodes: string[];
}

export interface UserResetPasswordResp {
  temporaryPassword: string;
}

export const userApi = {
  page: (params?: UserPageParams) => {
    const qs = new URLSearchParams();
    qs.set("pageNo", String(params?.pageNo ?? 1));
    qs.set("pageSize", String(params?.pageSize ?? 20));
    if (params?.username) qs.set("username", params.username);
    if (params?.nickname) qs.set("nickname", params.nickname);
    if (params?.status !== undefined) qs.set("status", String(params.status));
    return http.get<never, UserPageResp>(`/system/user/page?${qs.toString()}`);
  },

  getAssignableRoles: () => http.get<never, string[]>("/system/user/roles"),

  setRoles: (data: UserSetRolesReq) =>
    http.post<never, boolean>("/system/user/set-roles", data),

  lock: (id: number) => http.post<never, boolean>(`/system/user/lock?id=${id}`),

  unlock: (id: number) => http.post<never, boolean>(`/system/user/unlock?id=${id}`),

  delete: (id: number) =>
    http.delete<never, boolean>("/system/user/delete", { params: { id } }),

  resetPassword: (id: number) =>
    http.post<never, UserResetPasswordResp>(`/system/user/reset-password?id=${id}`),
};
