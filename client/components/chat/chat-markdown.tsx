"use client";

import { code } from "@streamdown/code";
import { Streamdown } from "streamdown";

import { cn } from "@/lib/utils";

import "./chat-markdown.css";
import "streamdown/styles.css";

const streamdownPlugins = { code };

export function ChatMarkdown({
    content,
    isStreaming = false,
    className,
}: {
    content: string;
    isStreaming?: boolean;
    className?: string;
}) {
    return (
        <div className={cn("chat-markdown-container relative", isStreaming && "chat-markdown-streaming")}>
            <Streamdown
                className={cn("chat-markdown max-w-none text-sm leading-relaxed [word-break:break-word]", className)}
                mode="streaming"
                isAnimating={isStreaming}
                plugins={streamdownPlugins}
                shikiTheme={["github-light", "github-dark"]}
            >
                {content}
            </Streamdown>
        </div>
    );
}