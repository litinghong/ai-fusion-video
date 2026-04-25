"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Loader2,
  Shield,
  User,
  Lock,
  Unlock,
  KeyRound,
  Trash2,
  Copy,
  Check,
  ArrowUp,
  ArrowDown,
} from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { userApi, type SystemUser } from "@/lib/api/user";
import { useAuthStore } from "@/lib/store/auth-store";
import { containerVariants, itemVariants } from "../_shared";
import { useAdminGuard } from "../_admin-guard";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogClose,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type SortField = "createTime" | "lastLoginTime";
type SortOrder = "asc" | "desc";
type StatusFilter = "" | "1" | "0";
type LoginStateFilter = "" | "never" | "active";

const DEFAULT_ROLE_OPTIONS = ["admin", "user"];

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return value;
  }
}

function toTimestamp(value: string | null | undefined): number | null {
  if (!value) return null;
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

export default function UserSettingsPage() {
  const { checking, isAdmin } = useAdminGuard();
  const currentUserId = useAuthStore((s) => s.user?.id ?? null);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const [users, setUsers] = useState<SystemUser[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize] = useState(20);

  const [usernameInput, setUsernameInput] = useState("");
  const [nicknameInput, setNicknameInput] = useState("");
  const [statusInput, setStatusInput] = useState<StatusFilter>("");
  const [search, setSearch] = useState<{
    username: string;
    nickname: string;
    status: StatusFilter;
  }>({
    username: "",
    nickname: "",
    status: "",
  });

  const [roleFilter, setRoleFilter] = useState("");
  const [loginStateFilter, setLoginStateFilter] = useState<LoginStateFilter>("");
  const [sortField, setSortField] = useState<SortField>("createTime");
  const [sortOrder, setSortOrder] = useState<SortOrder>("desc");

  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<SystemUser | null>(null);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [temporaryPassword, setTemporaryPassword] = useState("");
  const [copied, setCopied] = useState(false);
  const [assignableRoles, setAssignableRoles] = useState<string[]>(DEFAULT_ROLE_OPTIONS);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(total / pageSize)),
    [total, pageSize]
  );

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setErrorMessage("");
    try {
      const data = await userApi.page({
        pageNo,
        pageSize,
        username: search.username || undefined,
        nickname: search.nickname || undefined,
        status: search.status === "" ? undefined : Number(search.status),
      });
      setUsers(data.list || []);
      setTotal(data.total || 0);
    } catch (err) {
      console.error("加载用户列表失败:", err);
      setErrorMessage(err instanceof Error ? err.message : "加载用户列表失败");
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, search]);

  useEffect(() => {
    if (checking || !isAdmin) {
      return;
    }
    loadUsers();
  }, [checking, isAdmin, loadUsers]);

  useEffect(() => {
    if (checking || !isAdmin) {
      return;
    }
    let cancelled = false;
    userApi
      .getAssignableRoles()
      .then((roles) => {
        if (!cancelled && roles?.length) {
          setAssignableRoles(roles);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAssignableRoles(DEFAULT_ROLE_OPTIONS);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [checking, isAdmin]);

  const displayUsers = useMemo(() => {
    const filtered = users.filter((u) => {
      if (roleFilter && !(u.roles || []).includes(roleFilter)) {
        return false;
      }
      if (loginStateFilter === "never" && u.lastLoginTime) {
        return false;
      }
      if (loginStateFilter === "active" && !u.lastLoginTime) {
        return false;
      }
      return true;
    });

    const sorted = [...filtered].sort((a, b) => {
      const aTs = toTimestamp(a[sortField]);
      const bTs = toTimestamp(b[sortField]);

      if (aTs === null && bTs === null) return 0;
      if (aTs === null) return 1;
      if (bTs === null) return -1;

      return sortOrder === "asc" ? aTs - bTs : bTs - aTs;
    });

    return sorted;
  }, [users, roleFilter, loginStateFilter, sortField, sortOrder]);

  const toggleSort = (field: SortField) => {
    if (sortField !== field) {
      setSortField(field);
      setSortOrder("desc");
      return;
    }
    setSortOrder((prev) => (prev === "desc" ? "asc" : "desc"));
  };

  const handleSearch = () => {
    setSearch({
      username: usernameInput.trim(),
      nickname: nicknameInput.trim(),
      status: statusInput,
    });
    setPageNo(1);
  };

  const handleResetFilters = () => {
    setUsernameInput("");
    setNicknameInput("");
    setStatusInput("");
    setRoleFilter("");
    setLoginStateFilter("");
    setSortField("createTime");
    setSortOrder("desc");
    setSearch({ username: "", nickname: "", status: "" });
    setPageNo(1);
  };

  const openRoleDialog = (user: SystemUser) => {
    setSelectedUser(user);
    const nextRoles = (user.roles || []).filter((role) =>
      assignableRoles.includes(role)
    );
    setSelectedRoles(nextRoles.length ? nextRoles : ["user"]);
    setRoleDialogOpen(true);
  };

  const handleRoleSubmit = async () => {
    if (!selectedUser) return;
    setSubmitting(true);
    setErrorMessage("");
    try {
      await userApi.setRoles({
        userId: selectedUser.id,
        roleCodes: selectedRoles,
      });
      setRoleDialogOpen(false);
      await loadUsers();
    } catch (err) {
      console.error("更新角色失败:", err);
      setErrorMessage(err instanceof Error ? err.message : "更新角色失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleLock = async (user: SystemUser) => {
    const targetAction = user.status === 1 ? "锁定" : "解锁";
    if (!confirm(`确定要${targetAction}用户 ${user.username} 吗？`)) {
      return;
    }

    setSubmitting(true);
    setErrorMessage("");
    try {
      if (user.status === 1) {
        await userApi.lock(user.id);
      } else {
        await userApi.unlock(user.id);
      }
      await loadUsers();
    } catch (err) {
      console.error("更新用户状态失败:", err);
      setErrorMessage(err instanceof Error ? err.message : "更新用户状态失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResetPassword = async (user: SystemUser) => {
    if (!confirm(`确定要重置用户 ${user.username} 的密码吗？`)) {
      return;
    }

    setSelectedUser(user);
    setSubmitting(true);
    setErrorMessage("");
    try {
      const data = await userApi.resetPassword(user.id);
      setTemporaryPassword(data.temporaryPassword);
      setCopied(false);
      setPasswordDialogOpen(true);
      await loadUsers();
    } catch (err) {
      console.error("重置密码失败:", err);
      setErrorMessage(err instanceof Error ? err.message : "重置密码失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteUser = async (user: SystemUser) => {
    if (!confirm(`确定要删除用户 ${user.username} 吗？此操作不可恢复。`)) {
      return;
    }

    setSubmitting(true);
    setErrorMessage("");
    try {
      await userApi.delete(user.id);
      await loadUsers();
    } catch (err) {
      console.error("删除用户失败:", err);
      setErrorMessage(err instanceof Error ? err.message : "删除用户失败");
    } finally {
      setSubmitting(false);
    }
  };

  const copyPassword = async () => {
    if (!temporaryPassword) return;
    try {
      await navigator.clipboard.writeText(temporaryPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      // ignore clipboard errors
    }
  };

  if (checking || !isAdmin) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <motion.div
      className="max-w-[1200px]"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <motion.div variants={itemVariants} className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight">用户列表</h1>
        <p className="text-sm text-muted-foreground mt-1">
          管理员可变更角色、锁定/解锁账号、重置密码、删除用户
        </p>
      </motion.div>

      {errorMessage && (
        <motion.div
          variants={itemVariants}
          className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {errorMessage}
        </motion.div>
      )}

      <motion.div
        variants={itemVariants}
        className="rounded-xl border border-border/30 bg-card/50 p-4 mb-4"
      >
        <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
          <div className="space-y-1 md:col-span-2">
            <Label className="text-xs text-muted-foreground">用户名</Label>
            <Input
              value={usernameInput}
              onChange={(e) => setUsernameInput(e.target.value)}
              placeholder="按用户名搜索"
              className="h-9"
            />
          </div>
          <div className="space-y-1 md:col-span-2">
            <Label className="text-xs text-muted-foreground">昵称</Label>
            <Input
              value={nicknameInput}
              onChange={(e) => setNicknameInput(e.target.value)}
              placeholder="按昵称搜索"
              className="h-9"
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">状态</Label>
            <select
              value={statusInput}
              onChange={(e) => setStatusInput(e.target.value as StatusFilter)}
              className={cn(
                "h-9 w-full rounded-md border border-input bg-background px-3 text-sm",
                "focus:outline-none focus:ring-2 focus:ring-ring/50"
              )}
            >
              <option value="">全部</option>
              <option value="1">启用</option>
              <option value="0">锁定</option>
            </select>
          </div>
          <div className="flex items-end gap-2">
            <Button size="sm" onClick={handleSearch} disabled={loading}>
              {loading && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />}
              查询
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={handleResetFilters}
              disabled={loading}
            >
              重置
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mt-3">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">角色筛选（当前页）</Label>
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              className={cn(
                "h-9 w-full rounded-md border border-input bg-background px-3 text-sm",
                "focus:outline-none focus:ring-2 focus:ring-ring/50"
              )}
            >
              <option value="">全部角色</option>
              {assignableRoles.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">登录筛选（当前页）</Label>
            <select
              value={loginStateFilter}
              onChange={(e) =>
                setLoginStateFilter(e.target.value as LoginStateFilter)
              }
              className={cn(
                "h-9 w-full rounded-md border border-input bg-background px-3 text-sm",
                "focus:outline-none focus:ring-2 focus:ring-ring/50"
              )}
            >
              <option value="">全部</option>
              <option value="active">已登录过</option>
              <option value="never">从未登录</option>
            </select>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">时间排序</Label>
            <div className="flex gap-2">
              <Button
                type="button"
                size="sm"
                variant={sortField === "createTime" ? "default" : "outline"}
                onClick={() => toggleSort("createTime")}
              >
                注册时间
              </Button>
              <Button
                type="button"
                size="sm"
                variant={sortField === "lastLoginTime" ? "default" : "outline"}
                onClick={() => toggleSort("lastLoginTime")}
              >
                最后登录
              </Button>
            </div>
          </div>
          <div className="flex items-end text-xs text-muted-foreground">
            {sortOrder === "desc" ? (
              <span className="inline-flex items-center gap-1">
                <ArrowDown className="h-3 w-3" /> 倒序
              </span>
            ) : (
              <span className="inline-flex items-center gap-1">
                <ArrowUp className="h-3 w-3" /> 正序
              </span>
            )}
          </div>
        </div>
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="rounded-xl border border-border/30 bg-card/50 overflow-hidden"
      >
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-muted/30">
              <tr className="text-left">
                <th className="px-4 py-3 font-medium">用户名</th>
                <th className="px-4 py-3 font-medium">昵称</th>
                <th className="px-4 py-3 font-medium">邮箱</th>
                <th className="px-4 py-3 font-medium">角色</th>
                <th className="px-4 py-3 font-medium">
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 hover:text-foreground"
                    onClick={() => toggleSort("createTime")}
                  >
                    注册时间
                    {sortField === "createTime" &&
                      (sortOrder === "desc" ? (
                        <ArrowDown className="h-3 w-3" />
                      ) : (
                        <ArrowUp className="h-3 w-3" />
                      ))}
                  </button>
                </th>
                <th className="px-4 py-3 font-medium">
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 hover:text-foreground"
                    onClick={() => toggleSort("lastLoginTime")}
                  >
                    最后登录
                    {sortField === "lastLoginTime" &&
                      (sortOrder === "desc" ? (
                        <ArrowDown className="h-3 w-3" />
                      ) : (
                        <ArrowUp className="h-3 w-3" />
                      ))}
                  </button>
                </th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-muted-foreground">
                    <Loader2 className="h-5 w-5 animate-spin mx-auto mb-2" />
                    加载中...
                  </td>
                </tr>
              ) : displayUsers.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-muted-foreground">
                    暂无数据
                  </td>
                </tr>
              ) : (
                displayUsers.map((u) => {
                  const isSelf = currentUserId === u.id;
                  return (
                    <tr key={u.id} className="border-t border-border/20">
                      <td className="px-4 py-3 font-medium">{u.username}</td>
                      <td className="px-4 py-3">{u.nickname || "—"}</td>
                      <td className="px-4 py-3 text-muted-foreground">{u.email || "—"}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          {(u.roles || []).map((r) => (
                            <span
                              key={`${u.id}-${r}`}
                              className={cn(
                                "inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs",
                                r === "admin"
                                  ? "bg-blue-500/10 text-blue-500"
                                  : "bg-muted text-muted-foreground"
                              )}
                            >
                              {r === "admin" ? (
                                <Shield className="h-3 w-3" />
                              ) : (
                                <User className="h-3 w-3" />
                              )}
                              {r}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDate(u.createTime)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDate(u.lastLoginTime)}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={cn(
                            "inline-flex items-center px-2 py-0.5 rounded text-xs",
                            u.status === 1
                              ? "bg-green-500/10 text-green-500"
                              : "bg-amber-500/10 text-amber-500"
                          )}
                        >
                          {u.status === 1 ? "启用" : "锁定"}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => openRoleDialog(u)}
                            disabled={submitting}
                          >
                            角色
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleToggleLock(u)}
                            disabled={submitting || isSelf}
                            title={isSelf ? "不能锁定自己" : undefined}
                          >
                            {u.status === 1 ? (
                              <>
                                <Lock className="h-3.5 w-3.5 mr-1" /> 锁定
                              </>
                            ) : (
                              <>
                                <Unlock className="h-3.5 w-3.5 mr-1" /> 解锁
                              </>
                            )}
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleResetPassword(u)}
                            disabled={submitting}
                          >
                            <KeyRound className="h-3.5 w-3.5 mr-1" /> 重置密码
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => handleDeleteUser(u)}
                            disabled={submitting || isSelf}
                            title={isSelf ? "不能删除自己" : undefined}
                          >
                            <Trash2 className="h-3.5 w-3.5 mr-1" /> 删除用户
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="flex items-center justify-between mt-4 text-sm"
      >
        <span className="text-muted-foreground">
          当前页 {displayUsers.length} 条 / 总 {total} 条
        </span>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => setPageNo((p) => Math.max(1, p - 1))}
            disabled={pageNo <= 1 || loading}
          >
            上一页
          </Button>
          <span className="text-muted-foreground">
            第 {pageNo} / {totalPages} 页
          </span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setPageNo((p) => Math.min(totalPages, p + 1))}
            disabled={pageNo >= totalPages || loading}
          >
            下一页
          </Button>
        </div>
      </motion.div>

      <Dialog
        open={roleDialogOpen}
        onOpenChange={(open) => {
          setRoleDialogOpen(open);
          if (!open) {
            setSelectedUser(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>变更用户角色</DialogTitle>
            <DialogDescription>
              当前用户：{selectedUser?.username}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            {assignableRoles.map((role) => (
              <label key={role} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={selectedRoles.includes(role)}
                  onChange={(e) => {
                    setSelectedRoles((prev) =>
                      e.target.checked
                        ? Array.from(new Set([...prev, role]))
                        : prev.filter((r) => r !== role)
                    );
                  }}
                />
                {role}
              </label>
            ))}
          </div>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" size="sm" />}>
              取消
            </DialogClose>
            <Button
              size="sm"
              onClick={handleRoleSubmit}
              disabled={submitting || selectedRoles.length === 0}
            >
              {submitting && (
                <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />
              )}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={passwordDialogOpen}
        onOpenChange={(open) => {
          setPasswordDialogOpen(open);
          if (!open) {
            setTemporaryPassword("");
            setSelectedUser(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>重置密码成功</DialogTitle>
            <DialogDescription>
              用户 {selectedUser?.username || "-"} 的临时密码仅展示一次
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border border-border/30 bg-muted/20 p-3 font-mono text-sm break-all">
            {temporaryPassword || "—"}
          </div>
          <DialogFooter>
            <Button
              size="sm"
              variant="outline"
              onClick={copyPassword}
              disabled={!temporaryPassword}
            >
              {copied ? (
                <>
                  <Check className="h-3.5 w-3.5 mr-1" />
                  已复制
                </>
              ) : (
                <>
                  <Copy className="h-3.5 w-3.5 mr-1" />
                  复制密码
                </>
              )}
            </Button>
            <DialogClose render={<Button size="sm" />}>关闭</DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </motion.div>
  );
}
