"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { AuthLayout } from "@/components/ui/auth-layout";
import * as authApi from "@/lib/api/auth";
import { getInitStatus } from "@/lib/api/system-init";
import { useAuthStore } from "@/lib/store/auth-store";

export default function RegisterPage() {
  const router = useRouter();
  const [initReady, setInitReady] = useState(false);
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [email, setEmail] = useState("");
  const [emailCode, setEmailCode] = useState("");
  const [thirdPartyEnabled, setThirdPartyEnabled] = useState(false);
  const [emailVerificationEnabled, setEmailVerificationEnabled] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  useEffect(() => {
    const loadThirdPartyStatus = async () => {
      try {
        const status = await authApi.getThirdPartyNewApiStatus();
        setThirdPartyEnabled(!!status.enabled);
        setEmailVerificationEnabled(!!status.emailVerificationEnabled);
      } catch {
        setThirdPartyEnabled(false);
        setEmailVerificationEnabled(false);
      }
    };

    getInitStatus()
      .then((status) => {
        if (!status.initialized) {
          router.replace("/setup");
        } else {
          setInitReady(true);
          void loadThirdPartyStatus();
        }
      })
      .catch(() => {
        setInitReady(true);
        void loadThirdPartyStatus();
      });
  }, [router]);

  useEffect(() => {
    if (countdown <= 0) {
      return;
    }
    const timer = window.setInterval(() => {
      setCountdown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  if (!initReady) {
    return <div className="min-h-screen bg-black" />;
  }

  const validate = (): string | null => {
    if (!username.trim()) return "请输入用户名";
    if (username.trim().length < 3) return "用户名至少 3 个字符";
    if (!password) return "请输入密码";
    if (password.length < 6) return "密码至少 6 位";
    if (password !== confirmPassword) return "两次输入的密码不一致";

    if (thirdPartyEnabled) {
      if (!email.trim()) return "第三方注册需要填写邮箱";
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailPattern.test(email.trim())) return "邮箱格式不正确";
      if (emailVerificationEnabled && !emailCode.trim()) return "请输入邮箱验证码";
    }

    return null;
  };

  const isFormValid =
    username.trim().length >= 3 &&
    password.length >= 6 &&
    password === confirmPassword &&
    (!thirdPartyEnabled || (email.trim().length > 0 && (!emailVerificationEnabled || emailCode.trim().length > 0)));

  const handleSendCode = async () => {
    const trimmedEmail = email.trim();
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!trimmedEmail) {
      setError("请先输入邮箱");
      return;
    }
    if (!emailPattern.test(trimmedEmail)) {
      setError("邮箱格式不正确");
      return;
    }

    setError("");
    setSendingCode(true);
    try {
      await authApi.sendThirdPartyVerification({ email: trimmedEmail, turnstile: "" });
      setCountdown(60);
    } catch (err) {
      setError(err instanceof Error ? err.message : "验证码发送失败，请稍后重试");
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setError("");
    setLoading(true);
    try {
      const resp = await authApi.register({
        username: username.trim(),
        password,
        nickname: nickname.trim() || undefined,
        email: thirdPartyEnabled ? email.trim() : undefined,
        emailCode: thirdPartyEnabled && emailVerificationEnabled ? emailCode.trim() : undefined,
        turnstile: "",
      });

      useAuthStore.setState({
        token: resp.accessToken,
        refreshToken: resp.refreshToken,
        user: {
          id: resp.userId,
          username: resp.username,
          nickname: resp.nickname,
          avatar: null,
          email: null,
          phone: null,
          status: 1,
          createTime: "",
          lastLoginTime: null,
          roles: [],
        },
      });

      document.cookie = `auth-token=${resp.accessToken}; path=/; max-age=${7 * 24 * 60 * 60}; SameSite=Lax`;

      try {
        await useAuthStore.getState().fetchUserInfo();
      } catch {
        // ignore
      }

      setShowSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      showSuccess={showSuccess}
      successTitle="注册成功"
      successSubtitle="正在进入控制面板"
      onTransitionComplete={() => router.replace("/dashboard")}
    >
      <div className="space-y-2">
        <h1 className="text-[2rem] font-bold leading-[1.1] tracking-tight text-white">
          创建账户
        </h1>
        <p className="text-base text-white/50 font-light">注册后即可开始创作</p>
        {thirdPartyEnabled && (
          <p className="text-xs text-emerald-300/80">当前注册将通过 NewAPI 第三方账号体系完成</p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-3">
        <input
          type="text"
          placeholder="用户名"
          value={username}
          onChange={(e) => {
            setUsername(e.target.value);
            setError("");
          }}
          className="w-full backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
          required
          autoComplete="username"
          minLength={3}
          disabled={loading}
        />

        <input
          type="text"
          placeholder="昵称（选填）"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          className="w-full backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
          autoComplete="nickname"
          disabled={loading}
        />

        {thirdPartyEnabled && (
          <input
            type="email"
            placeholder="邮箱"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setError("");
            }}
            className="w-full backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
            autoComplete="email"
            disabled={loading}
            required
          />
        )}

        {thirdPartyEnabled && emailVerificationEnabled && (
          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="邮箱验证码"
              value={emailCode}
              onChange={(e) => {
                setEmailCode(e.target.value);
                setError("");
              }}
              className="flex-1 backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
              disabled={loading}
              required
            />
            <button
              type="button"
              onClick={handleSendCode}
              disabled={loading || sendingCode || countdown > 0}
              className={cn(
                "shrink-0 rounded-full px-4 py-3 text-sm font-medium transition-colors",
                loading || sendingCode || countdown > 0
                  ? "bg-[#111] text-white/40 border border-white/10 cursor-not-allowed"
                  : "bg-white/10 text-white hover:bg-white/20 border border-white/20"
              )}
            >
              {sendingCode ? "发送中..." : countdown > 0 ? `${countdown}s` : "发送验证码"}
            </button>
          </div>
        )}

        <input
          type="password"
          placeholder="密码（至少6位）"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            setError("");
          }}
          className="w-full backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
          required
          autoComplete="new-password"
          minLength={6}
          disabled={loading}
        />

        <input
          type="password"
          placeholder="确认密码"
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            setError("");
          }}
          className="w-full backdrop-blur-[1px] text-white border border-white/10 rounded-full py-3 px-5 focus:outline-none focus:border-white/30 bg-transparent placeholder:text-white/30 transition-colors"
          required
          autoComplete="new-password"
          minLength={6}
          disabled={loading}
        />

        <AnimatePresence>
          {error && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.2 }}
              className="overflow-hidden"
            >
              <p className="text-red-400/90 text-sm py-1">{error}</p>
            </motion.div>
          )}
        </AnimatePresence>

        <motion.button
          type="submit"
          disabled={loading || !isFormValid}
          className={cn(
            "w-full rounded-full font-medium py-3 transition-all duration-300",
            loading || !isFormValid
              ? "bg-[#111] text-white/50 border border-white/10 cursor-not-allowed"
              : "bg-white text-black hover:bg-white/90 cursor-pointer"
          )}
          whileHover={!loading && isFormValid ? { scale: 1.02 } : undefined}
          whileTap={!loading && isFormValid ? { scale: 0.98 } : undefined}
          transition={{ duration: 0.2 }}
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                  fill="none"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                />
              </svg>
              注册中...
            </span>
          ) : (
            "注册并开始使用"
          )}
        </motion.button>

        <button
          type="button"
          onClick={() => router.push("/login")}
          className="w-full text-sm text-white/60 hover:text-white transition-colors pt-1"
          disabled={loading}
        >
          已有账号？去登录
        </button>
      </form>

      <p className="text-xs text-white/30 pt-4">融光 · AI视频创作平台</p>
    </AuthLayout>
  );
}
