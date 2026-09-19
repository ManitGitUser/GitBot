export type IndexStatus = "PENDING" | "INDEXING" | "READY" | "FAILED";

export type User = {
    id: string;
    githubId: number;
    githubUsername: string;
    displayName: string;
    avatarUrl: string | null;
};

export type Repository = {
    id: string;
    githubRepoId: number;
    owner: string;
    name: string;
    fullName: string;
    isPrivate: boolean;
    defaultBranch: string;
    language: string | null;
    htmlUrl: string | null;
    description: string | null;
    indexStatus: IndexStatus;
    indexedAt: string | null;
    chunkCount: number;
    filesTotal: number;
    filesProcessed: number;
    errorMessage: string | null;
    indexedCommitSha: string | null;
    latestCommitSha: string | null;
};

export type SyncAllReposResponse = {
    totalRepositories: number;
    newRepositories: number;
    updatedRepositories: number;
    unchangedRepositories: number;
    repositoriesWithNewCommits: number;
};

export type SyncRepoResponse = {
    repoId: string;
    reindexTriggered: boolean;
    message: string;
    currentCommitSha: string;
    indexedCommitSha: string | null;
};

export type IndexStatusResponse = {
    repositoryId: string;
    indexStatus: IndexStatus;
    filesTotal: number;
    filesProcessed: number;
    chunkCount: number;
    indexedAt: string | null;
    errorMessage: string | null;
};

export type MessageStatus = "COMPLETE" | "INTERRUPTED" | "FAILED";

export type ReportReason =
    | "INCORRECT"
    | "IRRELEVANT"
    | "UNSAFE"
    | "CITATION_ISSUE"
    | "OTHER";

export type ChatSession = {
    id: string;
    repositoryId: string;
    title: string;
    createdAt: string;
    parentSessionId?: string | null;
    branchMessageId?: string | null;
    isShared?: boolean;
    shareToken?: string | null;
};

export type Citation = {
    filePath: string;
    startLine: number | null;
    endLine: number | null;
    language: string | null;
    repoFullName?: string | null;
};

export type ChatMessage = {
    id: string;
    role: "USER" | "ASSISTANT";
    status?: MessageStatus;
    content: string;
    citations: Citation[];
    createdAt: string;
};

export type ShareResponse = {
    shareToken: string;
    shareUrl: string;
};

export type PublicSharedChat = {
    title: string;
    repoFullName: string;
    sharedAt: string;
    messages: ChatMessage[];
};

export type PageResponse<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    hasNext: boolean;
    hasPrevious: boolean;
};

export type PagedMessagesResponse = {
    messages: ChatMessage[];
    hasMore: boolean;
    nextCursor: string | null;
};


export class ApiError extends Error {
    status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
    }
}

export function getApiBaseUrl() {
    return process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
}

export function getGithubLoginUrl() {
    return `${getApiBaseUrl()}/oauth2/authorization/github`;
}

function getCookie(name: string): string | null {
    if (typeof document === "undefined") return null;
    const match = document.cookie.match(new RegExp(`(^|;\\s*)(${name})=([^;]*)`));
    return match ? decodeURIComponent(match[3]) : null;
}

let cachedCsrfToken: string | null = null;

export async function getCsrfToken(): Promise<string | null> {
    const cookieToken = getCookie("XSRF-TOKEN");
    if (cookieToken) {
        cachedCsrfToken = cookieToken;
        return cookieToken;
    }
    if (cachedCsrfToken) {
        return cachedCsrfToken;
    }
    try {
        const res = await fetch(`${getApiBaseUrl()}/api/auth/csrf`, {
            credentials: "include",
        });
        if (res.ok) {
            const data = await res.json();
            if (data?.token) {
                cachedCsrfToken = data.token;
                return data.token;
            }
        }
    } catch {
        // ignore fetch failure for optional token prefetch
    }
    return getCookie("XSRF-TOKEN");
}

async function parseError(res: Response): Promise<string> {
    try {
        const data = await res.json();
        return data.message ?? data.error ?? res.statusText;
    } catch {
        return res.statusText || "Request failed";
    }
}

export async function apiFetch<T>(
    path: string,
    init?: RequestInit,
): Promise<T> {
    const method = init?.method?.toUpperCase() || "GET";
    const headers: Record<string, string> = {
        "Content-Type": "application/json",
        ...(init?.headers as Record<string, string> || {}),
    };

    if (["POST", "PUT", "DELETE", "PATCH"].includes(method)) {
        const csrfToken = await getCsrfToken();
        if (csrfToken) {
            headers["X-XSRF-TOKEN"] = csrfToken;
        }
    }

    const res = await fetch(`${getApiBaseUrl()}${path}`, {
        ...init,
        credentials: "include",
        headers,
    });

    if (!res.ok) {
        throw new ApiError(res.status, await parseError(res));
    }

    if (res.status === 204) {
        return undefined as T;
    }

    return res.json() as Promise<T>;
}

export const api = {
    csrf: () => apiFetch<{ token: string; headerName: string; parameterName: string }>("/api/auth/csrf"),
    me: async () => {
        const raw = await apiFetch<User & { avatar_url?: string }>("/api/auth/me");
        return {
            ...raw,
            avatarUrl: raw.avatarUrl || raw.avatar_url || null,
        };
    },
    syncProfile: async () => {
        const raw = await apiFetch<User & { avatar_url?: string }>("/api/auth/sync-profile", {
            method: "POST",
        });
        return {
            ...raw,
            avatarUrl: raw.avatarUrl || raw.avatar_url || null,
        };
    },
    logout: () =>
        apiFetch<void>("/api/auth/logout", {
            method: "POST",
        }),

    listRepos: (page = 0, size = 10, refresh = true) =>
        apiFetch<PageResponse<Repository>>(`/api/repos?page=${page}&size=${size}&refresh=${refresh}`),
    getRepo: (id: string) => apiFetch<Repository>(`/api/repos/${id}`),
    syncAllRepos: () =>
        apiFetch<SyncAllReposResponse>("/api/repos/sync-all", { method: "POST" }),
    startIndex: (id: string) =>
        apiFetch<Repository>(`/api/repos/${id}/index`, { method: "POST" }),
    syncRepo: (id: string) =>
        apiFetch<SyncRepoResponse>(`/api/repos/${id}/sync`, { method: "POST" }),
    indexStatus: (id: string) =>
        apiFetch<IndexStatusResponse>(`/api/repos/${id}/status`),
    createSession: (repositoryId: string, title?: string) =>
        apiFetch<ChatSession>("/api/chat/sessions", {
            method: "POST",
            body: JSON.stringify({ repositoryId, title }),
        }),
    listSessions: (repositoryId: string, page = 0, size = 10) =>
        apiFetch<PageResponse<ChatSession>>(
            `/api/chat/sessions?repositoryId=${encodeURIComponent(repositoryId)}&page=${page}&size=${size}`
        ),
    getMessages: (sessionId: string, before?: string | null, limit = 10) => {
        const params = new URLSearchParams();
        if (before) params.append("before", before);
        params.append("limit", limit.toString());
        return apiFetch<PagedMessagesResponse>(`/api/chat/sessions/${sessionId}?${params.toString()}`);
    },
    deleteSession: (sessionId: string) =>
        apiFetch<void>(`/api/chat/sessions/${sessionId}`, { method: "DELETE" }),
    renameSession: (sessionId: string, title: string) =>
        apiFetch<ChatSession>(`/api/chat/sessions/${sessionId}`, {
            method: "PATCH",
            body: JSON.stringify({ title }),
        }),
    branchSession: (sessionId: string, messageId: string, title?: string) =>
        apiFetch<ChatSession>(`/api/chat/sessions/${sessionId}/branch`, {
            method: "POST",
            body: JSON.stringify({ messageId, title }),
        }),
    createShare: (sessionId: string) =>
        apiFetch<ShareResponse>(`/api/chat/sessions/${sessionId}/share`, {
            method: "POST",
        }),
    revokeShare: (sessionId: string) =>
        apiFetch<void>(`/api/chat/sessions/${sessionId}/share`, {
            method: "DELETE",
        }),
    reportMessage: (sessionId: string, messageId: string, reason: ReportReason, details?: string) =>
        apiFetch<void>(`/api/chat/sessions/${sessionId}/messages/${messageId}/report`, {
            method: "POST",
            body: JSON.stringify({ reason, details }),
        }),
    stopStream: (sessionId: string) =>
        apiFetch<void>(`/api/chat/sessions/${sessionId}/stop`, {
            method: "POST",
        }),
    getPublicShare: (shareToken: string) =>
        apiFetch<PublicSharedChat>(`/api/public/shares/${shareToken}`),
};