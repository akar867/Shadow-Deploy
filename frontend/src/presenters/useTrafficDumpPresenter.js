import { useCallback, useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";

export function useTrafficDumpPresenter(token) {
  const [state, setState] = useState({
    loading: false,
    uploading: false,
    error: null,
    message: null,
    dumps: []
  });

  const load = useCallback(async () => {
    if (!token) {
      setState({
        loading: false,
        uploading: false,
        error: null,
        message: null,
        dumps: []
      });
      return;
    }
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const response = await fetch(`${API_BASE}/traffic-dumps`, {
        headers: {
          "X-Shadow-Token": token
        }
      });
      if (!response.ok) {
        throw new Error("Unable to load traffic dumps");
      }
      const dumps = await response.json();
      setState((current) => ({
        ...current,
        loading: false,
        dumps,
        error: null
      }));
    } catch (error) {
      setState((current) => ({
        ...current,
        loading: false,
        error: error?.message ?? "Unable to load traffic dumps"
      }));
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load]);

  const upload = useCallback(
    async ({ file, serviceName, deploymentId }) => {
      if (!token) {
        setState((current) => ({
          ...current,
          error: "Login required to upload traffic dumps"
        }));
        return null;
      }
      if (!file) {
        setState((current) => ({
          ...current,
          error: "Select a traffic dump file to upload"
        }));
        return null;
      }
      const formData = new FormData();
      formData.append("file", file);
      if (serviceName) formData.append("serviceName", serviceName);
      if (deploymentId) formData.append("deploymentId", deploymentId);

      setState((current) => ({
        ...current,
        uploading: true,
        error: null,
        message: null
      }));

      try {
        const response = await fetch(`${API_BASE}/traffic-dumps`, {
          method: "POST",
          headers: {
            "X-Shadow-Token": token
          },
          body: formData
        });
        if (!response.ok) {
          throw new Error("Upload failed");
        }
        const payload = await response.json();
        setState((current) => ({
          ...current,
          uploading: false,
          message: "Traffic dump processed and summary updated.",
          dumps: [payload.trafficDump, ...current.dumps]
        }));
        return payload;
      } catch (error) {
        setState((current) => ({
          ...current,
          uploading: false,
          error: error?.message ?? "Upload failed"
        }));
        return null;
      }
    },
    [token]
  );

  const runDemo = useCallback(async () => {
    if (!token) {
      setState((current) => ({
        ...current,
        error: "Login required to run demo mode"
      }));
      return null;
    }
    setState((current) => ({
      ...current,
      uploading: true,
      error: null,
      message: null
    }));
    try {
      const response = await fetch(`${API_BASE}/demo/run`, {
        method: "POST",
        headers: {
          "X-Shadow-Token": token
        }
      });
      if (!response.ok) {
        throw new Error("Demo run failed");
      }
      const payload = await response.json();
      setState((current) => ({
        ...current,
        uploading: false,
        message: "Demo traffic processed and summary updated.",
        dumps: [payload.trafficDump, ...current.dumps]
      }));
      return payload;
    } catch (error) {
      setState((current) => ({
        ...current,
        uploading: false,
        error: error?.message ?? "Demo run failed"
      }));
      return null;
    }
  }, [token]);

  return {
    state,
    load,
    upload,
    runDemo
  };
}
