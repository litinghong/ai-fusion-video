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

export const walletApi = {
  getAlipayAmounts: () =>
    http.get<never, AlipayAmountsResp>("/api/wallet/alipay/amounts"),

  createAlipayPayUrl: (amount: string, productId?: string | null) =>
    http.post<never, AlipayPayResp>("/api/wallet/alipay/pay", {
      amount,
      productId,
    }),
};
