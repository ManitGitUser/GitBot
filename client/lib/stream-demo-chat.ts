import { getApiBaseUrl, ApiError, type ChatMessage } from "@/lib/api";

export type StreamDemoChatHandlers = {
    onDemoToken?: (token: string) => void;
    onUserMessage?: (message: ChatMessage) => void;
    onToken?: (token: string) => void;
    onAssistantMessage?: (message: ChatMessage) => void;
    onDone?: () => void;
    onError?: (error: Error) => void;
    signal?: AbortSignal;
};

export type DemoChatPayload = {
    message: string;
    history: { role: "USER" | "ASSISTANT"; content: string }[];
    demoToken?: string | null;
};

export async function streamDemoChat(
    payload: DemoChatPayload,
    handlers: StreamDemoChatHandlers = {}
): Promise<void> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const url = `${getApiBaseUrl()}/api/demo/chat`;

    const res = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
        signal: handlers.signal,
    });

    const headerDemoToken = res.headers.get("X-Demo-Token");
    if (headerDemoToken) {
        handlers.onDemoToken?.(headerDemoToken);
    }

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
        throw new Error("No response body for demo SSE stream");
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        // Normalize CRLF to LF so SSE event boundaries and lines split consistently
        buffer = buffer.replace(/\r\n/g, "\n");
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
                    const rawLine = line.startsWith("data: ") ? line.slice(6) : line.slice(5);
                    dataLines.push(rawLine);
                }
            }

            const data = dataLines.join("\n");
            if (!data && (event !== "token" || dataLines.length === 0)) continue;

            try {
                if (event === "demo_token") {
                    handlers.onDemoToken?.(data.trim());
                } else if (event === "token") {
                    let tokenText = data;
                    if (!data && dataLines.length > 0) {
                        tokenText = " ";
                    } else {
                        try {
                            const parsed = JSON.parse(data);
                            if (typeof parsed === "string") {
                                tokenText = parsed;
                            }
                        } catch {
                            // Plain text
                        }
                    }
                    handlers.onToken?.(tokenText);
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
                    err instanceof Error ? err : new Error("Failed to parse demo SSE event")
                );
            }
        }
    }

    handlers.onDone?.();
}
