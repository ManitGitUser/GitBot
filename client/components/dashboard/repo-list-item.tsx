"use client";

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
} from "lucide-react";

import { LanguageBadge } from "@/components/dashboard/language-badge";
import { IndexStatusBadge } from "@/components/dashboard/repo-status";
import { LanguageIcon } from "@/components/icons/language-icon";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Spinner } from "@/components/ui/spinner";
import { getRepoProgress, useStartIndexing, useSyncRepo } from "@/hooks/use-repos";
import type { Repository } from "@/lib/api";
import { cn } from "@/lib/utils";

export function RepoListItem({ repo }: { repo: Repository }) {
    const router = useRouter();
    const indexMutation = useStartIndexing();
    const syncMutation = useSyncRepo();
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
        <div
            className={cn(
                "group relative flex flex-col gap-3 p-4 transition-colors hover:bg-muted/40 sm:p-4.5 md:flex-row md:items-center md:justify-between md:gap-6",
                isFailed && "bg-destructive/[0.02] hover:bg-destructive/[0.05]"
            )}
        >
            {/* Left side: Repository info & metadata */}
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                <div className="flex flex-wrap items-center gap-2">
                    <LanguageBadge language={repo.language} showLabel={false} />
                    <button
                        type="button"
                        onClick={openWorkspace}
                        className="truncate text-left font-medium text-foreground hover:text-primary hover:underline"
                    >
                        <span className="text-muted-foreground">{repo.owner} / </span>
                        <span className="font-semibold">{repo.name}</span>
                    </button>

                    {repo.isPrivate && (
                        <span className="inline-flex items-center gap-1 rounded-md border border-border/80 bg-muted/50 px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">
                            <Lock className="size-3" />
                            Private
                        </span>
                    )}

                    <IndexStatusBadge status={repo.indexStatus} hasNewCommit={hasNewCommit} />

                    {hasNewCommit && !isIndexing && (
                        <span className="inline-flex items-center gap-1 rounded-md border border-amber-500/20 bg-amber-500/10 px-1.5 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-400">
                            <GitBranch className="size-3" />
                            New commit ({repo.latestCommitSha?.slice(0, 7)})
                        </span>
                    )}
                </div>

                {repo.description && (
                    <p className="line-clamp-1 text-xs text-muted-foreground md:text-sm">
                        {repo.description}
                    </p>
                )}

                {isFailed && repo.errorMessage && (
                    <p className="line-clamp-1 text-xs text-destructive">
                        Error: {repo.errorMessage}
                    </p>
                )}

                {/* Metadata details row */}
                <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1">
                        <GitBranch className="size-3" />
                        {repo.defaultBranch}
                    </span>

                    {repo.language && (
                        <span className="inline-flex items-center gap-1.5">
                            <LanguageIcon language={repo.language} size="sm" />
                            {repo.language}
                        </span>
                    )}

                    {repo.chunkCount > 0 && (
                        <span>
                            {repo.chunkCount.toLocaleString()} chunks
                        </span>
                    )}

                    {repo.indexedAt && (
                        <span>
                            Indexed {new Date(repo.indexedAt).toLocaleDateString(undefined, {
                                month: "short",
                                day: "numeric",
                            })}
                        </span>
                    )}

                    {repo.htmlUrl && (
                        <a
                            href={repo.htmlUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 hover:text-foreground"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <ExternalLink className="size-3" />
                            GitHub
                        </a>
                    )}
                </div>

                {/* Progress bar if indexing */}
                {isIndexing && (
                    <div className="mt-1 flex max-w-xs items-center gap-2">
                        <Progress value={progress || 10} className="h-1.5 flex-1" />
                        <span className="text-[11px] text-muted-foreground">
                            {repo.filesProcessed}/{repo.filesTotal || "?"}
                        </span>
                    </div>
                )}
            </div>

            {/* Right side: Actions */}
            <div className="flex shrink-0 items-center gap-2 self-start pt-1 md:self-center md:pt-0">
                {repo.indexStatus === "READY" && (
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={isIndexing || isSyncing}
                        onClick={() => syncMutation.mutate(repo.id)}
                        title="Sync repository with latest GitHub commits"
                        className="h-8 text-xs font-medium"
                    >
                        {isSyncing ? (
                            <Spinner className="size-3.5" />
                        ) : (
                            <RefreshCw className="size-3.5" />
                        )}
                        <span className="hidden sm:inline">{isSyncing ? "Syncing…" : "Sync"}</span>
                    </Button>
                )}

                {repo.indexStatus === "READY" && (
                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={openChat}
                        className="h-8 text-xs font-medium"
                    >
                        <MessageSquare className="size-3.5" />
                        <span>Chat</span>
                    </Button>
                )}

                {hasNewCommit && repo.indexStatus === "READY" ? (
                    <Button
                        size="sm"
                        variant="default"
                        disabled={isIndexing || isSyncing}
                        onClick={() => indexMutation.mutate(repo.id)}
                        title="Reindex the new commit from GitHub"
                        className="h-8 text-xs font-medium"
                    >
                        {isIndexing ? (
                            <>
                                <Spinner className="size-3.5" />
                                <span>Indexing</span>
                            </>
                        ) : (
                            <>
                                <Sparkles className="size-3.5" />
                                <span>Reindex</span>
                            </>
                        )}
                    </Button>
                ) : (
                    <Button
                        size="sm"
                        variant={isFailed ? "outline" : "default"}
                        className={cn(
                            "h-8 text-xs font-medium",
                            isFailed && "border-destructive/30 text-destructive hover:bg-destructive/10"
                        )}
                        disabled={isIndexing || isSyncing}
                        onClick={handlePrimary}
                    >
                        {isIndexing ? (
                            <>
                                <Spinner className="size-3.5" />
                                <span>Indexing</span>
                            </>
                        ) : repo.indexStatus === "READY" ? (
                            <>
                                <span>Open</span>
                                <ArrowRight className="size-3.5" />
                            </>
                        ) : isFailed ? (
                            <>
                                <RotateCcw className="size-3.5" />
                                <span>Retry</span>
                            </>
                        ) : (
                            <>
                                <Sparkles className="size-3.5" />
                                <span>Index</span>
                            </>
                        )}
                    </Button>
                )}
            </div>
        </div>
    );
}
