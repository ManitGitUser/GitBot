"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import {
    ArrowLeft,
    Check,
    ChevronDown,
    Copy,
    ExternalLink,
    FolderGit2,
    Info,
    Sparkles,
    Terminal,
} from "lucide-react";

import { ChatComposer } from "@/components/chat/chat-composer";
import { ChatMarkdown } from "@/components/chat/chat-markdown";
import { CitationChips } from "@/components/chat/citation-chips";
import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { GitHubIcon } from "@/components/icons/github-icon";
import { BrandMark } from "@/components/layout/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { ScrollArea } from "@/components/ui/scroll-area";
import { toast } from "@/components/ui/toast";
import { Bubble, BubbleContent } from "@/components/ui/bubble";
import {
    Message,
    MessageAvatar,
    MessageContent,
    MessageFooter,
    MessageGroup,
} from "@/components/ui/message";
import { api, ApiError, getGithubLoginUrl, type ChatMessage } from "@/lib/api";
import { streamDemoChat } from "@/lib/stream-demo-chat";
import { cn } from "@/lib/utils";

const DEMO_REPO = {
    name: "GitBot",
    fullName: "ManitGitUser/GitBot",
    defaultBranch: "main",
    htmlUrl: "https://github.com/ManitGitUser/GitBot",
};

const MAX_DEMO_MESSAGES = 5;

const PROMPT_SUGGESTIONS = [
    {
        title: "RAG Architecture",
        prompt: "How does the RAG retrieval and neighbor chunking work in GitBot?",
    },
    {
        title: "Repository Isolation",
        prompt: "How is repository isolation guaranteed across users in vector searches?",
    },
    {
        title: "SSE Streaming",
        prompt: "How does GitBot handle SSE streaming and token delivery to the browser?",
    },
    {
        title: "Session & Auth",
        prompt: "How does GitHub OAuth and session cookie security work in GitBot?",
    },
];

const STORAGE_KEYS = {
    MESSAGES: "gitbot_demo_messages",
    TOKEN: "gitbot_demo_token",
    COUNT: "gitbot_demo_count",
};

function copyToClipboard(text: string): Promise<boolean> {
    if (typeof window !== "undefined" && navigator?.clipboard?.writeText) {
        return navigator.clipboard.writeText(text).then(
            () => true,
            () => false
        );
    }
    return Promise.resolve(false);
}

const emptySubscribe = () => () => {};

export default function DemoPage() {
    const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);
    const [messages, setMessages] = useState<ChatMessage[]>(() => {
        if (typeof window === "undefined") return [];
        try {
            const saved = sessionStorage.getItem(STORAGE_KEYS.MESSAGES);
            return saved ? (JSON.parse(saved) as ChatMessage[]) : [];
        } catch {
            return [];
        }
    });
    const [demoToken, setDemoToken] = useState<string | null>(() => {
        if (typeof window === "undefined") return null;
        try {
            return sessionStorage.getItem(STORAGE_KEYS.TOKEN);
        } catch {
            return null;
        }
    });
    const [messageCount, setMessageCount] = useState<number>(() => {
        if (typeof window === "undefined") return 0;
        try {
            const saved = sessionStorage.getItem(STORAGE_KEYS.COUNT);
            return saved ? parseInt(saved, 10) || 0 : 0;
        } catch {
            return 0;
        }
    });
    const [streamText, setStreamText] = useState<string>("");
    const [streaming, setStreaming] = useState<boolean>(false);
    const [copiedId, setCopiedId] = useState<string | null>(null);

    const abortControllerRef = useRef<AbortController | null>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const [isScrolledUp, setIsScrolledUp] = useState(false);
    const isScrolledUpRef = useRef(false);
    const isProgrammaticScrollRef = useRef(false);
    const lastUserUpScrollTimeRef = useRef(0);
    const lastScrollTopRef = useRef(0);
    const prevMessageCountRef = useRef(messages.length);

    // Sync authoritative IP demo remaining message count from backend on mount
    useEffect(() => {
        api.getDemoStatus()
            .then((status) => {
                if (typeof status.remainingMessages === "number") {
                    const used = Math.max(0, MAX_DEMO_MESSAGES - status.remainingMessages);
                    setMessageCount((prev) => {
                        const updated = Math.max(prev, used);
                        try {
                            sessionStorage.setItem(STORAGE_KEYS.COUNT, updated.toString());
                        } catch {}
                        return updated;
                    });
                }
            })
            .catch(() => {});
    }, []);

    // Sync updates to sessionStorage (browser tab scope only)
    const persistSession = useCallback((msgs: ChatMessage[], token: string | null, count: number) => {
        try {
            sessionStorage.setItem(STORAGE_KEYS.MESSAGES, JSON.stringify(msgs));
            if (token) {
                sessionStorage.setItem(STORAGE_KEYS.TOKEN, token);
            } else {
                sessionStorage.removeItem(STORAGE_KEYS.TOKEN);
            }
            sessionStorage.setItem(STORAGE_KEYS.COUNT, count.toString());
        } catch {
            // Ignore storage write errors
        }
    }, []);

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

    // Auto-scroll when new messages or tokens arrive, ONLY IF user hasn't scrolled up
    useEffect(() => {
        const container = scrollContainerRef.current;
        if (!container) return;

        const viewport = container.querySelector(
            '[data-slot="scroll-area-viewport"]'
        ) as HTMLElement | null;
        if (!viewport) return;

        if (!isScrolledUpRef.current) {
            isProgrammaticScrollRef.current = true;
            viewport.scrollTo({
                top: viewport.scrollHeight,
                behavior: streaming ? "instant" : "smooth",
            });
            lastScrollTopRef.current = viewport.scrollHeight;
        }
    }, [messages, streamText, streaming]);

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

    const handleCopy = async (id: string, text: string) => {
        const ok = await copyToClipboard(text);
        if (ok) {
            setCopiedId(id);
            setTimeout(() => setCopiedId(null), 2000);
            toast.add({ title: "Copied to clipboard", type: "success" });
        }
    };

    const handleStop = () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            abortControllerRef.current = null;
        }
        setStreaming(false);
    };

    const handleSend = async (content: string) => {
        if (!content.trim() || streaming) return;

        if (messageCount >= MAX_DEMO_MESSAGES) {
            toast.add({
                title: "Demo limit reached",
                description: "You have used all 5 demo messages. Sign in with GitHub to continue.",
                type: "warning",
            });
            return;
        }

        const nextCount = messageCount + 1;
        setMessageCount(nextCount);

        const tempUserMessage: ChatMessage = {
            id: crypto.randomUUID(),
            role: "USER",
            status: "COMPLETE",
            content: content.trim(),
            citations: [],
            createdAt: new Date().toISOString(),
        };

        const updatedMessages = [...messages, tempUserMessage];
        setMessages(updatedMessages);
        persistSession(updatedMessages, demoToken, nextCount);

        setStreaming(true);
        setStreamText("");

        const controller = new AbortController();
        abortControllerRef.current = controller;

        // Build history turns for RAG context
        const history = messages.slice(-10).map((m) => ({
            role: m.role,
            content: m.content,
        }));

        let activeToken = demoToken;

        try {
            await streamDemoChat(
                {
                    message: content.trim(),
                    history,
                    demoToken: activeToken,
                },
                {
                    signal: controller.signal,
                    onDemoToken: (token) => {
                        activeToken = token;
                        setDemoToken(token);
                    },
                    onToken: (token) => {
                        setStreamText((prev) => prev + token);
                    },
                    onAssistantMessage: (assistantMsg) => {
                        setMessages((prev) => {
                            const next = [...prev, assistantMsg];
                            persistSession(next, activeToken, nextCount);
                            return next;
                        });
                        setStreamText("");
                        setStreaming(false);
                    },
                    onDone: () => {
                        setStreaming(false);
                    },
                    onError: (err) => {
                        setStreaming(false);
                        toast.add({
                            title: "Demo Error",
                            description: err.message || "An error occurred during generation.",
                            type: "error",
                        });
                    },
                }
            );
        } catch (err: unknown) {
            setStreaming(false);
            const msg = err instanceof Error ? err.message : "Failed to send message";
            const isLimit =
                msg.toLowerCase().includes("limit reached") ||
                (err instanceof ApiError && err.status === 429);
            if (isLimit) {
                setMessageCount(MAX_DEMO_MESSAGES);
                persistSession(messages, demoToken, MAX_DEMO_MESSAGES);
                toast.add({
                    title: "Demo limit reached",
                    description: msg,
                    type: "warning",
                });
            } else {
                toast.add({
                    title: "Error",
                    description: msg,
                    type: "error",
                });
            }
        }
    };

    const remainingMessages = Math.max(0, MAX_DEMO_MESSAGES - messageCount);
    const limitReached = messageCount >= MAX_DEMO_MESSAGES;

    return (
        <div className="flex h-svh flex-col overflow-hidden bg-background">
            {/* Top Navigation Bar */}
            <header className="shrink-0 border-b bg-card/50 backdrop-blur z-20">
                <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4 sm:px-6">
                    <div className="flex items-center gap-3">
                        <Link
                            href="/"
                            className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
                        >
                            <ArrowLeft className="size-3.5" />
                            <span>Home</span>
                        </Link>
                        <div className="h-4 w-px bg-border/60" />
                        <BrandMark />
                    </div>

                    {/* Demo Status Badge & Notice */}
                    <div className="flex items-center gap-3">
                        <div className="hidden sm:flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-xs">
                            <Sparkles className="size-3.5 text-amber-500 animate-pulse" />
                            <span className="font-medium text-foreground">Demo Mode</span>
                            <span className="text-muted-foreground">·</span>
                            <span
                                className={cn(
                                    "font-semibold",
                                    remainingMessages === 0
                                        ? "text-destructive"
                                        : remainingMessages <= 2
                                            ? "text-amber-500"
                                            : "text-primary"
                                )}
                            >
                                {mounted ? (
                                    remainingMessages === 0
                                        ? "Limit reached"
                                        : `${remainingMessages} message${remainingMessages === 1 ? "" : "s"} left`
                                ) : (
                                    "5 messages left"
                                )}
                            </span>
                        </div>

                        <div className="flex items-center gap-2">
                            <ModeToggle />
                            <a
                                href={getGithubLoginUrl()}
                                className={cn(
                                    buttonVariants({ size: "sm" }),
                                    "hidden xs:inline-flex items-center gap-1.5 shadow-sm"
                                )}
                            >
                                <GitHubIcon className="size-3.5" />
                                <span>Sign in</span>
                            </a>
                        </div>
                    </div>
                </div>

                {/* Mobile demo counter bar */}
                <div className="flex sm:hidden items-center justify-between border-t border-border/40 bg-muted/40 px-4 py-1.5 text-[11px] text-muted-foreground">
                    <span className="flex items-center gap-1.5">
                        <Sparkles className="size-3 text-amber-500" />
                        Demo mode · Tab scoped
                    </span>
                    <span
                        className={cn(
                            "font-medium",
                            mounted && remainingMessages === 0 ? "text-destructive" : "text-foreground"
                        )}
                    >
                        {mounted
                            ? (remainingMessages === 0 ? "0 / 5 remaining" : `${remainingMessages} / 5 remaining`)
                            : "5 / 5 remaining"}
                    </span>
                </div>
            </header>

            {/* Conversation Area */}
            <main ref={scrollContainerRef} className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
                {/* Live region for screen readers */}
                <div aria-live="polite" aria-atomic="true" className="sr-only">
                    {streaming
                        ? "GitBot is generating a response..."
                        : "Response generation finished."}
                </div>

                <ScrollArea className="flex-1 min-h-0 min-w-0 w-full">
                    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 py-6 sm:px-6 transition-all">
                        {/* Empty State / Welcome Screen */}
                        {messages.length === 0 && !streaming && (
                            <div className="my-auto flex flex-col items-center justify-center py-8 text-center animate-in fade-in-50 duration-300">
                                <div className="mb-4 flex size-14 items-center justify-center rounded-2xl border border-border/60 bg-black shadow-md">
                                    <GitBotIcon className="size-14 rounded-2xl" />
                                </div>

                                <h2 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">
                                    Try GitBot with its codebase
                                </h2>

                                <p className="mt-2 max-w-md text-sm text-muted-foreground text-balance">
                                    Ask anything about GitBot&apos;s architecture, RAG vector retrieval,
                                    or backend streaming. No account or setup needed.
                                </p>

                                <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
                                    <Badge variant="secondary" className="gap-1.5 px-3 py-1 font-normal">
                                        <FolderGit2 className="size-3.5 text-muted-foreground" />
                                        <a
                                            href={DEMO_REPO.htmlUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="hover:underline flex items-center gap-1"
                                        >
                                            {DEMO_REPO.fullName}
                                            <ExternalLink className="size-2.5 opacity-60" />
                                        </a>
                                    </Badge>

                                    <Badge variant="outline" className="px-3 py-1 font-normal">
                                        5 free messages
                                    </Badge>

                                    <Badge variant="outline" className="px-3 py-1 font-normal text-muted-foreground">
                                        Tab-only memory
                                    </Badge>
                                </div>

                                {/* Privacy & Scope Note */}
                                <div className="mt-6 flex items-center gap-2 rounded-xl border border-border/60 bg-muted/30 px-3.5 py-2 text-xs text-muted-foreground">
                                    <Info className="size-3.5 text-primary shrink-0" />
                                    <span>
                                        Demo mode · 5 messages · Not saved to database · Disappears on tab close.
                                    </span>
                                </div>

                                {/* Starter Prompts */}
                                <div className="mt-8 grid w-full grid-cols-1 gap-3 sm:grid-cols-2 text-left">
                                    {PROMPT_SUGGESTIONS.map((item) => (
                                        <button
                                            key={item.title}
                                            type="button"
                                            onClick={() => void handleSend(item.prompt)}
                                            className="group flex flex-col justify-between rounded-xl border border-border/70 bg-card p-4 text-left shadow-xs transition-all hover:border-primary/50 hover:bg-accent/40"
                                        >
                                            <span className="text-xs font-semibold text-foreground group-hover:text-primary transition-colors flex items-center gap-1.5">
                                                <Terminal className="size-3 text-muted-foreground group-hover:text-primary" />
                                                {item.title}
                                            </span>
                                            <span className="mt-1 text-xs text-muted-foreground line-clamp-2">
                                                {item.prompt}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Messages List */}
                        {(messages.length > 0 || streaming) && (
                            <MessageGroup className="space-y-6">
                                {messages.map((message) => {
                                    const isUser = message.role === "USER";
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
                                            <MessageAvatar className="self-start mt-0.5">
                                                {isUser ? (
                                                    <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground font-semibold text-xs">
                                                        U
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
                                                            <p className="whitespace-pre-wrap text-sm leading-relaxed">
                                                                {message.content}
                                                            </p>
                                                        ) : (
                                                            <ChatMarkdown content={message.content} />
                                                        )}
                                                    </BubbleContent>
                                                </Bubble>

                                                {/* User Message Actions (Copy prompt) */}
                                                {isUser && (
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
                                                            onClick={() => void handleCopy(message.id, message.content)}
                                                            aria-label={copiedId === message.id ? "Copied prompt to clipboard" : "Copy prompt"}
                                                        >
                                                            {copiedId === message.id ? (
                                                                <Check className="size-3.5 text-emerald-500" />
                                                            ) : (
                                                                <Copy className="size-3.5" />
                                                            )}
                                                            <span className="ml-1">{copiedId === message.id ? "Copied" : "Copy"}</span>
                                                        </Button>
                                                    </div>
                                                )}

                                                {/* Citations Footer */}
                                                {!isUser && message.citations?.length > 0 && (
                                                    <MessageFooter>
                                                        <CitationChips
                                                            repo={DEMO_REPO}
                                                            citations={message.citations}
                                                        />
                                                    </MessageFooter>
                                                )}

                                                {/* Assistant Message Actions (Copy only, no retry/branch/report in demo) */}
                                                {!isUser && (
                                                    <div className="mt-1.5 flex items-center gap-1 text-muted-foreground opacity-100 sm:opacity-0 sm:group-hover/msg:opacity-100 transition-opacity">
                                                        <Button
                                                            variant="ghost"
                                                            size="icon-xs"
                                                            onClick={() => void handleCopy(message.id, message.content)}
                                                            className="h-6 w-6 text-muted-foreground hover:text-foreground"
                                                            title="Copy response"
                                                        >
                                                            {copiedId === message.id ? (
                                                                <Check className="size-3 text-green-500" />
                                                            ) : (
                                                                <Copy className="size-3" />
                                                            )}
                                                        </Button>
                                                    </div>
                                                )}
                                            </MessageContent>
                                        </Message>
                                    );
                                })}

                                {/* Active Streaming Response / Thinking Placeholder Bubble */}
                                {streaming && (streamText || messages[messages.length - 1]?.role === "USER") && (
                                    <Message align="start" className="group/msg relative w-full max-w-full">
                                        <MessageAvatar className="self-start mt-0.5">
                                            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-transparent p-0">
                                                <GitBotIcon className="size-8 rounded-lg" />
                                            </div>
                                        </MessageAvatar>
                                        <MessageContent>
                                            <Bubble
                                                variant="ghost"
                                                align="start"
                                                className="max-w-full bg-transparent border-none shadow-none"
                                            >
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
                        )}

                        {/* Limit Reached Callout Card */}
                        {limitReached && !streaming && (
                            <div className="my-6 rounded-2xl border border-primary/30 bg-primary/5 p-6 sm:p-8 text-center shadow-sm animate-in fade-in-50 zoom-in-95 duration-300">
                                <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-xl border border-primary/20 bg-background text-primary shadow-xs">
                                    <Sparkles className="size-6 text-amber-400" />
                                </div>
                                <h3 className="font-heading text-lg sm:text-xl font-semibold text-foreground">
                                    Demo limit reached
                                </h3>
                                <p className="mt-1 text-sm text-muted-foreground">
                                    Your demo conversation was not saved.
                                </p>
                                <p className="mt-1 text-sm text-muted-foreground">
                                    Sign in with GitHub to continue using GitBot with your own repositories.
                                </p>
                                <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
                                    <a
                                        href={getGithubLoginUrl()}
                                        className={cn(buttonVariants({ size: "lg" }), "gap-2 shadow-sm")}
                                    >
                                        <GitHubIcon className="size-4" />
                                        <span>Sign in with GitHub</span>
                                    </a>
                                </div>
                            </div>
                        )}
                    </div>
                </ScrollArea>

                {/* Jump to latest button if user scrolled up */}
                {isScrolledUp && (
                    <div className="pointer-events-none absolute inset-x-0 bottom-24 flex justify-center z-20">
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

                {/* Chat Composer */}
                <ChatComposer
                    disabled={limitReached}
                    placeholder={
                        limitReached
                            ? "Demo limit reached (5/5) · Sign in with GitHub to continue"
                            : `Ask anything about GitBot (${remainingMessages} of 5 messages remaining)…`
                    }
                    streaming={streaming}
                    onSend={handleSend}
                    onStop={handleStop}
                />
            </main>
        </div>
    );
}
