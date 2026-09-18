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

export type ChatSession = {
    id: string;
    repositoryId: string;
    title: string;
    createdAt: string;
};

export type Citation = {
    filePath: string;
    startLine: number | null;
    endLine: number | null;
    language: string | null;
};

export type ChatMessage = {
    id: string;
    role: "USER" | "ASSISTANT";
    content: string;
    citations: Citation[];
    createdAt: string;
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
    me: () => apiFetch<User>("/api/auth/me"),
    logout: () =>
        apiFetch<void>("/api/auth/logout", {
            method: "POST",
        }),

    listRepos: (refresh = true) =>
        apiFetch<Repository[]>(`/api/repos?refresh=${refresh}`),
    getRepo: (id: string) => apiFetch<Repository>(`/api/repos/${id}`),
    startIndex: (id: string) =>
        apiFetch<Repository>(`/api/repos/${id}/index`, { method: "POST" }),
    indexStatus: (id: string) =>
        apiFetch<IndexStatusResponse>(`/api/repos/${id}/status`),
    createSession: (repositoryId: string, title?: string) =>
        apiFetch<ChatSession>("/api/chat/sessions", {
            method: "POST",
            body: JSON.stringify({ repositoryId, title }),
        }),
    listSessions: (repositoryId: string) =>
        apiFetch<ChatSession[]>(
            `/api/chat/sessions?repositoryId=${encodeURIComponent(repositoryId)}`
        ),
    getMessages: (sessionId: string) =>
        apiFetch<ChatMessage[]>(`/api/chat/sessions/${sessionId}`),
};