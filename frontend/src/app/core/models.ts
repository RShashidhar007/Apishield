export type Role = 'ADMIN' | 'API_MANAGER' | 'DEVELOPER';
export interface User { id: number; email: string; displayName: string; roles: Role[]; active: boolean; lastLoginAt?: string; createdAt: string; }
export interface TokenResponse { accessToken: string; refreshToken: string; expiresIn: number; tokenType: string; user: User; }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number; }
export interface ApiError { code: string; message: string; correlationId?: string; details?: Record<string, string>; }

export type ApiStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export type AuthPolicy = 'API_KEY' | 'JWT' | 'NONE';
export interface ApiDef {
  id: number; name: string; description?: string; version: string; environment: 'DEV' | 'STAGING' | 'PROD'; status: ApiStatus;
  gatewayPath: string; publicPath: string; upstreamUrl?: string; methods: string[]; authPolicy: AuthPolicy;
  rateLimitEnabled: boolean; rateLimitRequests: number; rateLimitWindowSeconds: number; rateLimitBurst: number;
  connectTimeoutMs: number; responseTimeoutMs: number; maxRequestBytes: number; allowedHeaders: string[]; corsOrigins: string[];
  ownerId?: number; createdAt: string; updatedAt: string;
}
export type ApiRequest = Omit<ApiDef, 'id' | 'status' | 'publicPath' | 'ownerId' | 'createdAt' | 'updatedAt'>;

export interface KeyView { id: number; name: string; description?: string; prefix: string; maskedKey: string; apiIds: number[]; scopes: string[];
  expiresAt?: string; status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'; revokedAt?: string; quotaRequests?: number; lastUsedAt?: string; createdAt: string; requests24h: number; ownerId: number; }
export interface KeyWithSecret { key: KeyView; rawKey: string; }
export interface KeyCreate { name: string; description?: string; apiIds: number[]; scopes: string[]; expiresInDays?: number; quotaRequests?: number; }

export interface Summary { from: string; to: string; total: number; success: number; errors: number; errorRate: number; avgLatencyMs: number; p95LatencyMs: number;
  avgUpstreamLatencyMs: number; rateLimited: number; authFailures: number; policyRejections: number; upstreamFailures: number; }
export interface TimePoint { ts: string; total: number; errors: number; avgLatencyMs: number; maxLatencyMs: number; }
export interface ApiStat { apiId?: number; name?: string; version?: string; total: number; errors: number; avgLatencyMs: number; lastSeen?: string; }
export interface Bucket { label: string; count: number; }
export interface Quota { used: number; limit?: number; windowSeconds: number; enabled: boolean; }
export interface LogView { id: number; ts: string; correlationId: string; apiId?: number; keyId?: number; clientIp?: string; method: string; path: string; status: number;
  latencyMs: number; upstreamLatencyMs?: number; requestBytes: number; outcome: string; reason?: string; }

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export interface SecEvent { id: number; createdAt: string; type: string; severity: Severity; apiId?: number; clientIp?: string; correlationId?: string; description: string;
  occurrences: number; acknowledged: boolean; acknowledgedBy?: number; acknowledgedAt?: string; note?: string; }
export interface AuditEntry { id: number; ts: string; actorId?: number; actorEmail?: string; action: string; resourceType: string; resourceId?: string; outcome: string; detail?: string; correlationId?: string; }
export interface SystemInfo { version: string; jwtIssuer: string; accessTokenSeconds: number; refreshTokenSeconds: number; allowedUpstreamHosts: string[]; allowPrivateUpstreams: boolean;
  maxFailedLogins: number; lockMinutes: number; detection: Record<string, number>; retention: Record<string, number>; }
