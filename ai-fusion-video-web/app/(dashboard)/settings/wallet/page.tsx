"use client";

import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
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
import { walletApi, type AlipayReferenceAmount } from "@/lib/api/wallet";
import { containerVariants, itemVariants } from "../_shared";

const billRows = [
  { id: "ali_40062b794e11e52bda6f5f1522ebd7eefba77b72", quota: 2000, amount: "¥2000.00", status: "pending", time: "2026-04-21 23:32:35" },
  { id: "ali_8053447c8e250c1aab0fbc415a14f11ab2da3e02", quota: 200, amount: "¥200.00", status: "success", time: "2026-04-20 09:44:40" },
  { id: "ali_47837d96b2ae04946c0b450ee05c86469361896f", quota: 50, amount: "¥50.00", status: "pending", time: "2026-04-20 09:11:18" },
  { id: "ali_1ead13b1644d91ab6c7aa4beb9f249ff7d321f9e", quota: 200, amount: "¥200.00", status: "pending", time: "2026-04-20 09:10:20" },
  { id: "ali_9b6c6e1876f2974c08f07532cd3af0a0721f2480", quota: 200, amount: "¥200.00", status: "pending", time: "2026-04-20 09:10:12" },
  { id: "ali_d5aa65e41abebebf8aa821edb179f0ace100e444", quota: 100, amount: "¥100.00", status: "pending", time: "2026-04-20 09:07:08" },
  { id: "ali_ef085a891d96e6d768292bc0aa3336ec232e66d8", quota: 2000, amount: "¥2000.00", status: "success", time: "2026-04-20 08:54:00" },
  { id: "ali_fb1a85ee8e3c5876b53eca5173aef31569f10eda", quota: 2000, amount: "¥2000.00", status: "success", time: "2026-04-20 03:03:10" },
];

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

function WalletOverviewCard({
  onOpenBill,
  rechargeOptions,
  amountsLoading,
  amountsError,
  onRecharge,
}: {
  onOpenBill: () => void;
  rechargeOptions: AlipayReferenceAmount[];
  amountsLoading: boolean;
  amountsError: string | null;
  onRecharge: (option: AlipayReferenceAmount) => void;
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
            <StatItem value="¥1421.95" label="当前余额" icon={<Wallet className="h-3.5 w-3.5" />} />
            <StatItem value="¥1988.36" label="历史消耗" icon={<Zap className="h-3.5 w-3.5" />} />
            <StatItem value="5396" label="请求次数" icon={<BarChart3 className="h-3.5 w-3.5" />} />
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
                className="min-w-0 flex-1 bg-transparent px-3 text-[13px] text-[#2d333b] outline-none placeholder:text-[#8b9097]"
                placeholder="请输入兑换码"
              />
              <button
                type="button"
                className="mr-0 h-8 rounded-[10px] bg-[#2864f6] px-[13px] text-[14px] font-semibold text-white transition hover:bg-[#1f59e9]"
              >
                兑换额度
              </button>
            </div>
          </div>
        </div>
      </div>
    </motion.section>
  );
}

function InviteRewardCard() {
  const inviteUrl = "https://aigateways.cn/register?aff=qFWN";

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
            <StatItem value="¥0.00" label="待使用收益" icon={<Zap className="h-3.5 w-3.5" />} />
            <StatItem value="¥0.00" label="总收益" icon={<BarChart3 className="h-3.5 w-3.5" />} />
            <StatItem value="0" label="邀请人数" icon={<Users className="h-3.5 w-3.5" />} />
          </div>
        </div>
      </div>

      <div className="mx-[10px] h-[43px] rounded-b-[10px] border border-t-0 border-[#ececf0] px-[10px] py-[10px]">
        <div className="flex h-8 -translate-y-[1px] items-center rounded-[9px] bg-[#f5f5f6]">
          <span className="px-3 text-[13px] text-[#7f8389]">邀请链接</span>
          <span className="min-w-0 flex-1 truncate text-[14px] text-[#24292f]">{inviteUrl}</span>
          <button
            type="button"
            onClick={() => navigator.clipboard?.writeText(inviteUrl)}
            className="mr-0 inline-flex h-8 items-center gap-2 rounded-[10px] bg-[#2864f6] px-[14px] text-[14px] font-semibold text-white transition hover:bg-[#1f59e9]"
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

function StatusBadge({ status }: { status: string }) {
  const success = status === "success";

  return (
    <div className="flex items-center gap-2 text-[#20262d]">
      <span className={cn("h-1.5 w-1.5 rounded-full", success ? "bg-[#3db34a]" : "bg-[#f08a00]")} />
      <span className="leading-[18px]">{success ? "成功" : "待支付"}</span>
    </div>
  );
}

function BillDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const rows = useMemo(() => billRows, []);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] w-[920px] max-w-[calc(100vw-32px)] gap-0 overflow-hidden rounded-[10px] border border-[#6d6d6d] bg-white p-0 text-[#24292f] shadow-[0_18px_56px_rgba(0,0,0,0.30)]">
        <DialogHeader className="gap-0 px-[26px] pb-0 pt-[26px]">
          <DialogTitle className="text-[18px] font-semibold leading-6 text-[#20242a]">充值账单</DialogTitle>
        </DialogHeader>

        <div className="px-[26px] pt-[23px]">
          <div className="flex h-8 items-center rounded-[8px] bg-[#f5f5f6]">
            <Search className="ml-[10px] h-4 w-4 text-[#747980]" />
            <input
              className="w-full bg-transparent px-3 text-[13px] outline-none placeholder:text-[#777d85]"
              placeholder="订单号"
            />
          </div>
        </div>

        <div className="mt-[19px] max-h-[calc(92vh-134px)] overflow-auto px-[26px] pb-6">
          <table className="w-full table-fixed border-collapse text-[14px]">
            <thead>
              <tr className="border-b border-[#e7e9ed] text-left text-[#777c83]">
                <th className="w-[45%] px-4 pb-[15px] font-semibold">订单号</th>
                <th className="w-[6%] px-4 pb-[15px] font-semibold">支付方式</th>
                <th className="w-[10%] px-4 pb-[15px] font-semibold">充值额度</th>
                <th className="w-[11%] px-4 pb-[15px] font-semibold">支付金额</th>
                <th className="w-[7%] px-4 pb-[15px] font-semibold">状态</th>
                <th className="w-[10%] px-4 pb-[15px] font-semibold">操作</th>
                <th className="w-[11%] px-4 pb-[15px] font-semibold">创建时间</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-[#edf0f2]">
                  <td className="px-4 py-[12px]">
                    <div className="flex items-center gap-2">
                      <span className="break-all leading-5">{row.id}</span>
                      <Copy className="h-4 w-4 shrink-0 text-[#2864f6]" />
                    </div>
                  </td>
                  <td className="px-4 py-[12px] leading-5">支付宝</td>
                  <td className="px-4 py-[12px]">
                    <div className="flex items-center gap-1.5">
                      <Link2 className="h-4 w-4 text-[#20262d]" />
                      {row.quota}
                    </div>
                  </td>
                  <td className="px-4 py-[12px] text-[#ff2d25]">{row.amount}</td>
                  <td className="px-4 py-[12px]">
                    <StatusBadge status={row.status} />
                  </td>
                  <td className="px-4 py-[12px]">
                    {row.status === "pending" ? (
                      <button
                        type="button"
                        className="h-[27px] rounded-full border border-[#edf0f2] px-3 text-[14px] font-semibold text-[#2864f6] transition hover:border-[#2864f6]/40"
                      >
                        补单
                      </button>
                    ) : (
                      <span className="text-[#9aa0a6]"> </span>
                    )}
                  </td>
                  <td className="px-4 py-[12px] leading-5">{row.time}</td>
                </tr>
              ))}
            </tbody>
          </table>
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
            rechargeOptions={rechargeOptions}
            amountsLoading={amountsLoading}
            amountsError={amountsError}
            onRecharge={handleRecharge}
          />
          <InviteRewardCard />
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
