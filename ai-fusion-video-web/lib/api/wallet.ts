import { http } from "./client";

export interface AlipayReferenceAmount {
  amount: string;
  currency: string;
  productId: string | null;
  name: string | null;
  quota: number | null;
}

export interface AlipayAmountsResp {
  minAmount: string;
  currency: string;
  referenceAmounts: AlipayReferenceAmount[];
}

export interface AlipayPayResp {
  checkoutUrl: string;
  orderId: string | null;
  amount: string | null;
  creditedAmount: number | null;
}

export interface TopupResp {
  message: string;
}

export interface WalletCapabilitiesResp {
  topupEnabled: boolean;
}

export interface WalletStatsResp {
  quota: number | null;
  usedQuota: number | null;
  requestCount: number | null;
  affQuota: number | null;
  affHistoryQuota: number | null;
  affCount: number | null;
  quotaPerUnit: number;
  quotaDisplayType: string;
  affCode: string | null;
  inviteUrl: string | null;
}

export interface TopupBill {
  id: number;
  userId: number | null;
  amount: number | null;
  money: number | null;
  tradeNo: string | null;
  paymentMethod: string | null;
  createTime: number | null;
  completeTime: number | null;
  status: string | null;
}

export interface PageResult<T> {
  list: T[];
  total: number;
}

export const walletApi = {
  getCapabilities: () =>
    http.get<never, WalletCapabilitiesResp>("/api/wallet/capabilities"),

  getStats: () =>
    http.get<never, WalletStatsResp>("/api/wallet/stats"),

  getAlipayAmounts: () =>
    http.get<never, AlipayAmountsResp>("/api/wallet/alipay/amounts"),

  createAlipayPayUrl: (amount: string, productId?: string | null) =>
    http.post<never, AlipayPayResp>("/api/wallet/alipay/pay", {
      amount,
      productId,
    }),

  getTopupBills: (params: { pageNo: number; pageSize: number; keyword?: string }) =>
    http.get<never, PageResult<TopupBill>>("/api/wallet/topup/bills", { params }),

  topup: (key: string) =>
    http.post<never, TopupResp>("/api/wallet/topup", { key }),
};
