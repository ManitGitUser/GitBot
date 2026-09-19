"use client";

import {
    useInfiniteQuery,
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";

import { api, type ChatMessage, type PagedMessagesResponse, type ReportReason } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { streamChatMessage } from "@/lib/stream-chat";
import { toast } from "@/components/ui/toast";

export function useChatSessions(repositoryId: string, enabled = true) {
    return useInfiniteQuery({
        queryKey: queryKeys.chat.sessions(repositoryId),
        queryFn: ({ pageParam = 0 }) => api.listSessions(repositoryId, pageParam, 10),
        initialPageParam: 0,
        getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
        enabled: Boolean(repositoryId) && enabled,
    });
}

export function useChatMessages(sessionId: string | null) {
    const queryClient = useQueryClient();
    const [isLoadingEarlier, setIsLoadingEarlier] = useState(false);

    const query = useQuery({
        queryKey: queryKeys.chat.messages(sessionId ?? ""),
        queryFn: () => api.getMessages(sessionId!, null, 10),
        enabled: Boolean(sessionId),
        staleTime: 60_000,
    });

    const loadEarlier = useCallback(async () => {
        if (!sessionId || !query.data?.hasMore || !query.data.nextCursor || isLoadingEarlier) {
            return;
        }
        setIsLoadingEarlier(true);
        try {
            const older = await api.getMessages(sessionId, query.data.nextCursor, 10);
            queryClient.setQueryData<PagedMessagesResponse>(
                queryKeys.chat.messages(sessionId),
                (prev) => {
                    if (!prev) return older;
                    return {
                        messages: [...older.messages, ...prev.messages],
                        hasMore: older.hasMore,
                        nextCursor: older.nextCursor,
                    };
                }
            );
        } catch (error) {
            toast.add({
                title: "Could not load earlier messages",
                description: error instanceof Error ? error.message : "Unknown error",
                type: "error",
            });
        } finally {
            setIsLoadingEarlier(false);
        }
    }, [sessionId, query.data, isLoadingEarlier, queryClient]);

    return {
        ...query,
        messages: query.data?.messages ?? [],
        hasMore: query.data?.hasMore ?? false,
        nextCursor: query.data?.nextCursor ?? null,
        loadEarlier,
        isLoadingEarlier,
    };
}

export function useCreateChatSession(repositoryId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (title?: string) => api.createSession(repositoryId, title),
        onSuccess: (session) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.chat.sessions(repositoryId),
            });
            queryClient.setQueryData<PagedMessagesResponse>(queryKeys.chat.messages(session.id), {
                messages: [],
                hasMore: false,
                nextCursor: null,
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not create chat",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useDeleteChatSession(repositoryId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (sessionId: string) => api.deleteSession(sessionId),
        onSuccess: (_, sessionId) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.chat.sessions(repositoryId),
            });
            queryClient.removeQueries({
                queryKey: queryKeys.chat.messages(sessionId),
            });
            toast.add({
                title: "Chat deleted",
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not delete chat",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useRenameChatSession(repositoryId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ sessionId, title }: { sessionId: string; title: string }) =>
            api.renameSession(sessionId, title),
        onSuccess: () => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.chat.sessions(repositoryId),
            });
            toast.add({
                title: "Chat renamed",
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not rename chat",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useBranchChatSession(repositoryId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            sessionId,
            messageId,
            title,
        }: {
            sessionId: string;
            messageId: string;
            title?: string;
        }) => api.branchSession(sessionId, messageId, title),
        onSuccess: (branch) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.chat.sessions(repositoryId),
            });
            toast.add({
                title: "Branch created",
                description: `Created branch “${branch.title}”`,
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not branch chat",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useShareChatSession() {
    return useMutation({
        mutationFn: (sessionId: string) => api.createShare(sessionId),
        onError: (error: Error) => {
            toast.add({
                title: "Could not share chat",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useRevokeChatSession() {
    return useMutation({
        mutationFn: (sessionId: string) => api.revokeShare(sessionId),
        onError: (error: Error) => {
            toast.add({
                title: "Could not revoke share",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useReportMessage() {
    return useMutation({
        mutationFn: ({
            sessionId,
            messageId,
            reason,
            details,
        }: {
            sessionId: string;
            messageId: string;
            reason: ReportReason;
            details?: string;
        }) => api.reportMessage(sessionId, messageId, reason, details),
        onSuccess: () => {
            toast.add({
                title: "Feedback submitted",
                description: "Thank you for helping improve GitBot.",
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not report message",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useStreamChat(sessionId: string | null) {
    const queryClient = useQueryClient();
    const [streaming, setStreaming] = useState(false);
    const [streamText, setStreamText] = useState("");
    const [retryingMessageId, setRetryingMessageId] = useState<string | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const send = useCallback(
        async (content: string) => {
            if (!sessionId || !content.trim() || streaming) return;

            abortRef.current?.abort();
            const controller = new AbortController();
            abortRef.current = controller;

            const optimisticId = `temp-${Date.now()}`;
            const optimistic: ChatMessage = {
                id: optimisticId,
                role: "USER",
                status: "COMPLETE",
                content: content.trim(),
                citations: [],
                createdAt: new Date().toISOString(),
            };

            queryClient.setQueryData<PagedMessagesResponse>(
                queryKeys.chat.messages(sessionId),
                (prev) => {
                    const base = prev ?? { messages: [], hasMore: false, nextCursor: null };
                    return {
                        ...base,
                        messages: [...base.messages, optimistic],
                    };
                }
            );

            setStreaming(true);
            setRetryingMessageId(null);
            setStreamText("");

            try {
                await streamChatMessage(sessionId, { content: content.trim() }, {
                    signal: controller.signal,
                    onUserMessage: (message) => {
                        queryClient.setQueryData<PagedMessagesResponse>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => {
                                const base = prev ?? { messages: [], hasMore: false, nextCursor: null };
                                return {
                                    ...base,
                                    messages: [
                                        ...base.messages.filter((m) => m.id !== optimisticId),
                                        message,
                                    ],
                                };
                            }
                        );
                    },
                    onToken: (token) => {
                        setStreamText((prev) => prev + token);
                    },
                    onAssistantMessage: (message) => {
                        queryClient.setQueryData<PagedMessagesResponse>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => {
                                const base = prev ?? { messages: [], hasMore: false, nextCursor: null };
                                const exists = base.messages.some((m) => m.id === message.id);
                                return {
                                    ...base,
                                    messages: exists
                                        ? base.messages.map((m) => (m.id === message.id ? message : m))
                                        : [...base.messages, message],
                                };
                            }
                        );
                        setStreamText("");
                    },
                    onError: (err) => {
                        toast.add({
                            title: "Stream error",
                            description: err.message,
                            type: "error",
                        });
                        void queryClient.invalidateQueries({
                            queryKey: queryKeys.chat.messages(sessionId),
                        });
                    },
                });
            } catch (err) {
                setStreamText("");
                if ((err as Error).name === "AbortError") return;
                toast.add({
                    title: "Message failed",
                    description: err instanceof Error ? err.message : "Unknown error",
                    type: "error",
                });
                void queryClient.invalidateQueries({
                    queryKey: queryKeys.chat.messages(sessionId),
                });
            } finally {
                setStreaming(false);
            }
        },
        [sessionId, streaming, queryClient]
    );

    const retry = useCallback(
        async (messageId: string) => {
            if (!sessionId || streaming) return;

            abortRef.current?.abort();
            const controller = new AbortController();
            abortRef.current = controller;

            setStreaming(true);
            setRetryingMessageId(messageId);
            setStreamText("");

            try {
                await streamChatMessage(sessionId, { retryMessageId: messageId }, {
                    signal: controller.signal,
                    onToken: (token) => {
                        setStreamText((prev) => prev + token);
                    },
                    onAssistantMessage: (message) => {
                        queryClient.setQueryData<PagedMessagesResponse>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => {
                                const base = prev ?? { messages: [], hasMore: false, nextCursor: null };
                                const exists = base.messages.some((m) => m.id === message.id);
                                return {
                                    ...base,
                                    messages: exists
                                        ? base.messages.map((m) => (m.id === message.id ? message : m))
                                        : [...base.messages, message],
                                };
                            }
                        );
                        setStreamText("");
                        setRetryingMessageId(null);
                    },
                    onError: (err) => {
                        toast.add({
                            title: "Retry error",
                            description: err.message,
                            type: "error",
                        });
                        void queryClient.invalidateQueries({
                            queryKey: queryKeys.chat.messages(sessionId),
                        });
                    },
                });
            } catch (err) {
                setStreamText("");
                if ((err as Error).name === "AbortError") return;
                toast.add({
                    title: "Retry failed",
                    description: err instanceof Error ? err.message : "Unknown error",
                    type: "error",
                });
                void queryClient.invalidateQueries({
                    queryKey: queryKeys.chat.messages(sessionId),
                });
            } finally {
                setStreaming(false);
                setRetryingMessageId(null);
            }
        },
        [sessionId, streaming, queryClient]
    );

    const stop = useCallback(() => {
        abortRef.current?.abort();
        setStreaming(false);
        setStreamText("");
        setRetryingMessageId(null);
        if (sessionId) {
            void api.stopStream(sessionId)
                .catch(() => {})
                .finally(() => {
                    void queryClient.invalidateQueries({
                        queryKey: queryKeys.chat.messages(sessionId),
                    });
                });
        }
    }, [sessionId, queryClient]);

    return { send, retry, stop, streaming, streamText, retryingMessageId };
}