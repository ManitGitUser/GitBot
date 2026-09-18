"use client";

import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";

import { api, type ChatMessage, type ReportReason } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { streamChatMessage } from "@/lib/stream-chat";
import { toast } from "@/components/ui/toast";

export function useChatSessions(repositoryId: string, enabled = true) {
    return useQuery({
        queryKey: queryKeys.chat.sessions(repositoryId),
        queryFn: () => api.listSessions(repositoryId),
        enabled: Boolean(repositoryId) && enabled,
    });
}

export function useChatMessages(sessionId: string | null) {
    return useQuery({
        queryKey: queryKeys.chat.messages(sessionId ?? ""),
        queryFn: () => api.getMessages(sessionId!),
        enabled: Boolean(sessionId),
    });
}

export function useCreateChatSession(repositoryId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (title?: string) => api.createSession(repositoryId, title),
        onSuccess: (session) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.chat.sessions(repositoryId),
            });
            queryClient.setQueryData(queryKeys.chat.messages(session.id), []);
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

            queryClient.setQueryData<ChatMessage[]>(
                queryKeys.chat.messages(sessionId),
                (prev) => [...(prev ?? []), optimistic]
            );

            setStreaming(true);
            setStreamText("");

            try {
                await streamChatMessage(sessionId, { content: content.trim() }, {
                    signal: controller.signal,
                    onUserMessage: (message) => {
                        queryClient.setQueryData<ChatMessage[]>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => [
                                ...(prev ?? []).filter((m) => m.id !== optimisticId),
                                message,
                            ]
                        );
                    },
                    onToken: (token) => {
                        setStreamText((prev) => prev + token);
                    },
                    onAssistantMessage: (message) => {
                        queryClient.setQueryData<ChatMessage[]>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => {
                                const current = prev ?? [];
                                const exists = current.some((m) => m.id === message.id);
                                if (exists) {
                                    return current.map((m) => (m.id === message.id ? message : m));
                                }
                                return [...current, message];
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
                if ((err as Error).name === "AbortError") return;
                toast.add({
                    title: "Message failed",
                    description: err instanceof Error ? err.message : "Unknown error",
                    type: "error",
                });
                void queryClient.invalidateQueries({
                    queryKey: queryKeys.chat.messages(sessionId),
                });
                setStreamText("");
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
            setStreamText("");

            try {
                await streamChatMessage(sessionId, { retryMessageId: messageId }, {
                    signal: controller.signal,
                    onToken: (token) => {
                        setStreamText((prev) => prev + token);
                    },
                    onAssistantMessage: (message) => {
                        queryClient.setQueryData<ChatMessage[]>(
                            queryKeys.chat.messages(sessionId),
                            (prev) => {
                                const current = prev ?? [];
                                const exists = current.some((m) => m.id === message.id);
                                if (exists) {
                                    return current.map((m) => (m.id === message.id ? message : m));
                                }
                                return [...current, message];
                            }
                        );
                        setStreamText("");
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
                if ((err as Error).name === "AbortError") return;
                toast.add({
                    title: "Retry failed",
                    description: err instanceof Error ? err.message : "Unknown error",
                    type: "error",
                });
                void queryClient.invalidateQueries({
                    queryKey: queryKeys.chat.messages(sessionId),
                });
                setStreamText("");
            } finally {
                setStreaming(false);
            }
        },
        [sessionId, streaming, queryClient]
    );

    const stop = useCallback(() => {
        abortRef.current?.abort();
        if (sessionId) {
            void api.stopStream(sessionId).catch(() => {});
        }
        setStreaming(false);
    }, [sessionId]);

    return { send, retry, stop, streaming, streamText };
}