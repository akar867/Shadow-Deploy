import React, { useState } from "react";

export default function LoginView({ onLogin, error, loading }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("shadowdeploy");

  const handleSubmit = async (event) => {
    event.preventDefault();
    await onLogin(username, password);
  };

  return (
    <div className="login">
      <div className="card login-card">
        <p className="tag">ShadowDeploy</p>
        <h1>Welcome back</h1>
        <p className="subtitle">
          Sign in to review shadow traffic analysis and upload new dumps.
        </p>
        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            Username
            <input
              className="input"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
            />
          </label>
          <label className="field">
            Password
            <input
              className="input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
          </label>
          {error ? <p className="notice warn">{error}</p> : null}
          <button className="button" type="submit" disabled={loading}>
            {loading ? "Signing in..." : "Login"}
          </button>
          <p className="muted">
            Default credentials: admin / shadowdeploy
          </p>
        </form>
      </div>
    </div>
  );
}
