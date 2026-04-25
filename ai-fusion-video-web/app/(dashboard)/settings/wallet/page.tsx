"use client";

import type { ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import {
  BarChart3,
  Copy,
  CreditCard,
  Gift,
  Link2,
  Loader2,
  ReceiptText,
  Search,
  Users,
  Wallet,
  Zap,
} from "lucide-react";
import { motion } from "framer-motion";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { walletApi, type AlipayReferenceAmount, type TopupBill, type WalletStatsResp } from "@/lib/api/wallet";
import { containerVariants, itemVariants } from "../_shared";

const DEFAULT_QUOTA_PER_UNIT = 500000;
const DEFAULT_QUOTA_DISPLAY_TYPE = "USD";
const BILL_PAGE_SIZE = 10;

function SmokeLayer({ tone }: { tone: "blue" | "teal" }) {
  const palette =
    tone === "blue"
      ? {
          base: "from-[#0f5ff3] via-[#2256da] to-[#1d4fc6]",
          glow: "bg-cyan-300/18",
          rose: "bg-fuchsia-300/18",
          amber: "bg-amber-200/12",
        }
      : {
          base: "from-[#063d45] via-[#0a4f4d] to-[#0b2f35]",
          glow: "bg-emerald-300/18",
          rose: "bg-amber-300/12",
          amber: "bg-sky-300/12",
        };

  return (
    <div className={cn("absolute inset-0 overflow-hidden bg-linear-to-br", palette.base)}>
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_25%_8%,rgba(255,255,255,0.22),transparent_24%),radial-gradient(circle_at_76%_6%,rgba(255,255,255,0.12),transparent_18%)]" />
      <div className={cn("absolute -left-8 top-7 h-32 w-64 rounded-full blur-2xl", palette.glow)} />
      <div className={cn("absolute left-52 -top-8 h-52 w-64 rounded-full blur-3xl", palette.rose)} />
      <div className={cn("absolute right-24 top-10 h-40 w-72 rounded-full blur-3xl", palette.amber)} />
      <div className="absolute inset-0 opacity-45 [background:linear-gradient(118deg,transparent_0%,rgba(255,255,255,0.14)_19%,transparent_36%),linear-gradient(68deg,transparent_6%,rgba(255,255,255,0.08)_25%,transparent_45%),radial-gradient(ellipse_at_53%_56%,rgba(255,255,255,0.12),transparent_42%)]" />
      <div className="absolute inset-0 opacity-40 [background-image:radial-gradient(circle,rgba(255,255,255,0.55)_1px,transparent_1px)] [background-size:72px_50px]" />
    </div>
  );
}

function StatItem({
  value,
  label,
  icon,
}: {
  value: string;
  label: string;
  icon: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center text-white">
      <div className="text-[24px] font-bold leading-none tracking-[-0.02em]">{value}</div>
      <div className="mt-3 flex items-center gap-1.5 text-[12px] text-white/75">
        {icon}
        <span>{label}</span>
      </div>
    </div>
  );
}

function AlipayMark() {
  return (
    <span className="inline-flex h-[18px] w-[18px] items-center justify-center rounded-[4px] bg-[#2576ff] text-[13px] font-bold leading-none text-white">
      支
    </span>
  );
}

function formatAmountText(amount: string | null | undefined) {
  if (!amount) return "--";
  const value = Number(amount);
  if (!Number.isFinite(value)) return amount;
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

function getStoredQuotaPerUnit() {
  if (typeof window === "undefined") return DEFAULT_QUOTA_PER_UNIT;
  const stored = Number(window.localStorage.getItem("quota_per_unit"));
  return Number.isFinite(stored) && stored > 0 ? stored : DEFAULT_QUOTA_PER_UNIT;
}

function getStoredQuotaDisplayType() {
  if (typeof window === "undefined") return DEFAULT_QUOTA_DISPLAY_TYPE;
  return window.localStorage.getItem("quota_display_type") || DEFAULT_QUOTA_DISPLAY_TYPE;
}

function persistQuotaDisplayConfig(stats: WalletStatsResp) {
  if (typeof window === "undefined") return;
  const quotaPerUnit = Number(stats.quotaPerUnit);
  window.localStorage.setItem(
    "quota_per_unit",
    String(Number.isFinite(quotaPerUnit) && quotaPerUnit > 0 ? quotaPerUnit : DEFAULT_QUOTA_PER_UNIT)
  );
  window.localStorage.setItem("quota_display_type", stats.quotaDisplayType || DEFAULT_QUOTA_DISPLAY_TYPE);
}

function renderQuota(quota: number | null | undefined) {
  if (quota === null || quota === undefined) return "--";
  const numericQuota = Number(quota);
  if (!Number.isFinite(numericQuota)) return "--";

  const displayType = getStoredQuotaDisplayType().toLowerCase();
  if (displayType === "quota") {
    return String(Math.round(numericQuota));
  }

  const amount = numericQuota / getStoredQuotaPerUnit();
  const symbol = displayType === "cny" || displayType === "rmb" ? "¥" : "$";
  return `${symbol}${amount.toFixed(2)}`;
}

function renderRequestCount(count: number | null | undefined) {
  if (count === null || count === undefined) return "--";
  const value = Number(count);
  return Number.isFinite(value) ? String(Math.round(value)) : "--";
}

function WalletOverviewCard({
  onOpenBill,
  stats,
  rechargeOptions,
  amountsLoading,
  amountsError,
  onRecharge,
  redeemCode,
  redeeming,
  topupEnabled,
  onRedeemCodeChange,
  onRedeem,
}: {
  onOpenBill: () => void;
  stats: WalletStatsResp | null;
  rechargeOptions: AlipayReferenceAmount[];
  amountsLoading: boolean;
  amountsError: string | null;
  onRecharge: (option: AlipayReferenceAmount) => void;
  redeemCode: string;
  redeeming: boolean;
  topupEnabled: boolean | null;
  onRedeemCodeChange: (value: string) => void;
  onRedeem: () => void;
}) {
  return (
    <motion.section
      variants={itemVariants}
      className="min-h-[638px] overflow-hidden rounded-[16px] border border-[#e9e9ee] bg-white shadow-[0_2px_8px_rgba(17,24,39,0.03)]"
    >
      <div className="flex h-[62px] items-center justify-between px-[10px]">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#5daaff] text-white shadow-[0_6px_16px_rgba(93,170,255,0.36)]">
            <CreditCard className="h-[17px] w-[17px]" />
          </div>
          <div>
            <h2 className="text-[14px] font-semibold leading-5 text-[#2d333b]">账户充值</h2>
            <p className="text-[12px] leading-4 text-[#8a8f98]">多种充值方式，安全便捷</p>
          </div>
        </div>
        <button
          type="button"
          onClick={onOpenBill}
          className="mr-0 inline-flex h-8 items-center gap-2 rounded-[10px] bg-[#2864f6] px-4 text-[14px] font-semibold text-white shadow-[0_8px_18px_rgba(40,100,246,0.22)] transition hover:bg-[#1f59e9]"
        >
          <ReceiptText className="h-4 w-4" />
          账单
        </button>
      </div>

      <div className="mx-[10px] h-[129px] overflow-hidden rounded-t-[9px] relative">
        <SmokeLayer tone="blue" />
        <div className="relative z-10 px-4 pt-[17px] text-white">
          <h3 className="text-[17px] font-semibold">账户统计</h3>
          <div className="mt-[21px] grid grid-cols-3">
            <StatItem value={renderQuota(stats?.quota)} label="当前余额" icon={<Wallet className="h-3.5 w-3.5" />} />
            <StatItem value={renderQuota(stats?.usedQuota)} label="历史消耗" icon={<Zap className="h-3.5 w-3.5" />} />
            <StatItem value={renderRequestCount(stats?.requestCount)} label="请求次数" icon={<BarChart3 className="h-3.5 w-3.5" />} />
          </div>
        </div>
      </div>

      <div className="px-[10px] pb-[10px]">
        <div className="rounded-b-[10px] border border-t-0 border-[#ececf0] px-[10px] pb-5 pt-[20px]">
          <div className="mb-[10px] flex items-center gap-2">
            <AlipayMark />
            <h3 className="text-[15px] font-semibold text-[#24292f]">支付宝充值</h3>
          </div>

          {amountsLoading ? (
            <div className="flex h-[114px] items-center justify-center rounded-[14px] border border-[#ebebef] bg-[#fafafa] text-[14px] text-[#7a8088]">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              加载充值金额
            </div>
          ) : amountsError ? (
            <div className="flex h-[114px] items-center justify-center rounded-[14px] border border-[#f0d4d4] bg-[#fff8f8] px-4 text-center text-[14px] text-[#b42318]">
              {amountsError}
            </div>
          ) : rechargeOptions.length === 0 ? (
            <div className="flex h-[114px] items-center justify-center rounded-[14px] border border-[#ebebef] bg-[#fafafa] text-[14px] text-[#7a8088]">
              暂无可用充值金额
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {rechargeOptions.map((option) => {
                const amountText = formatAmountText(option.amount);
                return (
                  <button
                    key={`${option.productId || "amount"}-${option.amount}`}
                    type="button"
                    onClick={() => onRecharge(option)}
                    className="h-[114px] rounded-[14px] border border-[#ebebef] bg-white text-center transition hover:border-[#2b67f6]/45 hover:shadow-[0_10px_24px_rgba(37,99,235,0.10)]"
                  >
                    <div className="text-[18px] font-semibold leading-6 text-[#54595f]">{amountText}</div>
                    <div className="mt-[9px] text-[14px] text-[#5f666e]">
                      充值额度: {option.quota ?? amountText}
                    </div>
                    <div className="mt-[11px] text-[18px] font-bold text-[#53585e]">¥{amountText}</div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="mt-[10px] overflow-hidden rounded-[12px] border border-[#ececf0] bg-white">
          <div className="h-[40px] border-b border-[#ececf0] px-[10px] text-[14px] font-medium leading-[40px] text-[#787d84]">
            兑换码充值
          </div>
          <div className="p-[10px]">
            <div className="flex h-8 items-center rounded-[9px] bg-[#f5f5f6]">
              <Gift className="ml-3 h-4 w-4 shrink-0 text-[#7f8389]" />
              <input
                value={redeemCode}
                disabled={redeeming || topupEnabled !== true}
                onChange={(e) => onRedeemCodeChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    onRedeem();
                  }
                }}
                className="min-w-0 flex-1 bg-transparent px-3 text-[13px] text-[#2d333b] outline-none placeholder:text-[#8b9097] disabled:cursor-not-allowed disabled:opacity-70"
                placeholder={
                  topupEnabled === null ? "加载兑换码状态" : topupEnabled ? "请输入兑换码" : "兑换码充值未开启"
                }
              />
              <button
                type="button"
                disabled={redeeming || topupEnabled !== true || !redeemCode.trim()}
                onClick={onRedeem}
                className="mr-0 inline-flex h-8 items-center rounded-[10px] bg-[#2864f6] px-[13px] text-[14px] font-semibold text-white transition hover:bg-[#1f59e9] disabled:cursor-not-allowed disabled:opacity-70"
              >
                {redeeming && <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />}
                兑换额度
              </button>
            </div>
          </div>
        </div>
      </div>
    </motion.section>
  );
}

function InviteRewardCard({ stats }: { stats: WalletStatsResp | null }) {
  const inviteUrl = stats?.inviteUrl;
  const displayInviteUrl = inviteUrl || "--";

  const handleCopyInviteUrl = async () => {
    if (!inviteUrl) {
      toast.error("暂无可复制的邀请链接");
      return;
    }

    try {
      await navigator.clipboard.writeText(inviteUrl);
      toast.success("邀请链接已复制到剪切板");
    } catch {
      toast.error("复制邀请链接失败");
    }
  };

  return (
    <motion.section
      variants={itemVariants}
      className="min-h-[638px] overflow-hidden rounded-[16px] border border-[#e9e9ee] bg-white shadow-[0_2px_8px_rgba(17,24,39,0.03)]"
    >
      <div className="flex h-[62px] items-center px-[10px]">
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#63c767] text-white shadow-[0_6px_16px_rgba(99,199,103,0.32)]">
          <Gift className="h-[17px] w-[17px]" />
        </div>
        <div className="ml-3">
          <h2 className="text-[14px] font-semibold leading-5 text-[#2d333b]">邀请奖励</h2>
          <p className="text-[12px] leading-4 text-[#8a8f98]">邀请好友获得额外奖励</p>
        </div>
      </div>

      <div className="mx-[10px] h-[132px] overflow-hidden rounded-t-[9px] relative">
        <SmokeLayer tone="teal" />
        <div className="relative z-10 px-4 pt-[18px] text-white">
          <div className="flex items-start justify-between">
            <h3 className="text-[17px] font-semibold">收益统计</h3>
            <button
              type="button"
              disabled
              className="inline-flex h-6 items-center gap-1.5 rounded-[8px] bg-white/88 px-3 text-[12px] font-medium text-[#9aa0a6]"
            >
              <Zap className="h-3 w-3" />
              划转到余额
            </button>
          </div>
          <div className="mt-[21px] grid grid-cols-3">
            <StatItem value={renderQuota(stats?.affQuota)} label="待使用收益" icon={<Zap className="h-3.5 w-3.5" />} />
            <StatItem value={renderQuota(stats?.affHistoryQuota)} label="总收益" icon={<BarChart3 className="h-3.5 w-3.5" />} />
            <StatItem value={renderRequestCount(stats?.affCount)} label="邀请人数" icon={<Users className="h-3.5 w-3.5" />} />
          </div>
        </div>
      </div>

      <div className="mx-[10px] h-[43px] rounded-b-[10px] border border-t-0 border-[#ececf0] px-[10px] py-[10px]">
        <div className="flex h-8 -translate-y-[1px] items-center rounded-[9px] bg-[#f5f5f6]">
          <span className="px-3 text-[13px] text-[#7f8389]">邀请链接</span>
          <span className="min-w-0 flex-1 truncate text-[14px] text-[#24292f]">{displayInviteUrl}</span>
          <button
            type="button"
            disabled={!inviteUrl}
            onClick={handleCopyInviteUrl}
            className="mr-0 inline-flex h-8 items-center gap-2 rounded-[10px] bg-[#2864f6] px-[14px] text-[14px] font-semibold text-white transition hover:bg-[#1f59e9] disabled:cursor-not-allowed disabled:opacity-70"
          >
            <Copy className="h-4 w-4" />
            复制
          </button>
        </div>
      </div>

      <div className="mx-[10px] mt-[10px] overflow-hidden rounded-[11px] border border-[#ececf0]">
        <div className="h-[36px] border-b border-[#ececf0] px-[10px] text-[13px] leading-[36px] text-[#7c8188]">
          奖励说明
        </div>
        <ul className="space-y-[13px] px-[12px] py-[17px] text-[14px] text-[#7b8087]">
          <li className="flex items-center gap-3">
            <span className="h-1.5 w-1.5 rounded-full bg-[#45b456]" />
            邀请好友注册，好友充值后您可获得相应奖励
          </li>
          <li className="flex items-center gap-3">
            <span className="h-1.5 w-1.5 rounded-full bg-[#45b456]" />
            通过划转功能将奖励额度转入到您的账户余额中
          </li>
          <li className="flex items-center gap-3">
            <span className="h-1.5 w-1.5 rounded-full bg-[#45b456]" />
            邀请的好友越多，获得的奖励越多
          </li>
        </ul>
      </div>
    </motion.section>
  );
}

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  alipay: "支付宝",
  wxpay: "微信",
  stripe: "Stripe",
  creem: "Creem",
  waffo: "Waffo",
};

const STATUS_LABELS: Record<string, string> = {
  success: "成功",
  pending: "待支付",
  failed: "失败",
  expired: "已过期",
};

function formatBillMoney(money: number | null | undefined) {
  if (money === null || money === undefined) return "--";
  const value = Number(money);
  return Number.isFinite(value) ? `¥${value.toFixed(2)}` : "--";
}

function formatBillTime(timestamp: number | null | undefined) {
  if (!timestamp) return "--";
  const date = new Date(timestamp * 1000);
  if (Number.isNaN(date.getTime())) return "--";
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function StatusBadge({ status }: { status: string | null | undefined }) {
  const normalized = status || "";
  const success = normalized === "success";
  const danger = normalized === "failed" || normalized === "expired";

  return (
    <div className="flex items-center gap-2 text-[#20262d]">
      <span
        className={cn(
          "h-1.5 w-1.5 rounded-full",
          success ? "bg-[#3db34a]" : danger ? "bg-[#e5484d]" : "bg-[#f08a00]"
        )}
      />
      <span className="leading-[18px]">{STATUS_LABELS[normalized] || normalized || "--"}</span>
    </div>
  );
}

function BillDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [rows, setRows] = useState<TopupBill[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const totalPages = Math.max(1, Math.ceil(total / BILL_PAGE_SIZE));

  useEffect(() => {
    if (!open) return;
    let ignore = false;

    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await walletApi.getTopupBills({
          pageNo,
          pageSize: BILL_PAGE_SIZE,
          keyword: keyword.trim() || undefined,
        });
        if (ignore) return;
        setRows(data.list || []);
        setTotal(data.total || 0);
      } catch (err) {
        if (ignore) return;
        setRows([]);
        setTotal(0);
        setError(err instanceof Error ? err.message : "加载充值账单失败");
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    })();

    return () => {
      ignore = true;
    };
  }, [open, pageNo, keyword]);

  const copyTradeNo = (tradeNo: string | null | undefined) => {
    if (!tradeNo) return;
    navigator.clipboard?.writeText(tradeNo);
    toast.success("订单号已复制");
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] w-[920px] max-w-[calc(100vw-32px)] gap-0 overflow-hidden rounded-[10px] border border-[#6d6d6d] bg-white p-0 text-[#24292f] shadow-[0_18px_56px_rgba(0,0,0,0.30)] sm:max-w-[920px]">
        <DialogHeader className="gap-0 px-[26px] pb-0 pt-[26px]">
          <DialogTitle className="text-[18px] font-semibold leading-6 text-[#20242a]">充值账单</DialogTitle>
        </DialogHeader>

        <div className="px-[26px] pt-[23px]">
          <div className="flex h-8 items-center rounded-[8px] bg-[#f5f5f6]">
            <Search className="ml-[10px] h-4 w-4 text-[#747980]" />
            <input
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPageNo(1);
              }}
              className="w-full bg-transparent px-3 text-[13px] outline-none placeholder:text-[#777d85]"
              placeholder="订单号"
            />
          </div>
        </div>

        <div className="mt-[19px] max-h-[calc(92vh-190px)] overflow-auto px-[26px]">
          {error ? (
            <div className="flex h-[180px] items-center justify-center rounded-[10px] border border-[#f0d4d4] bg-[#fff8f8] px-4 text-center text-[14px] text-[#b42318]">
              {error}
            </div>
          ) : loading && rows.length === 0 ? (
            <div className="flex h-[180px] items-center justify-center text-[14px] text-[#7a8088]">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              加载充值账单
            </div>
          ) : rows.length === 0 ? (
            <div className="flex h-[180px] items-center justify-center text-[14px] text-[#7a8088]">
              暂无充值记录
            </div>
          ) : (
            <table className="w-full table-fixed border-collapse text-[14px]">
              <thead>
                <tr className="border-b border-[#e7e9ed] text-left text-[#777c83]">
                  <th className="w-[38%] px-4 pb-[15px] font-semibold">订单号</th>
                  <th className="w-[12%] px-4 pb-[15px] font-semibold">支付方式</th>
                  <th className="w-[12%] px-4 pb-[15px] font-semibold">充值额度</th>
                  <th className="w-[12%] px-4 pb-[15px] font-semibold">支付金额</th>
                  <th className="w-[10%] px-4 pb-[15px] font-semibold">状态</th>
                  <th className="w-[16%] px-4 pb-[15px] font-semibold">创建时间</th>
                </tr>
              </thead>
              <tbody className={cn(loading && "opacity-60")}>
                {rows.map((row) => (
                  <tr key={row.id || row.tradeNo} className="border-b border-[#edf0f2]">
                    <td className="px-4 py-[12px]">
                      <div className="flex items-center gap-2">
                        <span className="break-all leading-5">{row.tradeNo || "--"}</span>
                        {row.tradeNo && (
                          <button
                            type="button"
                            onClick={() => copyTradeNo(row.tradeNo)}
                            className="shrink-0 text-[#2864f6]"
                            aria-label="复制订单号"
                          >
                            <Copy className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-[12px] leading-5">
                      {row.paymentMethod ? PAYMENT_METHOD_LABELS[row.paymentMethod] || row.paymentMethod : "--"}
                    </td>
                    <td className="px-4 py-[12px]">
                      <div className="flex items-center gap-1.5">
                        <Link2 className="h-4 w-4 text-[#20262d]" />
                        {row.amount ?? "--"}
                      </div>
                    </td>
                    <td className="px-4 py-[12px] text-[#ff2d25]">{formatBillMoney(row.money)}</td>
                    <td className="px-4 py-[12px]">
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="px-4 py-[12px] leading-5">{formatBillTime(row.createTime)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="flex items-center justify-between px-[26px] pb-6 pt-4 text-[13px] text-[#6c727a]">
          <span>共 {total} 条</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={loading || pageNo <= 1}
              onClick={() => setPageNo((current) => Math.max(1, current - 1))}
              className="h-8 rounded-[8px] border border-[#e7e9ed] px-3 font-medium text-[#3f4650] transition hover:border-[#2864f6]/40 disabled:cursor-not-allowed disabled:opacity-50"
            >
              上一页
            </button>
            <span className="min-w-[62px] text-center">
              {pageNo} / {totalPages}
            </span>
            <button
              type="button"
              disabled={loading || pageNo >= totalPages}
              onClick={() => setPageNo((current) => Math.min(totalPages, current + 1))}
              className="h-8 rounded-[8px] border border-[#e7e9ed] px-3 font-medium text-[#3f4650] transition hover:border-[#2864f6]/40 disabled:cursor-not-allowed disabled:opacity-50"
            >
              下一页
            </button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function AlipayConfirmDialog({
  open,
  option,
  submitting,
  onOpenChange,
  onConfirm,
}: {
  open: boolean;
  option: AlipayReferenceAmount | null;
  submitting: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  const amountText = formatAmountText(option?.amount);
  const productName = option?.name || amountText;

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !submitting && onOpenChange(nextOpen)}>
      <DialogContent className="w-[448px] max-w-[calc(100vw-32px)] gap-0 rounded-[10px] border border-[#6d6d6d] bg-white p-0 text-[#20242a] shadow-[0_18px_56px_rgba(0,0,0,0.30)]">
        <DialogHeader className="gap-0 px-6 pb-0 pt-6">
          <DialogTitle className="text-[18px] font-semibold leading-6">支付宝充值确认</DialogTitle>
        </DialogHeader>

        <div className="px-6 pb-10 pt-6 text-[14px] leading-[18px] text-[#2f343a]">
          <div>产品名称： {productName}</div>
          <div>价格： ¥{amountText}</div>
          <div>充值额度： {option?.quota ?? amountText}</div>
          <div>是否确认充值?</div>
        </div>

        <div className="flex justify-end gap-3 px-6 pb-6">
          <button
            type="button"
            disabled={submitting}
            onClick={() => onOpenChange(false)}
            className="h-8 rounded-[10px] bg-[#f5f6f8] px-4 text-[14px] font-semibold text-[#5f666e] transition hover:bg-[#ebeef2] disabled:cursor-not-allowed disabled:opacity-70"
          >
            取消
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={onConfirm}
            className="inline-flex h-8 items-center rounded-[10px] bg-[#2864f6] px-4 text-[14px] font-semibold text-white transition hover:bg-[#1f59e9] disabled:cursor-not-allowed disabled:opacity-70"
          >
            {submitting && <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />}
            确定
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default function WalletSettingsPage() {
  const [billOpen, setBillOpen] = useState(false);
  const [amountsLoading, setAmountsLoading] = useState(true);
  const [amountsError, setAmountsError] = useState<string | null>(null);
  const [rechargeOptions, setRechargeOptions] = useState<AlipayReferenceAmount[]>([]);
  const [selectedOption, setSelectedOption] = useState<AlipayReferenceAmount | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [paying, setPaying] = useState(false);
  const [redeemCode, setRedeemCode] = useState("");
  const [redeeming, setRedeeming] = useState(false);
  const [topupEnabled, setTopupEnabled] = useState<boolean | null>(null);
  const [stats, setStats] = useState<WalletStatsResp | null>(null);

  const fetchStats = useCallback(async () => {
    const data = await walletApi.getStats();
    persistQuotaDisplayConfig(data);
    setStats(data);
  }, []);

  useEffect(() => {
    let ignore = false;

    (async () => {
      try {
        const data = await walletApi.getStats();
        if (ignore) return;
        persistQuotaDisplayConfig(data);
        setStats(data);
      } catch {
        if (!ignore) {
          setStats(null);
        }
      }
    })();

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;

    (async () => {
      setAmountsLoading(true);
      setAmountsError(null);
      try {
        const data = await walletApi.getAlipayAmounts();
        if (ignore) return;
        setRechargeOptions(data.referenceAmounts || []);
      } catch (err) {
        if (ignore) return;
        const message = err instanceof Error ? err.message : "加载充值金额失败";
        setAmountsError(message);
      } finally {
        if (!ignore) {
          setAmountsLoading(false);
        }
      }
    })();

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;

    (async () => {
      try {
        const data = await walletApi.getCapabilities();
        if (!ignore) {
          setTopupEnabled(Boolean(data.topupEnabled));
        }
      } catch {
        if (!ignore) {
          setTopupEnabled(false);
        }
      }
    })();

    return () => {
      ignore = true;
    };
  }, []);

  const handleRecharge = (option: AlipayReferenceAmount) => {
    setSelectedOption(option);
    setConfirmOpen(true);
  };

  const handleConfirmPay = async () => {
    if (!selectedOption?.amount) return;

    const payWindow = window.open("about:blank", "_blank");
    setPaying(true);
    try {
      const data = await walletApi.createAlipayPayUrl(selectedOption.amount, selectedOption.productId);
      if (!data.checkoutUrl) {
        throw new Error("未获取到支付宝充值链接");
      }
      if (payWindow) {
        payWindow.location.href = data.checkoutUrl;
      } else {
        window.open(data.checkoutUrl, "_blank");
      }
      setConfirmOpen(false);
      toast.success("支付宝充值链接已打开");
    } catch (err) {
      payWindow?.close();
      toast.error(err instanceof Error ? err.message : "创建支付宝充值链接失败");
    } finally {
      setPaying(false);
    }
  };

  const handleRedeem = async () => {
    const key = redeemCode.trim();
    if (!key || redeeming) return;
    if (topupEnabled !== true) {
      toast.error("兑换码充值未开启");
      return;
    }

    setRedeeming(true);
    try {
      const data = await walletApi.topup(key);
      setRedeemCode("");
      toast.success(data.message || "兑换码充值成功");
      void fetchStats().catch(() => {
        setStats(null);
      });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "兑换码充值失败");
    } finally {
      setRedeeming(false);
    }
  };

  return (
    <>
      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="visible"
        className="w-full max-w-[1264px]"
      >
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <WalletOverviewCard
            onOpenBill={() => setBillOpen(true)}
            stats={stats}
            rechargeOptions={rechargeOptions}
            amountsLoading={amountsLoading}
            amountsError={amountsError}
            onRecharge={handleRecharge}
            redeemCode={redeemCode}
            redeeming={redeeming}
            topupEnabled={topupEnabled}
            onRedeemCodeChange={setRedeemCode}
            onRedeem={handleRedeem}
          />
          <InviteRewardCard stats={stats} />
        </div>
      </motion.div>

      <BillDialog open={billOpen} onOpenChange={setBillOpen} />
      <AlipayConfirmDialog
        open={confirmOpen}
        option={selectedOption}
        submitting={paying}
        onOpenChange={setConfirmOpen}
        onConfirm={handleConfirmPay}
      />
    </>
  );
}
