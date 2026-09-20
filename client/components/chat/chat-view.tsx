"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    AlertCircle,
    ArrowLeft,
    Check,
    CheckCircle2,
    Copy,
    GitFork,
    Globe,
    Lock,
    Share2,
    Shield,
} from "lucide-react";

import { ChatComposer } from "@/components/chat/chat-composer";
import { ChatMessages } from "@/components/chat/chat-messages";
import { ChatSidebar } from "@/components/chat/chat-sidebar";
import { IndexingState } from "@/components/chat/indexing-state";
import { AppShell } from "@/components/layout/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "@/components/ui/toast";
import {
    useBranchChatSession,
    useChatMessages,
    useChatSessions,
    useCreateChatSession,
    useReportMessage,
    useRevokeChatSession,
    useShareChatSession,
    useStreamChat,
} from "@/hooks/use-chat";
import { useIndexStatus, useRepository } from "@/hooks/use-repos";
import type { ChatMessage, ChatSession, ReportReason } from "@/lib/api";
import { cn } from "@/lib/utils";

const REPORT_REASONS: { value: ReportReason; label: string; desc: string }[] = [
    {
        value: "INCORRECT",
        label: "Incorrect or inaccurate",
        desc: "Contains factual errors, hallucinated APIs, or incorrect code.",
    },
    {
        value: "IRRELEVANT",
        label: "Irrelevant or unhelpful",
        desc: "Did not follow instructions or went off-topic.",
    },
    {
        value: "UNSAFE",
        label: "Harmful or unsafe",
        desc: "Inappropriate suggestions or security vulnerabilities.",
    },
    {
        value: "CITATION_ISSUE",
        label: "Citation problem",
        desc: "Referenced wrong file paths or non-existent line ranges.",
    },
    {
        value: "OTHER",
        label: "Other issue",
        desc: "Any other problem not covered above.",
    },
];

export function ChatView({ repoId }: { repoId: string }) {
    const searchParams = useSearchParams();
    const isOpenMode = searchParams.get("mode") === "open";
    const paramSessionId = searchParams.get("sessionId");

    const repoQuery = useRepository(repoId);
    const isIndexing = repoQuery.data?.indexStatus === "INDEXING";
    const statusQuery = useIndexStatus(
        repoId,
        isIndexing || repoQuery.data?.indexStatus === "PENDING"
    );

    const indexStatus = statusQuery.data?.indexStatus ?? repoQuery.data?.indexStatus;
    const ready = indexStatus === "READY";

    const sessionsQuery = useChatSessions(repoId, ready);
    const createSession = useCreateChatSession(repoId);
    const branchSession = useBranchChatSession(repoId);
    const shareSession = useShareChatSession();
    const revokeShare = useRevokeChatSession();
    const reportMessage = useReportMessage();

    const [prevParamSessionId, setPrevParamSessionId] = useState(paramSessionId);
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(paramSessionId);
    const autoCreateRef = useRef(false);

    if (paramSessionId !== prevParamSessionId) {
        setPrevParamSessionId(paramSessionId);
        setSelectedSessionId(paramSessionId);
    }

    const allSessions = useMemo(
        () => sessionsQuery.data?.pages.flatMap((p) => p.content) ?? [],
        [sessionsQuery.data]
    );
    const activeSession =
        allSessions.find(
            (s: ChatSession) =>
                s.id ===
                (selectedSessionId ??
                    (isOpenMode ? undefined : allSessions[0]?.id))
        ) ??
        (isOpenMode && selectedSessionId === null
            ? null
            : allSessions[0] ?? null);
    const sessionId = activeSession?.id ?? selectedSessionId ?? null;

    useEffect(() => {
        if (activeSession?.title) {
            document.title = `${activeSession.title} - GitBot`;
        } else {
            document.title = "Chat - GitBot";
        }
    }, [activeSession?.title]);

    const messagesQuery = useChatMessages(sessionId);
    const { send, retry, stop, streaming, streamText, retryingMessageId } = useStreamChat(sessionId);

    // Share Modal State
    const [shareOpen, setShareOpen] = useState(false);
    const [copiedShare, setCopiedShare] = useState(false);

    // Branch Modal State
    const [branchingMessage, setBranchingMessage] = useState<ChatMessage | null>(null);
    const [branchTitle, setBranchTitle] = useState("");

    // Report Modal State
    const [reportingMessage, setReportingMessage] = useState<ChatMessage | null>(null);
    const [reportReason, setReportReason] = useState<ReportReason>("INCORRECT");
    const [reportDetails, setReportDetails] = useState("");
    const [reportSuccess, setReportSuccess] = useState(false);
    const [reportError, setReportError] = useState<string | null>(null);

    // Resizable Sidebar State
    const MIN_SIDEBAR_WIDTH = 220;
    const MAX_SIDEBAR_WIDTH = 480;
    const DEFAULT_SIDEBAR_WIDTH = 288;
    const SIDEBAR_STORAGE_KEY = "gitbot:chat-sidebar-width";

    const [sidebarWidth, setSidebarWidth] = useState(DEFAULT_SIDEBAR_WIDTH);
    const [isDragging, setIsDragging] = useState(false);
    const sidebarWidthRef = useRef(sidebarWidth);

    useEffect(() => {
        sidebarWidthRef.current = sidebarWidth;
    }, [sidebarWidth]);

    useEffect(() => {
        try {
            const saved = localStorage.getItem(SIDEBAR_STORAGE_KEY);
            if (saved) {
                const parsed = parseInt(saved, 10);
                if (!Number.isNaN(parsed) && parsed >= MIN_SIDEBAR_WIDTH && parsed <= MAX_SIDEBAR_WIDTH) {
                    setTimeout(() => setSidebarWidth(parsed), 0);
                }
            }
        } catch {
            // ignore
        }
    }, []);

    const handlePointerDown = useCallback((e: React.PointerEvent) => {
        e.preventDefault();
        setIsDragging(true);
        const startX = e.clientX;
        const startWidth = sidebarWidthRef.current;

        const onPointerMove = (moveEvent: PointerEvent) => {
            const delta = moveEvent.clientX - startX;
            const newWidth = Math.min(
                MAX_SIDEBAR_WIDTH,
                Math.max(MIN_SIDEBAR_WIDTH, startWidth + delta)
            );
            setSidebarWidth(newWidth);
        };

        const onPointerUp = () => {
            setIsDragging(false);
            document.removeEventListener("pointermove", onPointerMove);
            document.removeEventListener("pointerup", onPointerUp);
            document.body.style.removeProperty("cursor");
            document.body.style.removeProperty("user-select");
            try {
                localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarWidthRef.current));
            } catch {
                // ignore
            }
        };

        document.body.style.cursor = "col-resize";
        document.body.style.userSelect = "none";
        document.addEventListener("pointermove", onPointerMove);
        document.addEventListener("pointerup", onPointerUp);
    }, []);

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "ArrowLeft") {
            e.preventDefault();
            const next = Math.max(MIN_SIDEBAR_WIDTH, sidebarWidth - 16);
            setSidebarWidth(next);
            try {
                localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
            } catch {}
        } else if (e.key === "ArrowRight") {
            e.preventDefault();
            const next = Math.min(MAX_SIDEBAR_WIDTH, sidebarWidth + 16);
            setSidebarWidth(next);
            try {
                localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
            } catch {}
        }
    };

    useEffect(() => {
        if (!ready || sessionsQuery.isLoading) return;
        if (isOpenMode && selectedSessionId === null) return;
        if (allSessions.length > 0) return;
        if (
            !sessionsQuery.isSuccess ||
            allSessions.length > 0 ||
            autoCreateRef.current
        ) {
            return;
        }

        autoCreateRef.current = true;
        createSession.mutate(undefined, {
            onSuccess: (session) => setSelectedSessionId(session.id),
            onError: () => {
                autoCreateRef.current = false;
            },
        });
    }, [
        ready,
        isOpenMode,
        selectedSessionId,
        sessionsQuery.isLoading,
        sessionsQuery.isSuccess,
        allSessions.length,
        createSession,
    ]);

    function handleStartBranch(msg: ChatMessage) {
        setBranchingMessage(msg);
        setBranchTitle(activeSession ? `Branch: ${activeSession.title}` : "Branch");
    }

    function handleConfirmBranch() {
        if (!sessionId || !branchingMessage || !branchTitle.trim()) return;
        branchSession.mutate(
            {
                sessionId,
                messageId: branchingMessage.id,
                title: branchTitle.trim(),
            },
            {
                onSuccess: (newSession) => {
                    setBranchingMessage(null);
                    setSelectedSessionId(newSession.id);
                },
            }
        );
    }

    function handleStartReport(msg: ChatMessage) {
        setReportingMessage(msg);
        setReportReason("INCORRECT");
        setReportDetails("");
        setReportSuccess(false);
        setReportError(null);
    }

    function handleConfirmReport() {
        if (!sessionId || !reportingMessage) return;
        setReportError(null);
        reportMessage.mutate(
            {
                sessionId,
                messageId: reportingMessage.id,
                reason: reportReason,
                details: reportDetails.trim() || undefined,
            },
            {
                onSuccess: () => {
                    setReportSuccess(true);
                },
                onError: (err) => {
                    setReportError(err.message || "Failed to submit report");
                },
            }
        );
    }

    function handleToggleShare() {
        if (!sessionId) return;
        if (activeSession?.isShared) {
            revokeShare.mutate(sessionId, {
                onSuccess: () => {
                    void sessionsQuery.refetch();
                    toast.add({
                        title: "Share link revoked",
                        description: "This conversation is now private.",
                        type: "success",
                    });
                },
            });
        } else {
            shareSession.mutate(sessionId, {
                onSuccess: (share) => {
                    void sessionsQuery.refetch();
                    toast.add({
                        title: "Public link created",
                        description: `Anyone with the link can view this transcript: ${share.shareUrl}`,
                        type: "success",
                    });
                },
            });
        }
    }

    function handleCopyShareUrl() {
        if (!activeSession?.shareToken) return;
        const origin = typeof window !== "undefined" ? window.location.origin : "";
        const shareUrl = `${origin}/share/${activeSession.shareToken}`;
        navigator.clipboard.writeText(shareUrl).then(() => {
            setCopiedShare(true);
            setTimeout(() => setCopiedShare(false), 2000);
            toast.add({
                title: "Share link copied",
                type: "success",
            });
        });
    }

    if (repoQuery.isLoading) {
        return (
            <AppShell title="Loading chat…">
                <div className="grid flex-1 gap-4 p-4 md:grid-cols-[18rem_1fr]">
                    <Skeleton className="min-h-80 rounded-2xl" />
                    <Skeleton className="min-h-80 rounded-2xl" />
                </div>
            </AppShell>
        );
    }

    if (repoQuery.isError || !repoQuery.data) {
        return (
            <AppShell title="Repository unavailable">
                <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8">
                    <p className="text-sm text-muted-foreground">
                        {(repoQuery.error as Error)?.message ?? "Repository not found"}
                    </p>
                    <Link href="/dashboard" className={buttonVariants()}>Back to dashboard</Link>
                </div>
            </AppShell>
        );
    }

    const repo = repoQuery.data;

    return (
        <AppShell
            title={repo.fullName}
            activeSessionId={sessionId}
            contentClassName="overflow-hidden"
            description={
                ready
                    ? "Ask questions grounded in this repository"
                    : "Waiting for indexing to finish"
            }
            actions={
                <div className="flex items-center gap-2">
                    {ready && sessionId && (
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setShareOpen(true)}
                            aria-label="Share conversation transcript"
                            className="gap-1.5"
                        >
                            <Share2 className="size-3.5" />
                            <span>{activeSession?.isShared ? "Shared" : "Share"}</span>
                        </Button>
                    )}
                    <Link
                        href="/dashboard"
                        className={buttonVariants({ variant: "outline", size: "sm" })}
                    >
                        <ArrowLeft data-icon="inline-start" />
                        Repos
                    </Link>
                </div>
            }
        >
            <div className="flex min-h-0 min-w-0 flex-1 flex-col md:flex-row md:h-full md:overflow-hidden">
                <ChatSidebar
                    style={
                        {
                            "--chat-sidebar-width": `${sidebarWidth}px`,
                        } as React.CSSProperties
                    }
                    className="md:w-[var(--chat-sidebar-width)] md:h-full md:min-h-0 md:overflow-hidden flex flex-col"
                    repo={{
                        ...repo,
                        indexStatus: indexStatus ?? repo.indexStatus,
                        filesProcessed: statusQuery.data?.filesProcessed ?? repo.filesProcessed,
                        filesTotal: statusQuery.data?.filesTotal ?? repo.filesTotal,
                        chunkCount: statusQuery.data?.chunkCount ?? repo.chunkCount,
                        errorMessage: statusQuery.data?.errorMessage ?? repo.errorMessage,
                    }}
                    sessionId={sessionId}
                    onSelectSession={setSelectedSessionId}
                    onSessionDeleted={(id) => {
                        if (selectedSessionId === id) {
                            setSelectedSessionId(null);
                        }
                    }}
                />

                {/* Draggable resize handle */}
                <div
                    role="separator"
                    aria-orientation="vertical"
                    aria-label="Resize sidebar split"
                    aria-valuenow={sidebarWidth}
                    aria-valuemin={MIN_SIDEBAR_WIDTH}
                    aria-valuemax={MAX_SIDEBAR_WIDTH}
                    tabIndex={0}
                    onPointerDown={handlePointerDown}
                    onKeyDown={handleKeyDown}
                    className={cn(
                        "relative hidden md:flex w-1.5 -ml-1 cursor-col-resize select-none items-center justify-center transition-colors group z-10 shrink-0",
                        "hover:bg-primary/20 active:bg-primary/40 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
                        isDragging && "bg-primary/40"
                    )}
                >
                    <div className="h-8 w-0.5 rounded-full bg-muted-foreground/30 group-hover:bg-primary/60 transition-colors" />
                </div>

                <section className="flex min-h-0 min-w-0 flex-1 flex-col md:h-full md:overflow-hidden">
                    {!ready ? (
                        <IndexingState repo={repo} status={statusQuery.data} />
                    ) : (
                        <>
                            <ChatMessages
                                repo={repo}
                                messages={messagesQuery.messages}
                                streamText={streamText}
                                retryingMessageId={retryingMessageId}
                                isLoading={messagesQuery.isLoading}
                                hasMore={messagesQuery.hasMore}
                                onLoadEarlier={messagesQuery.loadEarlier}
                                isLoadingEarlier={messagesQuery.isLoadingEarlier}
                                streaming={streaming}
                                onRetry={(msgId) => retry(msgId)}
                                onBranch={handleStartBranch}
                                onReport={handleStartReport}
                                onShare={() => setShareOpen(true)}
                                hasActiveSession={Boolean(sessionId)}
                                onNewChat={() =>
                                    createSession.mutate("New chat", {
                                        onSuccess: (session) => setSelectedSessionId(session.id),
                                    })
                                }
                            />
                            <ChatComposer
                                disabled={!sessionId}
                                placeholder={
                                    !sessionId
                                        ? "Select a conversation or start a new chat to begin…"
                                        : undefined
                                }
                                streaming={streaming}
                                onSend={send}
                                onStop={stop}
                            />
                        </>
                    )}
                </section>
            </div>

            {/* Share Dialog */}
            <Dialog open={shareOpen} onOpenChange={setShareOpen}>
                <DialogContent>
                    <DialogHeader>
                        <div className="flex items-center justify-between pr-6">
                            <DialogTitle className="flex items-center gap-2">
                                <Globe className="size-5 text-primary" />
                                Share conversation
                            </DialogTitle>
                            <Badge
                                variant={activeSession?.isShared ? "secondary" : "outline"}
                                className="text-xs"
                            >
                                {activeSession?.isShared ? (
                                    <>
                                        <Globe className="mr-1 size-3 text-emerald-500" />
                                        Public link active
                                    </>
                                ) : (
                                    <>
                                        <Lock className="mr-1 size-3" />
                                        Private
                                    </>
                                )}
                            </Badge>
                        </div>
                        <DialogDescription>
                            Create an unguessable public link to this conversation. Anyone with the URL will be
                            able to view questions, responses, and referenced code snippets in read-only mode.
                        </DialogDescription>
                    </DialogHeader>

                    {/* Privacy notice callout */}
                    <div className="rounded-xl border border-muted-foreground/20 bg-muted/40 p-3 text-xs text-muted-foreground flex gap-2.5 items-start">
                        <Shield className="size-4 text-primary shrink-0 mt-0.5" />
                        <div>
                            <strong className="text-foreground">Privacy Protection:</strong> Sharing this transcript does
                            <strong> not</strong> make your GitHub repository public. Only messages in this session and
                            the specific code excerpts cited are shared.
                        </div>
                    </div>

                    {activeSession?.isShared && activeSession.shareToken ? (
                        <div className="space-y-3 py-1">
                            <label className="text-xs font-medium text-foreground">Public Link</label>
                            <div className="flex gap-2">
                                <Input
                                    readOnly
                                    value={
                                        typeof window !== "undefined"
                                            ? `${window.location.origin}/share/${activeSession.shareToken}`
                                            : `/share/${activeSession.shareToken}`
                                    }
                                    className="text-xs font-mono select-all"
                                />
                                <Button size="sm" onClick={handleCopyShareUrl} className="shrink-0">
                                    {copiedShare ? <Check className="size-4" /> : <Copy className="size-4" />}
                                    <span className="ml-1.5">{copiedShare ? "Copied" : "Copy"}</span>
                                </Button>
                            </div>
                            <p className="text-xs text-muted-foreground">
                                You can revoke this link at any time to immediately disable access.
                            </p>
                        </div>
                    ) : (
                        <div className="py-2 text-xs text-muted-foreground">
                            This conversation is currently private. Click below to generate an unguessable share link.
                        </div>
                    )}

                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShareOpen(false)}>
                            Close
                        </Button>
                        <Button
                            variant={activeSession?.isShared ? "destructive" : "default"}
                            disabled={shareSession.isPending || revokeShare.isPending}
                            onClick={handleToggleShare}
                        >
                            {shareSession.isPending || revokeShare.isPending ? <Spinner /> : null}
                            {activeSession?.isShared ? "Revoke share" : "Create public link"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Branch Dialog */}
            <Dialog open={Boolean(branchingMessage)} onOpenChange={(open) => !open && setBranchingMessage(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <GitFork className="size-5 text-primary" />
                            Branch conversation
                        </DialogTitle>
                        <DialogDescription>
                            Fork this chat into a new conversation. All messages up to this point will be preserved,
                            letting you explore an alternate line of inquiry.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        {branchingMessage && (
                            <div className="rounded-xl border bg-muted/30 p-2.5 text-xs text-muted-foreground line-clamp-3">
                                <span className="font-medium text-foreground">Branching from: </span>
                                {branchingMessage.content}
                            </div>
                        )}
                        <div className="space-y-1">
                            <label className="text-xs font-medium text-muted-foreground">New Branch Title</label>
                            <Input
                                value={branchTitle}
                                onChange={(e) => setBranchTitle(e.target.value)}
                                maxLength={200}
                                autoFocus
                                placeholder="Branch title"
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        e.preventDefault();
                                        handleConfirmBranch();
                                    }
                                }}
                            />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setBranchingMessage(null)}>
                            Cancel
                        </Button>
                        <Button
                            disabled={!branchTitle.trim() || branchSession.isPending}
                            onClick={handleConfirmBranch}
                        >
                            {branchSession.isPending ? <Spinner /> : null}
                            Create branch
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Report Dialog */}
            <Dialog open={Boolean(reportingMessage)} onOpenChange={(open) => !open && setReportingMessage(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Report AI response</DialogTitle>
                        <DialogDescription>
                            Provide feedback to help improve the quality and safety of GitBot answers.
                        </DialogDescription>
                    </DialogHeader>

                    {reportSuccess ? (
                        <div className="py-6 text-center space-y-3">
                            <CheckCircle2 className="size-10 text-emerald-500 mx-auto" />
                            <div>
                                <h3 className="text-sm font-semibold">Feedback Submitted</h3>
                                <p className="text-xs text-muted-foreground mt-1 max-w-xs mx-auto">
                                    Thank you! Your feedback has been recorded and will help improve future code grounding.
                                </p>
                            </div>
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setReportingMessage(null)}
                                className="mt-2"
                            >
                                Done
                            </Button>
                        </div>
                    ) : (
                        <>
                            <div className="space-y-4 py-2">
                                {reportError && (
                                    <div className="rounded-xl border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive flex items-center gap-2">
                                        <AlertCircle className="size-4 shrink-0" />
                                        <span data-slot="report-error-message">{reportError}</span>
                                    </div>
                                )}

                                <div className="space-y-2">
                                    <label className="text-xs font-medium text-foreground">Select Reason</label>
                                    <div className="space-y-1.5">
                                        {REPORT_REASONS.map((r) => {
                                            const isSelected = reportReason === r.value;
                                            return (
                                                <div
                                                    key={r.value}
                                                    onClick={() => setReportReason(r.value)}
                                                    className={cn(
                                                        "cursor-pointer rounded-xl border p-2.5 text-xs transition-colors",
                                                        isSelected
                                                            ? "border-primary bg-primary/5 text-foreground"
                                                            : "border-border hover:bg-muted/50 text-muted-foreground"
                                                    )}
                                                    role="radio"
                                                    aria-checked={isSelected}
                                                    tabIndex={0}
                                                    onKeyDown={(e) => {
                                                        if (e.key === "Enter" || e.key === " ") {
                                                            e.preventDefault();
                                                            setReportReason(r.value);
                                                        }
                                                    }}
                                                >
                                                    <div className="font-medium text-foreground">{r.label}</div>
                                                    <div className="text-[11px] opacity-80 mt-0.5">{r.desc}</div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                <div className="space-y-1.5">
                                    <div className="flex justify-between items-center text-xs">
                                        <label className="font-medium text-foreground">
                                            Additional Details (optional)
                                        </label>
                                        <span className="text-muted-foreground text-[11px]">
                                            {reportDetails.length} / 2000
                                        </span>
                                    </div>
                                    <Textarea
                                        value={reportDetails}
                                        onChange={(e) => setReportDetails(e.target.value)}
                                        placeholder="What went wrong or what would have been a better answer?"
                                        maxLength={2000}
                                        className="min-h-20 text-xs"
                                    />
                                </div>
                            </div>
                            <DialogFooter>
                                <Button variant="outline" onClick={() => setReportingMessage(null)}>
                                    Cancel
                                </Button>
                                <Button
                                    disabled={reportMessage.isPending}
                                    onClick={handleConfirmReport}
                                >
                                    {reportMessage.isPending ? <Spinner /> : null}
                                    Submit report
                                </Button>
                            </DialogFooter>
                        </>
                    )}
                </DialogContent>
            </Dialog>
        </AppShell>
    );
}