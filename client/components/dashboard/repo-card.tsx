"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
    ArrowRight,
    ExternalLink,
    GitBranch,
    Lock,
    MessageSquare,
    RefreshCw,
    RotateCcw,
    Sparkles,
    Trash2,
} from "lucide-react";

import { IndexErrorAlert } from "@/components/dashboard/index-error-alert";
import { IndexStatusBadge } from "@/components/dashboard/repo-status";
import { LanguageIcon } from "@/components/icons/language-icon";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Spinner } from "@/components/ui/spinner";
import { getRepoProgress, useRemoveIndex, useStartIndexing, useSyncRepo } from "@/hooks/use-repos";
import type { Repository } from "@/lib/api";
import { cn } from "@/lib/utils";

export function RepoCard({ repo }: { repo: Repository }) {
    const router = useRouter();
    const indexMutation = useStartIndexing();
    const removeIndexMutation = useRemoveIndex();
    const syncMutation = useSyncRepo();
    const [confirmRemoveIndexOpen, setConfirmRemoveIndexOpen] = useState(false);
    const hasNewCommit = Boolean(
        repo.latestCommitSha &&
        repo.indexedCommitSha &&
        repo.latestCommitSha !== repo.indexedCommitSha
    );
    const isSyncing = syncMutation.isPending;
    const isIndexing = repo.indexStatus === "INDEXING" || indexMutation.isPending;
    const isFailed = repo.indexStatus === "FAILED";
    const progress = getRepoProgress(repo);

    function openChat() {
        router.push(`/chat/${repo.id}`);
    }

    function openWorkspace() {
        router.push(`/chat/${repo.id}?mode=open`);
    }

    function handlePrimary() {
        if (repo.indexStatus === "READY") {
            openWorkspace();
            return;
        }
        indexMutation.mutate(repo.id, {
            onSuccess: () => router.push(`/chat/${repo.id}?mode=open`),
        });
    }

    return (
        <article
            className={cn(
                "group relative flex flex-col justify-between overflow-hidden rounded-xl border bg-card text-card-foreground shadow-2xs transition-all hover:border-ring/30 hover:shadow-sm"
            )}
        >
            <div className="border-b border-border/60 p-3.5 pb-2.5">
                <div className="flex items-start justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-2">
                        <div className="min-w-0">
                            <p className="truncate text-xs text-muted-foreground">{repo.owner}</p>
                            <h3 className="truncate font-semibold text-sm">{repo.name}</h3>
                        </div>
                    </div>
                    <IndexStatusBadge status={repo.indexStatus} hasNewCommit={hasNewCommit} />
                </div>
            </div>

            <div className="flex flex-1 flex-col gap-2.5 p-3.5">
                {!isFailed && (
                    <p className="line-clamp-2 text-xs text-muted-foreground">
                        {repo.description || "No description provided."}
                    </p>
                )}

                {isFailed && repo.description && (
                    <p className="line-clamp-2 text-xs text-muted-foreground">
                        {repo.description}
                    </p>
                )}

                <div className="flex flex-wrap items-center gap-1.5 pt-1">
                    {repo.isPrivate && (
                        <span className="inline-flex items-center gap-1 rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">
                            <Lock className="size-3" />
                            Private
                        </span>
                    )}
                    <span className="inline-flex items-center gap-1 rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">
                        <GitBranch className="size-3" />
                        {repo.defaultBranch}
                    </span>
                    {repo.language && (
                        <span className="inline-flex items-center gap-1.5 rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 text-[11px] font-medium">
                            <LanguageIcon language={repo.language} size="sm" />
                            {repo.language}
                        </span>
                    )}
                    {repo.chunkCount > 0 && (
                        <span
                            className={cn(
                                "rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 text-[11px] font-medium",
                                isFailed
                                    ? "border-destructive/20 text-destructive/80"
                                    : "text-muted-foreground"
                            )}
                        >
                            {repo.chunkCount.toLocaleString()} chunks
                            {isFailed ? " indexed" : ""}
                        </span>
                    )}
                </div>

                {hasNewCommit && !isIndexing && (
                    <div className="flex items-center gap-1.5 rounded-lg border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-xs text-amber-700 dark:text-amber-400">
                        <GitBranch className="size-3.5 shrink-0" />
                        <span className="truncate">New commit ({repo.latestCommitSha?.slice(0, 7)})</span>
                    </div>
                )}

                {isIndexing && (
                    <div className="space-y-1.5 rounded-lg border border-border/60 bg-muted/30 p-2.5">
                        <div className="flex items-center justify-between text-xs text-muted-foreground">
                            <span>Indexing…</span>
                            <span>
                                {repo.filesProcessed}/{repo.filesTotal || "?"}
                            </span>
                        </div>
                        <Progress value={progress || 8} className="h-1.5" />
                    </div>
                )}

                {isFailed && repo.errorMessage && (
                    <IndexErrorAlert message={repo.errorMessage} />
                )}
            </div>

            <div className="flex items-center justify-between border-t border-border/60 bg-muted/20 px-3.5 py-2.5">
                {repo.htmlUrl ? (
                    <a
                        href={repo.htmlUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                    >
                        <ExternalLink className="size-3" />
                        GitHub
                    </a>
                ) : (
                    <span />
                )}

                <div className="flex flex-wrap items-center gap-2">
                    {repo.indexStatus === "READY" && (
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={isIndexing || isSyncing}
                            onClick={() => syncMutation.mutate(repo.id)}
                            title="Sync repository with latest GitHub commits"
                        >
                            {isSyncing ? (
                                <Spinner data-icon="inline-start" />
                            ) : (
                                <RefreshCw data-icon="inline-start" />
                            )}
                            {isSyncing ? "Syncing…" : "Sync"}
                        </Button>
                    )}
                    {repo.indexStatus === "READY" && (
                        <Button variant="secondary" size="sm" onClick={openChat}>
                            <MessageSquare data-icon="inline-start" />
                            Chat
                        </Button>
                    )}
                    {repo.indexStatus === "READY" && (
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={isIndexing || isSyncing || removeIndexMutation.isPending}
                            onClick={() => setConfirmRemoveIndexOpen(true)}
                            title="Remove repository index"
                            className="text-muted-foreground hover:text-destructive hover:border-destructive/40"
                        >
                            <Trash2 className="size-3.5" />
                            <span className="hidden sm:inline">Remove Index</span>
                        </Button>
                    )}
                    {hasNewCommit && repo.indexStatus === "READY" ? (
                        <Button
                            size="sm"
                            variant="default"
                            disabled={isIndexing || isSyncing}
                            onClick={() => indexMutation.mutate(repo.id)}
                            title="Reindex the new commit from GitHub"
                        >
                            {isIndexing ? (
                                <>
                                    <Spinner data-icon="inline-start" />
                                    Indexing
                                </>
                            ) : (
                                <>
                                    <Sparkles data-icon="inline-start" />
                                    Reindex
                                </>
                            )}
                        </Button>
                    ) : (
                        <Button
                            size="sm"
                            variant={isFailed ? "outline" : "default"}
                            className={cn(isFailed && "border-destructive/30 text-destructive hover:bg-destructive/10")}
                            disabled={isIndexing || isSyncing}
                            onClick={handlePrimary}
                        >
                            {isIndexing ? (
                                <>
                                    <Spinner data-icon="inline-start" />
                                    Indexing
                                </>
                            ) : repo.indexStatus === "READY" ? (
                                <>
                                    Open
                                    <ArrowRight data-icon="inline-end" />
                                </>
                            ) : isFailed ? (
                                <>
                                    <RotateCcw data-icon="inline-start" />
                                    Retry
                                </>
                            ) : (
                                <>
                                    <Sparkles data-icon="inline-start" />
                                    Index
                                </>
                            )}
                        </Button>
                    )}
                </div>
            </div>

            {/* Remove Index Confirmation Alert Dialog */}
            <AlertDialog open={confirmRemoveIndexOpen} onOpenChange={setConfirmRemoveIndexOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Remove repository index?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This will permanently remove the indexed code chunks for this repository and free database storage.
                            Your repository connection and existing conversations will not be deleted.
                            You can index the repository again later.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onClick={() => setConfirmRemoveIndexOpen(false)}>
                            Cancel
                        </AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={removeIndexMutation.isPending}
                            onClick={() => {
                                removeIndexMutation.mutate(repo.id, {
                                    onSuccess: () => setConfirmRemoveIndexOpen(false),
                                });
                            }}
                        >
                            {removeIndexMutation.isPending ? <Spinner className="size-3.5" /> : null}
                            Remove Index
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </article>
    );
}