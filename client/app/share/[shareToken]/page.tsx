"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { formatDistanceToNow } from "date-fns";
import { Bot, Check, Copy, ExternalLink, Globe, ShieldAlert, UserRound } from "lucide-react";

import { ChatMarkdown } from "@/components/chat/chat-markdown";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button, buttonVariants } from "@/components/ui/button";
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
import { api, type PublicSharedChat } from "@/lib/api";
import { cn } from "@/lib/utils";

function copyToClipboard(text: string): Promise<boolean> {
    if (typeof window !== "undefined" && navigator?.clipboard?.writeText) {
        return navigator.clipboard.writeText(text).then(() => true, () => false);
    }
    try {
        const textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        const ok = document.execCommand("copy");
        document.body.removeChild(textarea);
        return Promise.resolve(ok);
    } catch {
        return Promise.resolve(false);
    }
}

function CopyAnswerButton({ text }: { text: string }) {
    const [copied, setCopied] = useState(false);

    async function handleCopy() {
        const ok = await copyToClipboard(text);
        if (ok) {
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
            toast.add({ title: "Copied to clipboard", type: "success" });
        }
    }

    return (
        <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs text-muted-foreground"
            onClick={handleCopy}
            aria-label="Copy response"
        >
            {copied ? <Check className="size-3.5 text-emerald-500" /> : <Copy className="size-3.5" />}
            <span className="ml-1">{copied ? "Copied" : "Copy"}</span>
        </Button>
    );
}

export default function SharedChatPage({
    params,
}: {
    params: Promise<{ shareToken: string }>;
}) {
    const { shareToken } = use(params);
    const [data, setData] = useState<PublicSharedChat | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let mounted = true;
        api.getPublicShare(shareToken)
            .then((res) => {
                if (mounted) {
                    setData(res);
                    setLoading(false);
                }
            })
            .catch((err) => {
                if (mounted) {
                    setError(err instanceof Error ? err.message : "Failed to load shared conversation");
                    setLoading(false);
                }
            });
        return () => {
            mounted = false;
        };
    }, [shareToken]);

    if (loading) {
        return (
            <div className="flex min-h-screen flex-col bg-background">
                <header className="border-b bg-card px-6 py-4">
                    <Skeleton className="h-6 w-48" />
                </header>
                <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 p-6">
                    <Skeleton className="h-20 w-full rounded-2xl" />
                    <Skeleton className="h-16 w-2/3 rounded-3xl" />
                    <Skeleton className="ml-auto h-12 w-1/2 rounded-3xl" />
                    <Skeleton className="h-24 w-3/4 rounded-3xl" />
                </div>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6 text-center">
                <div className="rounded-full bg-destructive/10 p-3 text-destructive">
                    <ShieldAlert className="size-8" />
                </div>
                <h1 className="text-xl font-semibold">Shared chat unavailable</h1>
                <p className="max-w-md text-sm text-muted-foreground">
                    This shared conversation does not exist, has expired, or was revoked by its author.
                </p>
                <Link href="/" className={buttonVariants()}>Go to GitBot</Link>
            </div>
        );
    }

    return (
        <div className="flex min-h-screen flex-col bg-background">
            <header className="flex items-center justify-between border-b bg-card px-6 py-4">
                <div className="flex items-center gap-3">
                    <Link href="/" className="flex items-center gap-2 font-semibold">
                        <span className="rounded-lg bg-primary/10 px-2 py-1 text-sm text-primary">
                            GitBot
                        </span>
                    </Link>
                    <span className="text-sm font-medium">{data.title}</span>
                </div>
                <div className="flex items-center gap-3">
                    <Badge variant="outline" className="gap-1 text-xs">
                        <Globe className="size-3" />
                        Read-Only View
                    </Badge>
                    <Link
                        href="/login"
                        className={buttonVariants({ size: "sm", variant: "outline" })}
                    >
                        Sign in
                    </Link>
                </div>
            </header>

            {/* Read-Only Notice Banner */}
            <div className="border-b bg-muted/40 px-6 py-3 text-xs text-muted-foreground">
                <div className="mx-auto flex max-w-3xl items-center justify-between">
                    <span>
                        Shared conversation grounded in <strong>{data.repoFullName}</strong>.
                    </span>
                    <span>
                        Shared{" "}
                        {formatDistanceToNow(new Date(data.sharedAt), {
                            addSuffix: true,
                        })}
                    </span>
                </div>
            </div>

            <ScrollArea className="flex-1">
                <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-8">
                    <MessageGroup>
                        {data.messages.map((message) => {
                            const isUser = message.role === "USER";
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
                                            variant={isUser ? "default" : "ghost"}
                                            align={isUser ? "end" : "start"}
                                            className={cn(!isUser && "max-w-full bg-transparent border-none shadow-none")}
                                        >
                                            <BubbleContent
                                                className={cn(!isUser && "w-full max-w-full px-1 py-1 bg-transparent border-none shadow-none text-foreground")}
                                            >
                                                {isUser ? (
                                                    <span className="whitespace-pre-wrap">
                                                        {message.content}
                                                    </span>
                                                ) : (
                                                    <ChatMarkdown content={message.content} />
                                                )}
                                            </BubbleContent>
                                        </Bubble>

                                        {!isUser && message.citations?.length > 0 && (
                                            <MessageFooter>
                                                <div className="flex flex-wrap gap-1.5 pt-1">
                                                    {message.citations.map((c, i) => {
                                                        const line = c.startLine != null ? `#L${c.startLine}` : "";
                                                        const href = `https://github.com/${data.repoFullName}/blob/main/${c.filePath}${line}`;
                                                        return (
                                                            <Badge
                                                                key={`${c.filePath}-${i}`}
                                                                variant="outline"
                                                                render={
                                                                    <a
                                                                        href={href}
                                                                        target="_blank"
                                                                        rel="noreferrer"
                                                                    />
                                                                }
                                                                className="max-w-full gap-1 font-normal text-xs"
                                                            >
                                                                <span className="truncate">
                                                                    {c.filePath}
                                                                    {c.startLine != null ? `:${c.startLine}` : ""}
                                                                </span>
                                                                <ExternalLink className="size-3 opacity-60" />
                                                            </Badge>
                                                        );
                                                    })}
                                                </div>
                                            </MessageFooter>
                                        )}

                                        {!isUser && (
                                            <div className="mt-1">
                                                <CopyAnswerButton text={message.content} />
                                            </div>
                                        )}
                                    </MessageContent>
                                </Message>
                            );
                        })}
                    </MessageGroup>
                </div>
            </ScrollArea>
        </div>
    );
}
