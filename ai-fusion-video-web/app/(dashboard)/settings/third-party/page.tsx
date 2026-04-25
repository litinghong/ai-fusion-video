"use client";

import { useEffect, useMemo, useState } from "react";
import { Link2, Loader2, Save } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { http } from "@/lib/api/client";
import { containerVariants, itemVariants } from "../_shared";
import { useAdminGuard } from "../_admin-guard";

const KEY_ENABLED = "third_party_newapi_enabled";
const KEY_BASE_URL = "third_party_newapi_base_url";
const KEY_EMAIL_VERIFY_ENABLED = "third_party_newapi_email_verification_enabled";
const KEY_MODEL_SYNC_ENABLED = "third_party_newapi_model_sync_enabled";
const KEY_SYSTEM_ACCESS_TOKEN = "third_party_newapi_system_access_token";
const DEFAULT_BASE_URL = "http://localhost:3001";

interface NewApiConfigs {
  enabled: boolean;
  baseUrl: string;
  emailVerificationEnabled: boolean;
  modelSyncEnabled: boolean;
  systemAccessToken: string;
}

function parseBoolean(value: string | undefined, defaultValue = false): boolean {
  if (!value) return defaultValue;
  const normalized = value.trim().toLowerCase();
  return normalized === "true" || normalized === "1" || normalized === "yes" || normalized === "on";
}

export default function ThirdPartySettingsPage() {
  const { checking, isAdmin } = useAdminGuard();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [configs, setConfigs] = useState<NewApiConfigs>({
    enabled: false,
    baseUrl: DEFAULT_BASE_URL,
    emailVerificationEnabled: false,
    modelSyncEnabled: true,
    systemAccessToken: "",
  });
  const [original, setOriginal] = useState<NewApiConfigs>({
    enabled: false,
    baseUrl: DEFAULT_BASE_URL,
    emailVerificationEnabled: false,
    modelSyncEnabled: true,
    systemAccessToken: "",
  });

  useEffect(() => {
    (async () => {
      try {
        const list = await http.get<never, { configKey: string; configValue: string }[]>("/api/system/config");
        const map: Record<string, string> = {};
        list.forEach((c) => {
          map[c.configKey] = c.configValue || "";
        });

        const loaded: NewApiConfigs = {
          enabled: parseBoolean(map[KEY_ENABLED]),
          baseUrl: (map[KEY_BASE_URL] || DEFAULT_BASE_URL).trim() || DEFAULT_BASE_URL,
          emailVerificationEnabled: parseBoolean(map[KEY_EMAIL_VERIFY_ENABLED]),
          modelSyncEnabled: parseBoolean(map[KEY_MODEL_SYNC_ENABLED], true),
          systemAccessToken: (map[KEY_SYSTEM_ACCESS_TOKEN] || "").trim(),
        };

        setConfigs(loaded);
        setOriginal(loaded);
      } catch (err) {
        console.error("加载第三方配置失败:", err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const hasChanges = useMemo(() => {
    return JSON.stringify(configs) !== JSON.stringify(original);
  }, [configs, original]);

  if (checking || !isAdmin) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const handleSave = async () => {
    setSaving(true);
    try {
      const nextConfigs: Record<string, string> = {
        [KEY_ENABLED]: String(configs.enabled),
        [KEY_BASE_URL]: configs.baseUrl.trim() || DEFAULT_BASE_URL,
        [KEY_EMAIL_VERIFY_ENABLED]: String(configs.emailVerificationEnabled),
        [KEY_MODEL_SYNC_ENABLED]: String(configs.modelSyncEnabled),
        [KEY_SYSTEM_ACCESS_TOKEN]: configs.systemAccessToken.trim(),
      };
      await http.put("/api/system/config", nextConfigs);
      setOriginal({
        ...configs,
        baseUrl: configs.baseUrl.trim() || DEFAULT_BASE_URL,
        systemAccessToken: configs.systemAccessToken.trim(),
      });
      if (!configs.baseUrl.trim()) {
        setConfigs((prev) => ({ ...prev, baseUrl: DEFAULT_BASE_URL }));
      }
    } catch (err) {
      console.error("保存第三方配置失败:", err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <motion.div className="max-w-[900px]" variants={containerVariants} initial="hidden" animate="visible">
      <motion.div variants={itemVariants} className="mb-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">第三方集成</h1>
            <p className="text-muted-foreground mt-1 text-sm">配置 NewAPI 账号体系接入策略</p>
          </div>
          <button
            onClick={handleSave}
            disabled={!hasChanges || saving}
            className={cn(
              "flex items-center gap-2 px-5 py-2 rounded-xl text-sm font-medium transition-all duration-200",
              hasChanges
                ? "bg-primary text-primary-foreground shadow-sm hover:opacity-90"
                : "bg-muted/50 text-muted-foreground cursor-not-allowed border border-border/30"
            )}
          >
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            {saving ? "保存中…" : "保存"}
          </button>
        </div>
      </motion.div>

      {loading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <motion.div
          variants={itemVariants}
          className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-6 space-y-5"
        >
          <div className="flex items-center gap-2 mb-1">
            <Link2 className="h-4 w-4 text-primary" />
            <h3 className="text-sm font-semibold">NewAPI 集成</h3>
          </div>

          <label className="flex items-center gap-3 text-sm">
            <input
              type="checkbox"
              checked={configs.enabled}
              onChange={(e) => setConfigs((prev) => ({ ...prev, enabled: e.target.checked }))}
              className="h-4 w-4 rounded border-border/50"
            />
            <span>开启第三方登录</span>
          </label>

          <div className="space-y-1.5">
            <label className="text-xs text-muted-foreground">NewAPI Base URL</label>
            <input
              type="url"
              value={configs.baseUrl}
              disabled={!configs.enabled}
              onChange={(e) => setConfigs((prev) => ({ ...prev, baseUrl: e.target.value }))}
              placeholder={DEFAULT_BASE_URL}
              className={cn(
                "w-full px-4 py-2.5 rounded-xl text-sm",
                "bg-muted/30 border border-border/30",
                "focus:outline-none focus:border-primary/50 focus:ring-1 focus:ring-primary/20",
                "placeholder:text-muted-foreground/40",
                !configs.enabled && "opacity-60 cursor-not-allowed"
              )}
            />
          </div>

          <label className={cn("flex items-center gap-3 text-sm", !configs.enabled && "opacity-60")}> 
            <input
              type="checkbox"
              checked={configs.emailVerificationEnabled}
              disabled={!configs.enabled}
              onChange={(e) =>
                setConfigs((prev) => ({ ...prev, emailVerificationEnabled: e.target.checked }))
              }
              className="h-4 w-4 rounded border-border/50"
            />
            <span>开启邮箱认证（用户注册时二次校验邮箱验证码）</span>
          </label>

          <label className={cn("flex items-center gap-3 text-sm", !configs.enabled && "opacity-60")}>
            <input
              type="checkbox"
              checked={configs.modelSyncEnabled}
              disabled={!configs.enabled}
              onChange={(e) =>
                setConfigs((prev) => ({ ...prev, modelSyncEnabled: e.target.checked }))
              }
              className="h-4 w-4 rounded border-border/50"
            />
            <span>开启模型同步</span>
          </label>

          <div className={cn("space-y-1.5", (!configs.enabled || !configs.modelSyncEnabled) && "opacity-60")}>
            <label className="text-xs text-muted-foreground">系统访问信令牌</label>
            <input
              type="password"
              value={configs.systemAccessToken}
              disabled={!configs.enabled || !configs.modelSyncEnabled}
              onChange={(e) => setConfigs((prev) => ({ ...prev, systemAccessToken: e.target.value }))}
              placeholder="请输入 NewAPI 系统访问令牌"
              className={cn(
                "w-full px-4 py-2.5 rounded-xl text-sm",
                "bg-muted/30 border border-border/30",
                "focus:outline-none focus:border-primary/50 focus:ring-1 focus:ring-primary/20",
                "placeholder:text-muted-foreground/40",
                (!configs.enabled || !configs.modelSyncEnabled) && "cursor-not-allowed"
              )}
            />
            <p className="text-[11px] text-muted-foreground">
              Token 可在「个人设置 - 安全设置 - 系统访问令牌」中生成。
            </p>
          </div>

          <div className="rounded-lg border border-border/20 bg-muted/10 p-3 text-xs text-muted-foreground leading-relaxed">
            普通用户启用后将走 NewAPI 注册/登录流程并同步本地账号；管理员账号始终优先使用本项目本地登录。
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}
