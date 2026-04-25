"use client";

import { useState, useEffect } from "react";
import { Globe, Save, Loader2, AlertTriangle } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { http } from "@/lib/api/client";
import { containerVariants, itemVariants } from "../_shared";
import { useAdminGuard } from "../_admin-guard";

interface SystemConfigs {
  site_base_url: string;
}

export default function GeneralSettingsPage() {
  const { checking, isAdmin } = useAdminGuard();
  const [configs, setConfigs] = useState<SystemConfigs>({ site_base_url: "" });
  const [original, setOriginal] = useState<SystemConfigs>({ site_base_url: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const list = await http.get<never, { configKey: string; configValue: string }[]>(
          "/api/system/config"
        );
        const map: Record<string, string> = {};
        list.forEach((c) => { map[c.configKey] = c.configValue || ""; });
        const loaded = { site_base_url: map.site_base_url || "" };
        setConfigs(loaded);
        setOriginal(loaded);
      } catch (err) {
        console.error("加载系统配置失败:", err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const hasChanges = configs.site_base_url !== original.site_base_url;

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
      await http.put("/api/system/config", configs);
      setOriginal({ ...configs });
    } catch (err) {
      console.error("保存系统配置失败:", err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <motion.div
      className="max-w-[800px]"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      {/* 标题 */}
      <motion.div variants={itemVariants} className="mb-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">通用设置</h1>
            <p className="text-muted-foreground mt-1 text-sm">
              配置站点访问域名等全局参数
            </p>
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
            {saving ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Save className="h-4 w-4" />
            )}
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
          className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-6"
        >
          <div className="flex items-center gap-2 mb-4">
            <Globe className="h-4 w-4 text-primary" />
            <h3 className="text-sm font-semibold">项目访问域名</h3>
          </div>

          <p className="text-xs text-muted-foreground mb-4 leading-relaxed">
            配置本项目部署后的完整访问域名（不含末尾斜杠）。
            系统将使用该域名拼接内部资源的公网访问地址，供外部服务和 API 调用。
          </p>

          <input
            type="url"
            value={configs.site_base_url}
            onChange={(e) =>
              setConfigs((prev) => ({ ...prev, site_base_url: e.target.value }))
            }
            placeholder="https://fusion.example.com"
            className={cn(
              "w-full px-4 py-2.5 rounded-xl text-sm",
              "bg-muted/30 border border-border/30",
              "focus:outline-none focus:border-primary/50 focus:ring-1 focus:ring-primary/20",
              "placeholder:text-muted-foreground/40"
            )}
          />

          <div className="mt-4 space-y-3">
            <div className="flex items-start gap-2 p-3 rounded-lg bg-muted/10 border border-border/20">
              <div className="text-xs text-muted-foreground leading-relaxed">
                <p className="mb-1">
                  <strong>示例：</strong>
                </p>
                <ul className="list-disc list-inside space-y-0.5">
                  <li>本地开发：<code className="text-foreground/80">http://localhost:8080</code></li>
                  <li>内网部署：<code className="text-foreground/80">http://192.168.1.100:8080</code></li>
                  <li>公网部署：<code className="text-foreground/80">https://fusion.example.com</code></li>
                </ul>
              </div>
            </div>

            <div className="flex items-start gap-2 p-3 rounded-lg bg-amber-500/5 border border-amber-500/20">
              <AlertTriangle className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
              <p className="text-xs text-muted-foreground leading-relaxed">
                <strong>备注：</strong>画风参考图生图功能依赖此配置或对象存储。
                使用本地存储时，需要配置此域名才能将参考图传递给 AI API；
                若已配置对象存储，上传的图片会自动获得公网 URL，此项可不填。
              </p>
            </div>
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}
