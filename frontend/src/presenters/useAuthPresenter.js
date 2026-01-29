import { useCallback, useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";
const TOKEN_KEY = "shadowdeploy_token";

export function useAuthPresenter() {
  const [state, setState] = useState({
    loading: true,
    token: null,
    user: null,
    error: null
  });

  const hydrateFromToken = useCallback(async (token) => {
    if (!token) {
      setState({ loading: false, token: null, user: null, error: null });
      return;
    }
    try {
      const response = await fetch(`${API_BASE}/auth/me`, {
        headers: {
          "X-Shadow-Token": token
        }
      });
      if (!response.ok) {
        throw new Error("Session expired");
      }
      const user = await response.json();
      setState({ loading: false, token, user, error: null });
    } catch (error) {
      localStorage.removeItem(TOKEN_KEY);
      setState({
        loading: false,
        token: null,
        user: null,
        error: error?.message ?? "Unable to restore session"
      });
    }
  }, []);

  useEffect(() => {
    const stored = localStorage.getItem(TOKEN_KEY);
    hydrateFromToken(stored);
  }, [hydrateFromToken]);

  const login = useCallback(async (username, password) => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ username, password })
      });
      if (!response.ok) {
        throw new Error("Invalid credentials");
      }
      const payload = await response.json();
      localStorage.setItem(TOKEN_KEY, payload.token);
      setState({
        loading: false,
        token: payload.token,
        user: payload.user,
        error: null
      });
      return payload;
    } catch (error) {
      setState({
        loading: false,
        token: null,
        user: null,
        error: error?.message ?? "Login failed"
      });
      return null;
    }
  }, []);

  const logout = useCallback(async () => {
    const token = state.token;
    if (token) {
      try {
        await fetch(`${API_BASE}/auth/logout`, {
          method: "POST",
          headers: {
            "X-Shadow-Token": token
          }
        });
      } catch (error) {
        // Ignore logout failures and still clear local session.
      }
    }
    localStorage.removeItem(TOKEN_KEY);
    setState({ loading: false, token: null, user: null, error: null });
  }, [state.token]);

  return {
    state,
    login,
    logout
  };
}
