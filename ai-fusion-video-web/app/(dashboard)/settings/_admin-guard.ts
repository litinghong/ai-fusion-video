"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/store/auth-store";

export function useAdminGuard() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const fetchUserInfo = useAuthStore((s) => s.fetchUserInfo);
  const [checking, setChecking] = useState(true);
  const isAdmin = (user?.roles || []).includes("admin");

  useEffect(() => {
    let cancelled = false;

    const check = async () => {
      let currentUser = user;
      if (currentUser && (!currentUser.roles || currentUser.roles.length === 0)) {
        try {
          await fetchUserInfo();
        } catch {
          // ignore
        }
        currentUser = useAuthStore.getState().user;
      }

      const admin = (currentUser?.roles || []).includes("admin");
      if (!cancelled) {
        if (!admin) {
          router.replace("/settings/profile");
        }
        setChecking(false);
      }
    };

    check();
    return () => {
      cancelled = true;
    };
  }, [fetchUserInfo, router, user]);

  return { checking, isAdmin };
}
