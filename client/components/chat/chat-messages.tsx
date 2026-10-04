"use client";

import {
    AlertCircle,
    Check,
    ChevronDown,
    Copy,
    Flag,
    GitFork,
    Plus,
    RotateCcw,
    Share2,
    Sparkles,
    StopCircle,
    UserRound,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ChatMarkdown } from "@/components/chat/chat-markdown";
import { CitationChips } from "@/components/chat/citation-chips";
import { GitBotIcon } from "@/components/icons/gitbot-icon";
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
import { useCurrentUser } from "@/hooks/use-auth";
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
    ready = true,
    onRetry,
    onBranch,
    onReport,
    onShare,
}: {
    message: ChatMessage;
    streaming?: boolean;
    ready?: boolean;
    onRetry?: (messageId: string) => void;
    onBranch?: (message: ChatMessage) => void;
    onReport?: (message: ChatMessage) => void;
    onShare?: () => void;
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
                description: "Clipboard access was denied.",
                type: "error",
            });
        }
    }

    return (
        <div
            className={cn(
                "mt-2 flex min-h-7 flex-wrap items-center gap-1 text-muted-foreground",
                "opacity-100 sm:opacity-0 sm:group-hover/msg:opacity-100 focus-within:opacity-100 transition-opacity duration-150"
            )}
            role="toolbar"
            aria-label="Assistant message actions"
        >
            <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={handleCopy}
                aria-label={copied ? "Copied to clipboard" : "Copy response to clipboard"}
            >
                {copied ? <Check className="size-3.5 text-emerald-500" /> : <Copy className="size-3.5" />}
                <span className="ml-1">{copied ? "Copied" : "Copy"}</span>
            </Button>

            {onRetry && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs"
                    disabled={streaming || !ready}
                    onClick={() => onRetry(message.id)}
                    aria-label="Retry generating this response"
                    title={streaming ? "Wait for generation to finish" : !ready ? "Repository is not indexed. Index it again to retry." : undefined}
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
                    disabled={streaming || !ready}
                    onClick={() => onBranch(message)}
                    aria-label="Branch new conversation from this message"
                    title={streaming ? "Wait for generation to finish" : !ready ? "Repository is not indexed. Index it again to branch." : undefined}
                >
                    <GitFork className="size-3.5" />
                    <span className="ml-1">Branch</span>
                </Button>
            )}

            {onShare && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs"
                    disabled={streaming}
                    onClick={onShare}
                    aria-label="Share this chat conversation"
                >
                    <Share2 className="size-3.5" />
                    <span className="ml-1">Share</span>
                </Button>
            )}

            {onReport && (
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs hover:text-amber-500 dark:hover:text-amber-400"
                    disabled={streaming}
                    onClick={() => onReport(message)}
                    aria-label="Report incorrect or unsafe response"
                    title={streaming ? "Wait for generation to finish" : undefined}
                >
                    <Flag className="size-3.5" />
                    <span className="ml-1">Report</span>
                </Button>
            )}
        </div>
    );
}

function UserMessageToolbar({ message }: { message: ChatMessage }) {
    const [copied, setCopied] = useState(false);

    async function handleCopy() {
        const success = await copyToClipboard(message.content);
        if (success) {
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
            toast.add({
                title: "Prompt copied to clipboard",
                type: "success",
            });
        } else {
            toast.add({
                title: "Failed to copy",
                description: "Clipboard access was denied.",
                type: "error",
            });
        }
    }

    return (
        <div
            className={cn(
                "mt-1 flex min-h-7 flex-wrap items-center justify-end gap-1 text-muted-foreground",
                "opacity-100 sm:opacity-0 sm:group-hover/msg:opacity-100 focus-within:opacity-100 transition-opacity duration-150"
            )}
            role="toolbar"
            aria-label="User message actions"
        >
            <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={handleCopy}
                aria-label={copied ? "Copied prompt to clipboard" : "Copy prompt"}
            >
                {copied ? <Check className="size-3.5 text-emerald-500" /> : <Copy className="size-3.5" />}
                <span className="ml-1">{copied ? "Copied" : "Copy"}</span>
            </Button>
        </div>
    );
}

export function ChatMessages({
    repo,
    messages,
    streamText,
    retryingMessageId,
    isLoading,
    hasMore,
    onLoadEarlier,
    isLoadingEarlier,
    streaming,
    onRetry,
    onBranch,
    onReport,
    onShare,
    hasActiveSession = true,
    onNewChat,
    ready = true,
}: {
    repo: Repository;
    messages: ChatMessage[];
    streamText?: string;
    retryingMessageId?: string | null;
    isLoading?: boolean;
    hasMore?: boolean;
    onLoadEarlier?: () => void;
    isLoadingEarlier?: boolean;
    streaming?: boolean;
    onRetry?: (messageId: string) => void;
    onBranch?: (message: ChatMessage) => void;
    onReport?: (message: ChatMessage) => void;
    onShare?: () => void;
    hasActiveSession?: boolean;
    onNewChat?: () => void;
    ready?: boolean;
}) {
    const { data: user } = useCurrentUser();
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const [isScrolledUp, setIsScrolledUp] = useState(false);
    const isScrolledUpRef = useRef(false);
    const isProgrammaticScrollRef = useRef(false);
    const lastUserUpScrollTimeRef = useRef(0);
    const lastScrollTopRef = useRef(0);
    const prevMessageCountRef = useRef(messages.length);

    // Attach scroll, wheel, touch, and key listeners to detect intentional user scrolling
    useEffect(() => {
        const container = scrollContainerRef.current;
        if (!container) return;

        const viewport = container.querySelector(
            '[data-slot="scroll-area-viewport"]'
        ) as HTMLElement | null;
        if (!viewport) return;

        lastScrollTopRef.current = viewport.scrollTop;

        const handleScroll = () => {
            const currentScrollTop = viewport.scrollTop;
            const prevScrollTop = lastScrollTopRef.current;
            lastScrollTopRef.current = currentScrollTop;

            if (isProgrammaticScrollRef.current) {
                isProgrammaticScrollRef.current = false;
                return;
            }

            const distanceFromBottom =
                viewport.scrollHeight - currentScrollTop - viewport.clientHeight;

            // If user's scroll position moved upwards (e.g. trackpad swipe, dragging scrollbar, key navigation)
            if (currentScrollTop < prevScrollTop && distanceFromBottom > 15) {
                isScrolledUpRef.current = true;
                setIsScrolledUp(true);
                lastUserUpScrollTimeRef.current = Date.now();
                return;
            }

            // User is at/near the bottom (within 20px)
            if (distanceFromBottom <= 20) {
                // Only clear if the user wasn't actively scrolling up in the last 400ms
                if (Date.now() - lastUserUpScrollTimeRef.current > 400) {
                    if (isScrolledUpRef.current) {
                        isScrolledUpRef.current = false;
                        setIsScrolledUp(false);
                    }
                }
            } else if (distanceFromBottom > 35) {
                if (!isScrolledUpRef.current) {
                    isScrolledUpRef.current = true;
                    setIsScrolledUp(true);
                }
            }
        };

        const handleWheel = (e: WheelEvent) => {
            if (e.deltaY < 0) {
                // Upward wheel event: user intentionally scrolled up
                isScrolledUpRef.current = true;
                setIsScrolledUp(true);
                lastUserUpScrollTimeRef.current = Date.now();
            } else if (e.deltaY > 0) {
                // Downward wheel event: check if user reached bottom
                const distanceFromBottom =
                    viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight;
                if (distanceFromBottom <= 20) {
                    isScrolledUpRef.current = false;
                    setIsScrolledUp(false);
                }
            }
        };

        let touchStartY = 0;
        const handleTouchStart = (e: TouchEvent) => {
            if (e.touches[0]) {
                touchStartY = e.touches[0].clientY;
            }
        };

        const handleTouchMove = (e: TouchEvent) => {
            if (e.touches[0]) {
                const currentY = e.touches[0].clientY;
                // Swiping downwards on screen (finger moving down) scrolls content UP
                if (currentY > touchStartY + 5) {
                    isScrolledUpRef.current = true;
                    setIsScrolledUp(true);
                    lastUserUpScrollTimeRef.current = Date.now();
                }
            }
        };

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "ArrowUp" || e.key === "PageUp" || e.key === "Home") {
                isScrolledUpRef.current = true;
                setIsScrolledUp(true);
                lastUserUpScrollTimeRef.current = Date.now();
            }
        };

        container.addEventListener("wheel", handleWheel, { passive: true });
        container.addEventListener("touchstart", handleTouchStart, { passive: true });
        container.addEventListener("touchmove", handleTouchMove, { passive: true });
        container.addEventListener("keydown", handleKeyDown, { passive: true });
        viewport.addEventListener("scroll", handleScroll, { passive: true });

        return () => {
            container.removeEventListener("wheel", handleWheel);
            container.removeEventListener("touchstart", handleTouchStart);
            container.removeEventListener("touchmove", handleTouchMove);
            container.removeEventListener("keydown", handleKeyDown);
            viewport.removeEventListener("scroll", handleScroll);
        };
    }, []);

    const handleLoadEarlier = async () => {
        if (!onLoadEarlier || isLoadingEarlier) return;
        const container = scrollContainerRef.current;
        const viewport = container?.querySelector(
            '[data-slot="scroll-area-viewport"]'
        ) as HTMLElement | null;

        const prevScrollHeight = viewport?.scrollHeight ?? 0;
        const prevScrollTop = viewport?.scrollTop ?? 0;

        await onLoadEarlier();

        requestAnimationFrame(() => {
            if (viewport) {
                const newScrollHeight = viewport.scrollHeight;
                const delta = newScrollHeight - prevScrollHeight;
                viewport.scrollTop = prevScrollTop + delta;
                lastScrollTopRef.current = viewport.scrollTop;
            }
        });
    };

    // When the user submits a new prompt, take them to bottom initially and reset scroll intent
    useEffect(() => {
        const lastMsg = messages[messages.length - 1];
        if (messages.length > prevMessageCountRef.current && lastMsg?.role === "USER") {
            isScrolledUpRef.current = false;
            setIsScrolledUp(false);
            lastUserUpScrollTimeRef.current = 0;
            const container = scrollContainerRef.current;
            const viewport = container?.querySelector(
                '[data-slot="scroll-area-viewport"]'
            ) as HTMLElement | null;
            if (viewport) {
                isProgrammaticScrollRef.current = true;
                viewport.scrollTo({
                    top: viewport.scrollHeight,
                    behavior: "smooth",
                });
                lastScrollTopRef.current = viewport.scrollHeight;
            }
        }
        prevMessageCountRef.current = messages.length;
    }, [messages]);

    // Auto-scroll when new messages or tokens arrive, ONLY IF user hasn't scrolled up and not loading earlier
    useEffect(() => {
        const container = scrollContainerRef.current;
        if (!container) return;

        const viewport = container.querySelector(
            '[data-slot="scroll-area-viewport"]'
        ) as HTMLElement | null;
        if (!viewport) return;

        // User scroll intent takes priority over streaming auto-scroll
        if (!isScrolledUpRef.current && !isLoadingEarlier) {
            isProgrammaticScrollRef.current = true;
            viewport.scrollTo({
                top: viewport.scrollHeight,
                behavior: streaming ? "instant" : "smooth",
            });
            lastScrollTopRef.current = viewport.scrollHeight;
        }
    }, [messages, streamText, streaming, isLoadingEarlier]);

    function scrollToBottom() {
        const container = scrollContainerRef.current;
        if (!container) return;
        const viewport = container.querySelector(
            '[data-slot="scroll-area-viewport"]'
        ) as HTMLElement | null;
        if (!viewport) return;

        isScrolledUpRef.current = false;
        setIsScrolledUp(false);
        lastUserUpScrollTimeRef.current = 0;
        isProgrammaticScrollRef.current = true;
        viewport.scrollTo({ top: viewport.scrollHeight, behavior: "smooth" });
        lastScrollTopRef.current = viewport.scrollHeight;
    }

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
        <div ref={scrollContainerRef} className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
            {/* Live region for screen readers */}
            <div aria-live="polite" aria-atomic="true" className="sr-only">
                {streaming
                    ? "GitBot is generating a response..."
                    : "Response generation finished."}
            </div>

            <ScrollArea className="flex-1 min-h-0 min-w-0 w-full">
                <div className="mx-auto flex w-full max-w-4xl flex-col gap-5 px-4 sm:px-6 py-6 transition-all">
                    {hasMore && (
                        <div className="flex justify-center pb-2">
                            <Button
                                variant="outline"
                                size="sm"
                                className="text-xs text-muted-foreground hover:text-foreground"
                                onClick={handleLoadEarlier}
                                disabled={isLoadingEarlier}
                            >
                                {isLoadingEarlier ? "Loading earlier messages..." : "Load earlier messages"}
                            </Button>
                        </div>
                    )}

                    {!hasActiveSession && (
                        <div className="rounded-2xl border border-dashed bg-muted/30 px-6 py-10 text-center">
                            <p className="font-medium">Repository workspace ready</p>
                            <p className="mt-1 text-sm text-muted-foreground">
                                Select an existing conversation from the sidebar or start a new chat to begin.
                            </p>
                            {onNewChat && (
                                <div className="mt-4 flex justify-center">
                                    <Button size="sm" onClick={onNewChat}>
                                        <Plus data-icon="inline-start" />
                                        Start new chat
                                    </Button>
                                </div>
                            )}
                        </div>
                    )}

                    {hasActiveSession && messages.length === 0 && !streamText && (
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
                            const isRetryingThis = !isUser && message.id === retryingMessageId;
                            const isInterrupted = message.status === "INTERRUPTED" && !isRetryingThis;
                            const isFailed = message.status === "FAILED" && !isRetryingThis;

                            return (
                                <Message
                                    key={message.id}
                                    align={isUser ? "end" : "start"}
                                    className={cn(
                                        "group/msg relative",
                                        isUser
                                            ? "ml-auto max-w-[85%] sm:max-w-[78%] md:max-w-[72%] pr-1 sm:pr-2"
                                            : "w-full max-w-full"
                                    )}
                                >
                                    <MessageAvatar>
                                        {isUser ? (
                                            <div className="relative size-8 shrink-0 overflow-hidden rounded-full">
                                                {user?.avatarUrl ? (
                                                    /* eslint-disable-next-line @next/next/no-img-element */
                                                    <img
                                                        src={user.avatarUrl}
                                                        alt={user.displayName || user.githubUsername || "User"}
                                                        referrerPolicy="no-referrer"
                                                        className="size-full object-cover"
                                                    />
                                                ) : (
                                                    <div className="flex size-full items-center justify-center bg-primary text-primary-foreground">
                                                        <UserRound className="size-4" />
                                                    </div>
                                                )}
                                            </div>
                                        ) : (
                                            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-transparent p-0">
                                                <GitBotIcon className="size-8 rounded-lg" />
                                            </div>
                                        )}
                                    </MessageAvatar>
                                    <MessageContent>
                                        <Bubble
                                            variant={isUser ? "default" : "ghost"}
                                            align={isUser ? "end" : "start"}
                                            className={cn(
                                                !isUser
                                                    ? "max-w-full bg-transparent border-none shadow-none"
                                                    : "max-w-full rounded-2xl sm:rounded-3xl"
                                            )}
                                        >
                                            <BubbleContent
                                                className={cn(
                                                    !isUser
                                                        ? "w-full max-w-full px-1 py-1 bg-transparent border-none shadow-none text-foreground"
                                                        : "px-4 py-2.5"
                                                )}
                                            >
                                                {isUser ? (
                                                    <span className="whitespace-pre-wrap break-words">{message.content}</span>
                                                ) : isRetryingThis ? (
                                                    <ChatMarkdown content={streamText || "Regenerating response..."} isStreaming />
                                                ) : (
                                                    <>
                                                        <ChatMarkdown content={message.content} />
                                                        {isInterrupted && (
                                                            <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-500/30 bg-amber-500/10 p-2.5 text-xs text-amber-800 dark:text-amber-200">
                                                                <div className="flex items-center gap-1.5 font-medium">
                                                                    <StopCircle className="size-4 shrink-0 text-amber-500" />
                                                                    <span>Generation stopped before completion.</span>
                                                                </div>
                                                                {onRetry && (
                                                                    <Button
                                                                        size="sm"
                                                                        variant="outline"
                                                                        className="h-6 px-2 text-xs border-amber-500/40 hover:bg-amber-500/20"
                                                                        disabled={streaming}
                                                                        onClick={() => onRetry(message.id)}
                                                                    >
                                                                        <RotateCcw className="mr-1 size-3" />
                                                                        Retry
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        )}
                                                        {isFailed && (
                                                            <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive">
                                                                <div className="flex items-center gap-1.5 font-medium">
                                                                    <AlertCircle className="size-4 shrink-0" />
                                                                    <span>Generation encountered an error.</span>
                                                                </div>
                                                                {onRetry && (
                                                                    <Button
                                                                        size="sm"
                                                                        variant="destructive"
                                                                        className="h-6 px-2 text-xs"
                                                                        disabled={streaming}
                                                                        onClick={() => onRetry(message.id)}
                                                                    >
                                                                        <RotateCcw className="mr-1 size-3" />
                                                                        Retry
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        )}
                                                    </>
                                                )}
                                            </BubbleContent>
                                        </Bubble>

                                        {isUser && (
                                            <UserMessageToolbar message={message} />
                                        )}

                                        {!isUser && message.citations?.length > 0 && !isRetryingThis && (
                                            <MessageFooter>
                                                <CitationChips repo={repo} citations={message.citations} />
                                            </MessageFooter>
                                        )}

                                        {!isUser && !isRetryingThis && (
                                            <AssistantMessageToolbar
                                                message={message}
                                                streaming={streaming}
                                                ready={ready}
                                                onRetry={onRetry}
                                                onBranch={onBranch}
                                                onReport={onReport}
                                                onShare={onShare}
                                            />
                                        )}
                                    </MessageContent>
                                </Message>
                            );
                        })}

                        {(streaming || streamText) && !retryingMessageId && (streamText || messages[messages.length - 1]?.role === "USER") && (
                            <Message align="start" className="group/msg relative w-full max-w-full">
                                <MessageAvatar className="self-start mt-0.5">
                                    <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-transparent p-0">
                                        <GitBotIcon className="size-8 rounded-lg" />
                                    </div>
                                </MessageAvatar>
                                <MessageContent>
                                    <Bubble variant="ghost" align="start" className="max-w-full bg-transparent border-none shadow-none">
                                        <BubbleContent className="w-full max-w-full px-1 py-1 bg-transparent border-none shadow-none text-foreground">
                                            {streamText ? (
                                                <ChatMarkdown content={streamText} isStreaming />
                                            ) : (
                                                <div className="flex items-center gap-2.5 py-1.5 text-sm text-muted-foreground animate-in fade-in duration-200">
                                                    <Sparkles className="size-4 text-primary animate-pulse shrink-0" />
                                                    <span className="font-medium text-xs text-foreground/80">
                                                        Searching codebase and preparing response
                                                    </span>
                                                    <span className="inline-flex items-center gap-1">
                                                        <span className="size-1 rounded-full bg-primary/70 animate-bounce" style={{ animationDelay: "0ms" }} />
                                                        <span className="size-1 rounded-full bg-primary/70 animate-bounce" style={{ animationDelay: "150ms" }} />
                                                        <span className="size-1 rounded-full bg-primary/70 animate-bounce" style={{ animationDelay: "300ms" }} />
                                                    </span>
                                                </div>
                                            )}
                                        </BubbleContent>
                                    </Bubble>
                                </MessageContent>
                            </Message>
                        )}
                    </MessageGroup>
                </div>
            </ScrollArea>

            {/* Jump to latest button if user scrolled up */}
            {isScrolledUp && (
                <div className="pointer-events-none absolute inset-x-0 bottom-4 flex justify-center z-20">
                    <Button
                        size="sm"
                        variant="secondary"
                        className="pointer-events-auto gap-1.5 rounded-full border border-border/80 bg-background/95 px-3 py-1.5 text-xs font-medium shadow-md backdrop-blur transition-transform hover:scale-105 active:scale-95"
                        onClick={scrollToBottom}
                        aria-label="Scroll to newest messages"
                    >
                        <ChevronDown className="size-3.5" />
                        <span>{streaming ? "New response below" : "Jump to latest"}</span>
                    </Button>
                </div>
            )}
        </div>
    );
}