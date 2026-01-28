import React from "react";
import { useShadowSummaryPresenter } from "./presenters/useShadowSummaryPresenter.js";
import ShadowSummaryView from "./views/ShadowSummaryView.jsx";

export default function App() {
  const { state, refresh } = useShadowSummaryPresenter();

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
          <button type="button" className="button" onClick={refresh}>
            Refresh summary
          </button>
          <span className="status-pill">{state.statusLabel}</span>
        </div>
      </header>

      <main>
        <ShadowSummaryView state={state} onRefresh={refresh} />
      </main>
    </div>
  );
}
