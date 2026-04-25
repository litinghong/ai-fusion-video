import { http } from "./client";

// ============================================================
// 类型定义
// ============================================================

export interface StorageConfig {
  id: number;
  name: string;
  type: string;
  endpoint?: string;
  bucketName?: string;
  accessKey?: string;
  secretKey?: string;
  region?: string;
  basePath?: string;
  customDomain?: string;
  isDefault?: boolean;
  status: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface StorageConfigSaveReq {
  id?: number;
  name: string;
  type?: string;
  endpoint?: string;
  bucketName?: string;
  accessKey?: string;
  secretKey?: string;
  region?: string;
  basePath?: string;
  customDomain?: string;
  isDefault?: boolean;
  status?: number;
  remark?: string;
}

// ============================================================
// 存储类型选项
// ============================================================

export const STORAGE_TYPE_OPTIONS = [
  { value: "local", label: "本地存储", description: "文件保存在服务器本地磁盘" },
  { value: "s3", label: "S3 兼容存储", description: "阿里云 OSS / 腾讯 COS / AWS S3 / MinIO 等" },
] as const;

export const STORAGE_TYPE_LABELS: Record<string, string> = {
  local: "本地存储",
  s3: "S3 兼容",
};

// ============================================================
// API
// ============================================================

export const storageConfigApi = {
  async create(data: StorageConfigSaveReq): Promise<number> {
    return http.post("/storage/config/create", data);
  },

  async update(data: StorageConfigSaveReq): Promise<boolean> {
    return http.put("/storage/config/update", data);
  },

  async delete(id: number): Promise<boolean> {
    return http.delete("/storage/config/delete", { params: { id } });
  },

  async get(id: number): Promise<StorageConfig> {
    return http.get("/storage/config/get", { params: { id } });
  },

  async list(): Promise<StorageConfig[]> {
    return http.get("/storage/config/list");
  },

  async setDefault(id: number): Promise<boolean> {
    return http.put("/storage/config/set-default", null, { params: { id } });
  },

  async test(data: StorageConfigSaveReq): Promise<string> {
    return http.post("/storage/config/test", data);
  },
};
