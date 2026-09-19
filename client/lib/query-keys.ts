export const queryKeys = {
    auth: {
        all: ["auth"] as const,
        me: () => [...queryKeys.auth.all, "me"] as const,
    },
    repos: {
        all: ["repos"] as const,
        list: (page = 0, size = 10) => [...queryKeys.repos.all, "list", { page, size }] as const,
        detail: (id: string) => [...queryKeys.repos.all, "detail", id] as const,
        status: (id: string) => [...queryKeys.repos.all, "status", id] as const,
    },
    chat: {
        all: ["chat"] as const,
        sessions: (repositoryId: string, page = 0, size = 10) =>
            [...queryKeys.chat.all, "sessions", repositoryId, { page, size }] as const,
        messages: (sessionId: string) =>
            [...queryKeys.chat.all, "messages", sessionId] as const,
    },
};