"use client";

import { AlertCircle, Bot, Check, Copy, Flag, GitFork, RotateCcw, UserRound } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ChatMarkdown } from "@/components/chat/chat-markdown";
import { CitationChips } from "@/components/chat/citation-chips";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button } from "@/components/ui/button";
import {
    Message,
    MessageAvatar,
    MessageContent,
    MessageFooter,
    MessageGroup,
} from "@/components/ui/message";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import type { ChatMessage, Repository } from "@/lib/api";
import { cn } from "@/lib/utils";

function copyToClipboard(text: string): Promise<boolean> {
    if (typeof window !== "undefined" && navigator?.clipboard?.writeText) {
        return navigator.clipboard.writeText(text).then(
            () => true,
            () => false
        );
    }
    try {
        const textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        const success = document.execCommand("copy");
        document.body.removeChild(textarea);
        return Promise.resolve(success);
    } catch {
        return Promise.resolve(false);
    }
}

function AssistantMessageToolbar({
    message,
    streaming,
    onRetry,
    onBranch,
    onReport,
}: {
    message: ChatMessage;
    streaming?: boolean;
    onRetry?: (messageId: string) => void;
    onBranch?: (message: ChatMessage) => void;
    onReport?: (message: ChatMessage) => void;
}) {
    const [copied, setCopied] = useState(false);

    async function handleCopy() {
        const success = await copyToClipboard(message.content);
        if (success) {
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
            toast.add({
                title: "Copied to clipboard",
                type: "success",
            });
        } else {
            toast.add({
                title: "Failed to copy",
                description: "Clipboard permissions denied or unsupported.",
                type: "error",
            });
        }
    }

    return (
        <div className="mt-2 flex flex-wrap items-center gap-1 text-muted-foreground">
            <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={handleCopy}
                aria-label="Copy response"
            >
                {copied ? <Check className="size-3.5 text-emerald-500" /> : <Copy className="size-3.5" />}
                <span className="ml-1">{copied ? "Copied" : "Copy"}</span>
            </Button>

            {onRetry && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs"
                    disabled={streaming}
                    onClick={() => onRetry(message.id)}
                    aria-label="Retry response"
                >
                    <RotateCcw className="size-3.5" />
                    <span className="ml-1">Retry</span>
                </Button>
            )}

            {onBranch && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs"
                    disabled={streaming}
                    onClick={() => onBranch(message)}
                    aria-label="Branch conversation from this message"
                >
                    <GitFork className="size-3.5" />
                    <span className="ml-1">Branch</span>
                </Button>
            )}

            {onReport && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs hover:text-amber-500"
                    disabled={streaming}
                    onClick={() => onReport(message)}
                    aria-label="Report AI response"
                >
                    <Flag className="size-3.5" />
                    <span className="ml-1">Report</span>
                </Button>
            )}
        </div>
    );
}

export function ChatMessages({
    repo,
    messages,
    streamText,
    isLoading,
    streaming,
    onRetry,
    onBranch,
    onReport,
}: {
    repo: Repository;
    messages: ChatMessage[];
    streamText?: string;
    isLoading?: boolean;
    streaming?: boolean;
    onRetry?: (messageId: string) => void;
    onBranch?: (message: ChatMessage) => void;
    onReport?: (message: ChatMessage) => void;
}) {
    const bottomRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, streamText]);

    if (isLoading) {
        return (
            <div className="flex flex-1 flex-col gap-4 p-6">
                <Skeleton className="h-16 w-2/3 rounded-3xl" />
                <Skeleton className="ml-auto h-12 w-1/2 rounded-3xl" />
                <Skeleton className="h-24 w-3/4 rounded-3xl" />
            </div>
        );
    }

    return (
        <ScrollArea className="flex-1">
            <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-6">
                {messages.length === 0 && !streamText && (
                    <div className="rounded-2xl border border-dashed bg-muted/30 px-6 py-10 text-center">
                        <p className="font-medium">Ask anything about this codebase</p>
                        <p className="mt-1 text-sm text-muted-foreground">
                            Try “Where is authentication handled?” or “Explain the repository indexing flow.”
                        </p>
                    </div>
                )}

                <MessageGroup>
                    {messages.map((message) => {
                        const isUser = message.role === "USER";
                        const isInterrupted = message.status === "INTERRUPTED";
                        const isFailed = message.status === "FAILED";

                        return (
                            <Message key={message.id} align={isUser ? "end" : "start"}>
                                <MessageAvatar>
                                    <Avatar className="size-8">
                                        <AvatarFallback
                                            className={cn(
                                                isUser
                                                    ? "bg-primary text-primary-foreground"
                                                    : "bg-muted"
                                            )}
                                        >
                                            {isUser ? (
                                                <UserRound className="size-4" />
                                            ) : (
                                                <Bot className="size-4" />
                                            )}
                                        </AvatarFallback>
                                    </Avatar>
                                </MessageAvatar>
                                <MessageContent>
                                    <Bubble
                                        variant={isUser ? "default" : "muted"}
                                        align={isUser ? "end" : "start"}
                                        className={cn(!isUser && "max-w-full")}
                                    >
                                        <BubbleContent className={cn(!isUser && "w-full max-w-full px-4 py-3")}>
                                            {isUser ? (
                                                <span className="whitespace-pre-wrap">{message.content}</span>
                                            ) : (
                                                <>
                                                    <ChatMarkdown content={message.content} />
                                                    {(isInterrupted || isFailed) && (
                                                        <div className="mt-2 flex items-center gap-2">
                                                            <Badge
                                                                variant={isFailed ? "destructive" : "outline"}
                                                                className="text-xs"
                                                            >
                                                                <AlertCircle className="mr-1 size-3" />
                                                                {isFailed ? "Generation Failed" : "Interrupted"}
                                                            </Badge>
                                                        </div>
                                                    )}
                                                </>
                                            )}
                                        </BubbleContent>
                                    </Bubble>

                                    {!isUser && message.citations?.length > 0 && (
                                        <MessageFooter>
                                            <CitationChips repo={repo} citations={message.citations} />
                                        </MessageFooter>
                                    )}

                                    {!isUser && (
                                        <AssistantMessageToolbar
                                            message={message}
                                            streaming={streaming}
                                            onRetry={onRetry}
                                            onBranch={onBranch}
                                            onReport={onReport}
                                        />
                                    )}
                                </MessageContent>
                            </Message>
                        );
                    })}

                    {streamText && (
                        <Message align="start">
                            <MessageAvatar>
                                <Avatar className="size-8">
                                    <AvatarFallback className="bg-muted">
                                        <Bot className="size-4" />
                                    </AvatarFallback>
                                </Avatar>
                            </MessageAvatar>
                            <MessageContent>
                                <Bubble variant="muted" align="start" className="max-w-full">
                                    <BubbleContent className="w-full max-w-full px-4 py-3">
                                        <ChatMarkdown content={streamText} isStreaming />
                                        <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse rounded-sm bg-foreground/50 align-middle" />
                                    </BubbleContent>
                                </Bubble>
                            </MessageContent>
                        </Message>
                    )}
                </MessageGroup>
                <div ref={bottomRef} />
            </div>
        </ScrollArea>
    );
}