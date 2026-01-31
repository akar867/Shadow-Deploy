import React from "react";
import { useAuthPresenter } from "./presenters/useAuthPresenter.js";
import { useShadowSummaryPresenter } from "./presenters/useShadowSummaryPresenter.js";
import { useTrafficDumpPresenter } from "./presenters/useTrafficDumpPresenter.js";
import LoginView from "./views/LoginView.jsx";
import ShadowSummaryView from "./views/ShadowSummaryView.jsx";

export default function App() {
  const auth = useAuthPresenter();
  const summary = useShadowSummaryPresenter(auth.state.token);
  const traffic = useTrafficDumpPresenter(auth.state.token);

  const handleUpload = async (payload) => {
    const result = await traffic.upload(payload);
    if (result?.summary) {
      summary.setSummary(result.summary);
    } else {
      summary.refresh();
    }
  };

  const handleDemo = async () => {
    const result = await traffic.runDemo();
    if (result?.summary) {
      summary.setSummary(result.summary);
    } else {
      summary.refresh();
    }
  };

  if (auth.state.loading) {
    return (
      <div className="login">
        <div className="card login-card">
          <h2>Loading ShadowDeploy</h2>
          <p className="muted">Checking your session...</p>
        </div>
      </div>
    );
  }

  if (!auth.state.user) {
    return (
      <LoginView
        onLogin={auth.login}
        error={auth.state.error}
        loading={auth.state.loading}
      />
    );
  }

  return (
    <div className="app">
      <header className="app__header">
        <div>
          <p className="tag">ShadowDeploy</p>
          <h1>See production failures before you deploy</h1>
          <p className="subtitle">
            ShadowDeploy mirrors real traffic, compares behavior, and explains
            what will break before users notice.
          </p>
        </div>
        <div className="actions">
          <div className="user-pill">
            Signed in as {auth.state.user.displayName}
          </div>
          <div className="action-row">
            <button type="button" className="button ghost" onClick={auth.logout}>
              Logout
            </button>
            <button type="button" className="button" onClick={summary.refresh}>
              Refresh summary
            </button>
          </div>
          <span className="status-pill">{summary.state.statusLabel}</span>
        </div>
      </header>

      <main>
        <ShadowSummaryView
          state={summary.state}
          onRefresh={summary.refresh}
          trafficState={traffic.state}
          onUpload={handleUpload}
          onRunDemo={handleDemo}
        />
      </main>
    </div>
  );
}
