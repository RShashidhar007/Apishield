-- APIShield baseline schema (SQL Server 2022). All timestamps are DATETIME2(3) in UTC.
CREATE TABLE roles (
  id INT IDENTITY(1,1) PRIMARY KEY,
  name VARCHAR(32) NOT NULL CONSTRAINT uq_roles_name UNIQUE
);
INSERT INTO roles(name) VALUES ('ADMIN'), ('API_MANAGER'), ('DEVELOPER');

CREATE TABLE users (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  email VARCHAR(254) NOT NULL CONSTRAINT uq_users_email UNIQUE,
  display_name NVARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  active BIT NOT NULL CONSTRAINT df_users_active DEFAULT 1,
  failed_attempts INT NOT NULL CONSTRAINT df_users_failed DEFAULT 0,
  locked_until DATETIME2(3) NULL,
  last_login_at DATETIME2(3) NULL,
  created_at DATETIME2(3) NOT NULL,
  updated_at DATETIME2(3) NOT NULL
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id INT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE refresh_tokens (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL CONSTRAINT uq_rt_hash UNIQUE,
  family_id VARCHAR(36) NOT NULL,
  expires_at DATETIME2(3) NOT NULL,
  revoked BIT NOT NULL CONSTRAINT df_rt_revoked DEFAULT 0,
  used BIT NOT NULL CONSTRAINT df_rt_used DEFAULT 0,
  created_at DATETIME2(3) NOT NULL,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_rt_family ON refresh_tokens(family_id);
CREATE INDEX ix_rt_user ON refresh_tokens(user_id);

-- An "API" row is one version of an upstream service together with its route and policies.
CREATE TABLE apis (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(100) NOT NULL,
  description NVARCHAR(1000) NULL,
  version VARCHAR(32) NOT NULL,
  environment VARCHAR(16) NOT NULL,           -- DEV | STAGING | PROD
  status VARCHAR(16) NOT NULL,                -- ACTIVE | INACTIVE | ARCHIVED
  gateway_path VARCHAR(120) NOT NULL,         -- public path = /gateway + gateway_path
  upstream_url VARCHAR(500) NOT NULL,
  methods VARCHAR(80) NOT NULL,               -- comma separated, upper case
  auth_policy VARCHAR(16) NOT NULL,           -- API_KEY | JWT | NONE
  rate_limit_enabled BIT NOT NULL,
  rate_limit_requests INT NOT NULL,
  rate_limit_window_seconds INT NOT NULL,
  rate_limit_burst INT NOT NULL,
  connect_timeout_ms INT NOT NULL,
  response_timeout_ms INT NOT NULL,
  max_request_bytes BIGINT NOT NULL,
  allowed_headers VARCHAR(500) NULL,
  cors_origins VARCHAR(500) NULL,
  owner_id BIGINT NOT NULL,
  created_at DATETIME2(3) NOT NULL,
  updated_at DATETIME2(3) NOT NULL,
  CONSTRAINT fk_apis_owner FOREIGN KEY (owner_id) REFERENCES users(id),
  CONSTRAINT uq_apis_name_version_env UNIQUE (name, version, environment)
);
-- A path may only be used by one non-archived API; enforced by a filtered unique index.
CREATE UNIQUE INDEX ux_apis_path_active ON apis(gateway_path) WHERE status <> 'ARCHIVED';
CREATE INDEX ix_apis_status ON apis(status);

CREATE TABLE api_keys (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  name NVARCHAR(100) NOT NULL,
  description NVARCHAR(500) NULL,
  prefix VARCHAR(12) NOT NULL CONSTRAINT uq_keys_prefix UNIQUE,
  key_hash VARCHAR(64) NOT NULL,
  owner_id BIGINT NOT NULL,
  expires_at DATETIME2(3) NULL,
  revoked BIT NOT NULL CONSTRAINT df_keys_revoked DEFAULT 0,
  revoked_at DATETIME2(3) NULL,
  quota_requests INT NULL,
  last_used_at DATETIME2(3) NULL,
  rotated_from BIGINT NULL,
  created_at DATETIME2(3) NOT NULL,
  CONSTRAINT fk_keys_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE INDEX ix_keys_owner ON api_keys(owner_id);

CREATE TABLE api_key_apis (
  key_id BIGINT NOT NULL,
  api_id BIGINT NOT NULL,
  PRIMARY KEY (key_id, api_id),
  CONSTRAINT fk_kapi_key FOREIGN KEY (key_id) REFERENCES api_keys(id) ON DELETE CASCADE,
  CONSTRAINT fk_kapi_api FOREIGN KEY (api_id) REFERENCES apis(id)
);

CREATE TABLE api_key_scopes (
  key_id BIGINT NOT NULL,
  scope VARCHAR(16) NOT NULL,
  PRIMARY KEY (key_id, scope),
  CONSTRAINT fk_ks_key FOREIGN KEY (key_id) REFERENCES api_keys(id) ON DELETE CASCADE
);

-- High-volume table: retention purge job deletes rows older than apishield.retention.request-log-days.
CREATE TABLE request_logs (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  ts DATETIME2(3) NOT NULL,
  correlation_id VARCHAR(64) NOT NULL,
  api_id BIGINT NULL,
  key_id BIGINT NULL,
  user_id BIGINT NULL,
  client_ip VARCHAR(64) NULL,
  method VARCHAR(10) NOT NULL,
  path VARCHAR(512) NOT NULL,
  status INT NOT NULL,
  latency_ms BIGINT NOT NULL,
  upstream_latency_ms BIGINT NULL,
  request_bytes BIGINT NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  reason VARCHAR(64) NULL
);
CREATE INDEX ix_rl_ts ON request_logs(ts) INCLUDE (api_id, status, latency_ms, outcome);
CREATE INDEX ix_rl_api_ts ON request_logs(api_id, ts);
CREATE INDEX ix_rl_ip_outcome_ts ON request_logs(client_ip, outcome, ts);
CREATE INDEX ix_rl_corr ON request_logs(correlation_id);
CREATE INDEX ix_rl_key_ts ON request_logs(key_id, ts);

CREATE TABLE security_events (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  created_at DATETIME2(3) NOT NULL,
  type VARCHAR(48) NOT NULL,
  severity VARCHAR(16) NOT NULL,              -- LOW | MEDIUM | HIGH | CRITICAL
  api_id BIGINT NULL,
  client_ip VARCHAR(64) NULL,
  correlation_id VARCHAR(64) NULL,
  description NVARCHAR(500) NOT NULL,
  occurrences INT NOT NULL,
  acknowledged BIT NOT NULL CONSTRAINT df_se_ack DEFAULT 0,
  acknowledged_by BIGINT NULL,
  acknowledged_at DATETIME2(3) NULL,
  note NVARCHAR(1000) NULL
);
CREATE INDEX ix_se_created ON security_events(created_at DESC);
CREATE INDEX ix_se_sev_ack ON security_events(severity, acknowledged);
CREATE INDEX ix_se_dedupe ON security_events(type, client_ip, api_id, created_at);

CREATE TABLE audit_logs (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  ts DATETIME2(3) NOT NULL,
  actor_id BIGINT NULL,
  actor_email VARCHAR(254) NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(32) NOT NULL,
  resource_id VARCHAR(64) NULL,
  outcome VARCHAR(16) NOT NULL,               -- SUCCESS | FAILURE | DENIED
  detail NVARCHAR(500) NULL,
  correlation_id VARCHAR(64) NULL
);
CREATE INDEX ix_audit_ts ON audit_logs(ts DESC);
CREATE INDEX ix_audit_actor ON audit_logs(actor_id, ts);
CREATE INDEX ix_audit_action ON audit_logs(action, ts);
