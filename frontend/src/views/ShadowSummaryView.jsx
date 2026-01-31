import React from "react";
import TrafficDumpView from "./TrafficDumpView.jsx";

export default function ShadowSummaryView({
  state,
  onRefresh,
  trafficState,
  onUpload,
  onRunDemo
}) {
  const { data, loading, error } = state;

  if (!data) {
    return (
      <section className="card">
        <h2>Shadow summary</h2>
        <p>{loading ? "Loading data..." : "No data available."}</p>
        <button type="button" className="button ghost" onClick={onRefresh}>
          Try again
        </button>
        {error ? <p className="notice warn">{error}</p> : null}
      </section>
    );
  }

  return (
    <section className="summary">
      {error ? <p className="notice warn">{error}</p> : null}
      <div className="grid two">
        <div className="card">
          <h2>Deployment snapshot</h2>
          <div className="stat">
            <span>Deployment</span>
            <strong>{data.deploymentId}</strong>
          </div>
          <div className="stat">
            <span>Service</span>
            <strong>{data.serviceName}</strong>
          </div>
          <div className="stat">
            <span>Risk score</span>
            <strong>{Math.round(data.riskScore * 100)}%</strong>
          </div>
          <div className="stat">
            <span>Generated</span>
            <strong>{new Date(data.generatedAt).toLocaleString()}</strong>
          </div>
          {error ? (
            <p className="notice warn">
              Using fallback data because the backend is unreachable.
            </p>
          ) : null}
        </div>

        <div className="card">
          <h2>AI failure explanation</h2>
          <ul className="insights">
            {data.aiInsights?.map((insight) => (
              <li key={insight}>{insight}</li>
            ))}
          </ul>
        </div>
      </div>

      <div className="card">
        <h2>Diff metrics</h2>
        <div className="grid three">
          {data.metrics?.map((metric) => (
            <div key={metric.label} className="metric">
              <div>
                <p className="metric__label">{metric.label}</p>
                <p className="metric__description">{metric.description}</p>
              </div>
              <p className="metric__value">
                {metric.value}
                <span>{metric.unit}</span>
              </p>
            </div>
          ))}
        </div>
      </div>

      <div className="grid two">
        <div className="card">
          <h2>Key findings</h2>
          <div className="list">
            {data.findings?.map((finding) => (
              <div key={finding.id} className="list-item">
                <div>
                  <p className="list-title">{finding.title}</p>
                  <p className="muted">{finding.description}</p>
                  <p className="muted">
                    Recommendation: {finding.recommendation}
                  </p>
                </div>
                <div className={`pill ${finding.severity}`}>
                  {finding.severity}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Risk owners</h2>
          <div className="list">
            {data.riskItems?.map((item) => (
              <div key={item.label} className="list-item">
                <div>
                  <p className="list-title">{item.label}</p>
                  <p className="muted">{item.impact}</p>
                  <p className="muted">Owner: {item.owner}</p>
                </div>
                <div className={`pill ${item.severity}`}>
                  {item.severity}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <TrafficDumpView
        state={trafficState}
        onUpload={onUpload}
        onRunDemo={onRunDemo}
      />
    </section>
  );
}
