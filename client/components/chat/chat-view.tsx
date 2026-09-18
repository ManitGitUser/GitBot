"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ArrowLeft, Check, Copy, GitFork, Globe, Share2 } from "lucide-react";

import { ChatComposer } from "@/components/chat/chat-composer";
import { ChatMessages } from "@/components/chat/chat-messages";
import { ChatSidebar } from "@/components/chat/chat-sidebar";
import { IndexingState } from "@/components/chat/indexing-state";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
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
import type { ChatMessage, ReportReason } from "@/lib/api";

const REPORT_REASONS: { value: ReportReason; label: string }[] = [
    { value: "INCORRECT", label: "Incorrect answer" },
    { value: "IRRELEVANT", label: "Irrelevant answer" },
    { value: "UNSAFE", label: "Harmful or unsafe answer" },
    { value: "CITATION_ISSUE", label: "Citation problem" },
    { value: "OTHER", label: "Other issue" },
];

export function ChatView({ repoId }: { repoId: string }) {
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

    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const autoCreateRef = useRef(false);

    const activeSession =
        sessionsQuery.data?.find((s) => s.id === (selectedSessionId ?? sessionsQuery.data?.[0]?.id)) ??
        sessionsQuery.data?.[0] ??
        null;
    const sessionId = activeSession?.id ?? null;

    const messagesQuery = useChatMessages(sessionId);
    const { send, retry, stop, streaming, streamText } = useStreamChat(sessionId);

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

    useEffect(() => {
        if (!ready || sessionsQuery.isLoading) return;
        if (sessionsQuery.data && sessionsQuery.data.length > 0) return;
        if (
            !sessionsQuery.isSuccess ||
            (sessionsQuery.data?.length ?? 0) > 0 ||
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
        sessionsQuery.isLoading,
        sessionsQuery.isSuccess,
        sessionsQuery.data,
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
    }

    function handleConfirmReport() {
        if (!sessionId || !reportingMessage) return;
        reportMessage.mutate(
            {
                sessionId,
                messageId: reportingMessage.id,
                reason: reportReason,
                details: reportDetails.trim() || undefined,
            },
            {
                onSuccess: () => setReportingMessage(null),
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
                        type: "success",
                    });
                },
            });
        } else {
            shareSession.mutate(sessionId, {
                onSuccess: (share) => {
                    void sessionsQuery.refetch();
                    toast.add({
                        title: "Share link created",
                        description: `Public URL: ${share.shareUrl}`,
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
                    <Button render={<Link href="/dashboard" />}>Back to dashboard</Button>
                </div>
            </AppShell>
        );
    }

    const repo = repoQuery.data;

    return (
        <AppShell
            title={repo.fullName}
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
                            aria-label="Share chat"
                        >
                            <Share2 data-icon="inline-start" />
                            {activeSession?.isShared ? "Shared" : "Share"}
                        </Button>
                    )}
                    <Button variant="outline" size="sm" render={<Link href="/dashboard" />}>
                        <ArrowLeft data-icon="inline-start" />
                        Repos
                    </Button>
                </div>
            }
        >
            <div className="flex min-h-0 flex-1 flex-col md:flex-row">
                <ChatSidebar
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

                <section className="flex min-h-[70vh] min-w-0 flex-1 flex-col">
                    {!ready ? (
                        <IndexingState repo={repo} status={statusQuery.data} />
                    ) : (
                        <>
                            <ChatMessages
                                repo={repo}
                                messages={messagesQuery.data ?? []}
                                streamText={streamText}
                                isLoading={messagesQuery.isLoading}
                                streaming={streaming}
                                onRetry={(msgId) => retry(msgId)}
                                onBranch={handleStartBranch}
                                onReport={handleStartReport}
                            />
                            <ChatComposer
                                disabled={!sessionId}
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
                        <DialogTitle className="flex items-center gap-2">
                            <Globe className="size-5 text-primary" />
                            Share conversation
                        </DialogTitle>
                        <DialogDescription>
                            Create a public, read-only link to this conversation. Anyone with the link will be
                            able to view the questions, answers, and referenced repository code snippets.
                        </DialogDescription>
                    </DialogHeader>

                    {activeSession?.isShared && activeSession.shareToken ? (
                        <div className="space-y-4 py-2">
                            <div className="flex gap-2">
                                <Input
                                    readOnly
                                    value={
                                        typeof window !== "undefined"
                                            ? `${window.location.origin}/share/${activeSession.shareToken}`
                                            : `/share/${activeSession.shareToken}`
                                    }
                                    className="text-xs"
                                />
                                <Button size="sm" onClick={handleCopyShareUrl}>
                                    {copiedShare ? <Check className="size-4" /> : <Copy className="size-4" />}
                                    <span className="ml-1.5">{copiedShare ? "Copied" : "Copy"}</span>
                                </Button>
                            </div>
                            <p className="text-xs text-muted-foreground">
                                Sharing is currently active. You can revoke access at any time.
                            </p>
                        </div>
                    ) : (
                        <div className="py-2 text-sm text-muted-foreground">
                            Private by default. Clicking enable will create an unguessable public link.
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
                            {activeSession?.isShared ? "Revoke share" : "Enable share link"}
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
                            Create a new chat conversation starting from this message. All prior turns up to
                            this point will be copied over.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <label className="text-xs font-medium text-muted-foreground">Branch Title</label>
                        <Input
                            value={branchTitle}
                            onChange={(e) => setBranchTitle(e.target.value)}
                            maxLength={200}
                            placeholder="Title for branched conversation"
                            onKeyDown={(e) => {
                                if (e.key === "Enter") {
                                    e.preventDefault();
                                    handleConfirmBranch();
                                }
                            }}
                        />
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setBranchingMessage(null)}>
                            Cancel
                        </Button>
                        <Button
                            disabled={!branchTitle.trim() || branchSession.isPending}
                            onClick={handleConfirmBranch}
                        >
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
                            Help us improve GitBot by flagging incorrect, irrelevant, or unsafe answers.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <div>
                            <label className="text-xs font-medium text-muted-foreground">Reason</label>
                            <select
                                value={reportReason}
                                onChange={(e) => setReportReason(e.target.value as ReportReason)}
                                className="mt-1 flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                            >
                                {REPORT_REASONS.map((r) => (
                                    <option key={r.value} value={r.value}>
                                        {r.label}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="text-xs font-medium text-muted-foreground">
                                Details (optional)
                            </label>
                            <Textarea
                                value={reportDetails}
                                onChange={(e) => setReportDetails(e.target.value)}
                                placeholder="What went wrong with this response?"
                                maxLength={2000}
                                className="mt-1 min-h-20"
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
                            Submit report
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </AppShell>
    );
}