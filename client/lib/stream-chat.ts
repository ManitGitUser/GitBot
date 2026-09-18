import { getApiBaseUrl, getCsrfToken, ApiError, type ChatMessage } from "@/lib/api";

export type StreamChatHandlers = {
    onUserMessage?: (message: ChatMessage) => void;
    onToken?: (token: string) => void;
    onAssistantMessage?: (message: ChatMessage) => void;
    onDone?: () => void;
    onError?: (error: Error) => void;
    signal?: AbortSignal;
};

export type StreamChatPayload =
    | { content: string; retryMessageId?: never }
    | { retryMessageId: string; content?: never };

export async function streamChatMessage(
    sessionId: string,
    payload: string | StreamChatPayload,
    handlers: StreamChatHandlers = {}
): Promise<void> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const csrfToken = await getCsrfToken();
    if (csrfToken) {
        headers["X-XSRF-TOKEN"] = csrfToken;
    }

    const isRetry = typeof payload === "object" && Boolean(payload.retryMessageId);
    const url = isRetry
        ? `${getApiBaseUrl()}/api/chat/sessions/${sessionId}/retry`
        : `${getApiBaseUrl()}/api/chat/sessions/${sessionId}/messages`;

    const body = isRetry
        ? JSON.stringify({ messageId: (payload as { retryMessageId: string }).retryMessageId })
        : JSON.stringify({ content: typeof payload === "string" ? payload : payload.content });

    const res = await fetch(url, {
        method: "POST",
        credentials: "include",
        headers,
        body,
        signal: handlers.signal,
    });

    if (!res.ok) {
        let message = res.statusText;
        try {
            const data = await res.json();
            message = data.message ?? data.error ?? message;
        } catch {
            // ignore
        }
        throw new ApiError(res.status, message);
    }

    if (!res.body) {
        throw new Error("No response body for SSE stream");
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() ?? "";

        for (const part of parts) {
            if (!part.trim()) continue;

            const lines = part.split("\n");
            let event = "message";
            const dataLines: string[] = [];

            for (const line of lines) {
                if (line.startsWith("event:")) {
                    event = line.slice(6).trim();
                } else if (line.startsWith("data:")) {
                    dataLines.push(line.slice(5).trimStart());
                }
            }

            const data = dataLines.join("\n");
            if (!data) continue;

            try {
                if (event === "token") {
                    handlers.onToken?.(JSON.parse(data) as string);
                } else if (event === "user_message") {
                    handlers.onUserMessage?.(JSON.parse(data) as ChatMessage);
                } else if (event === "assistant_message") {
                    handlers.onAssistantMessage?.(JSON.parse(data) as ChatMessage);
                } else if (event === "done") {
                    handlers.onDone?.();
                } else if (event === "error") {
                    handlers.onError?.(new Error(data));
                }
            } catch (err) {
                handlers.onError?.(
                    err instanceof Error ? err : new Error("Failed to parse SSE event")
                );
            }
        }
    }

    handlers.onDone?.();
}