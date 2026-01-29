import React, { useState } from "react";

export default function TrafficDumpView({ state, onUpload }) {
  const [file, setFile] = useState(null);
  const [serviceName, setServiceName] = useState("");
  const [deploymentId, setDeploymentId] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    await onUpload({ file, serviceName, deploymentId });
    setFile(null);
  };

  return (
    <section className="traffic">
      <div className="card">
        <h2>Upload traffic dump</h2>
        <p className="muted">
          Upload real production request logs to simulate a shadow run.
        </p>
        <form className="form" onSubmit={handleSubmit}>
          <div className="grid two">
            <label className="field">
              Service name
              <input
                className="input"
                type="text"
                placeholder="checkout-service"
                value={serviceName}
                onChange={(event) => setServiceName(event.target.value)}
              />
            </label>
            <label className="field">
              Deployment id
              <input
                className="input"
                type="text"
                placeholder="deploy-2026-01-28-rc1"
                value={deploymentId}
                onChange={(event) => setDeploymentId(event.target.value)}
              />
            </label>
          </div>
          <div className="upload-row">
            <input
              className="input file-input"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            <button className="button" type="submit" disabled={state.uploading}>
              {state.uploading ? "Uploading..." : "Run shadow test"}
            </button>
          </div>
          {state.error ? <p className="notice warn">{state.error}</p> : null}
          {state.message ? <p className="notice success">{state.message}</p> : null}
        </form>
      </div>

      <div className="card">
        <h2>Recent traffic dumps</h2>
        {state.loading ? (
          <p className="muted">Loading traffic dumps...</p>
        ) : (
          <div className="list">
            {state.dumps?.length ? (
              state.dumps.map((dump) => (
                <div key={dump.id} className="list-item">
                  <div>
                    <p className="list-title">{dump.fileName}</p>
                    <p className="muted">
                      {dump.serviceName} · {dump.deploymentId}
                    </p>
                    <p className="muted">
                      {new Date(dump.uploadedAt).toLocaleString()} ·{" "}
                      {Math.round(dump.sizeBytes / 1024)} KB
                    </p>
                  </div>
                  <span className="pill low">
                    {dump.summaryId ? "summary ready" : "uploaded"}
                  </span>
                </div>
              ))
            ) : (
              <p className="muted">No uploads yet. Add one above.</p>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
