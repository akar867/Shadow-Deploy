import { useCallback, useEffect, useMemo, useState } from "react";

const FALLBACK_SUMMARY = {
  deploymentId: "deploy-2026-01-28-rc1",
  serviceName: "checkout-service",
  status: "needs-attention",
  generatedAt: new Date().toISOString(),
  riskScore: 0.74,
  metrics: [
    {
      label: "HTTP mismatch rate",
      description: "Responses with non-matching status codes",
      value: 2.3,
      unit: "%"
    },
    {
      label: "Payload drift",
      description: "Responses with body differences above threshold",
      value: 6.1,
      unit: "%"
    }
  ],
  findings: [
    {
      id: "finding-001",
      title: "DiscountService null handling",
      severity: "high",
      affectedPercent: 2.3,
      description:
        "Checkout requests with couponType=FLASH return 500 due to null handling in DiscountService.",
      recommendation: "Guard against null couponType and add default discount fallback."
    }
  ],
  riskItems: [
    {
      label: "Checkout service",
      severity: "high",
      impact: "Potential revenue loss from 500 errors",
      owner: "payments-team"
    }
  ],
  aiInsights: [
    "2.3% of checkout requests fail due to null handling in DiscountService when couponType=FLASH."
  ]
};

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";

const statusLabelFor = (status, loading, error) => {
  if (loading) return "Loading summary";
  if (error) return "Offline fallback";
  if (status === "healthy") return "Healthy";
  if (status === "needs-attention") return "Needs attention";
  return "Review required";
};

export function useShadowSummaryPresenter(token) {
  const [state, setState] = useState({
    loading: false,
    error: null,
    data: null,
    statusLabel: token ? "Loading summary" : "Login required"
  });

  const loadSummary = useCallback(async () => {
    if (!token) {
      setState({
        loading: false,
        error: null,
        data: null,
        statusLabel: "Login required"
      });
      return;
    }
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const response = await fetch(`${API_BASE}/summary`, {
        headers: {
          "X-Shadow-Token": token
        }
      });
      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Unauthorized");
        }
        throw new Error(`Request failed with ${response.status}`);
      }
      const data = await response.json();
      setState({
        loading: false,
        error: null,
        data,
        statusLabel: statusLabelFor(data.status, false, null)
      });
    } catch (error) {
      if (error?.message === "Unauthorized") {
        setState({
          loading: false,
          error: "Session expired. Please log in again.",
          data: null,
          statusLabel: "Login required"
        });
        return;
      }
      setState({
        loading: false,
        error: error?.message ?? "Unable to reach backend",
        data: FALLBACK_SUMMARY,
        statusLabel: statusLabelFor("needs-attention", false, error)
      });
    }
  }, [token]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  const presenterState = useMemo(() => {
    if (!state.data) {
      return {
        ...state,
        statusLabel: state.statusLabel
      };
    }
    return {
      ...state,
      statusLabel: statusLabelFor(state.data.status, state.loading, state.error)
    };
  }, [state]);

  const setSummary = useCallback((summary) => {
    if (!summary) {
      return;
    }
    setState({
      loading: false,
      error: null,
      data: summary,
      statusLabel: statusLabelFor(summary.status, false, null)
    });
  }, []);

  return {
    state: presenterState,
    refresh: loadSummary,
    setSummary
  };
}
