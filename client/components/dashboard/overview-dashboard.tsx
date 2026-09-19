"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
    AlertCircle,
    ArrowRight,
    CheckCircle2,
    Clock,
    FolderGit2,
    Layers,
    MessageSquare,
    RefreshCw,
    Share2,
    Sparkles,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useRepos, useSyncAllRepos } from "@/hooks/use-repos";
import { useRecentChatSessions } from "@/hooks/use-chat";
import { cn } from "@/lib/utils";

const LANGUAGE_COLORS: Record<string, string> = {
    TypeScript: "#3178c6",
    JavaScript: "#f7df1e",
    Python: "#3572A5",
    Java: "#b07219",
    Go: "#00ADD8",
    Rust: "#dea584",
    Ruby: "#701516",
    PHP: "#4F5D95",
    "C#": "#178600",
    "C++": "#f34b7d",
    C: "#555555",
    HTML: "#e34c26",
    CSS: "#563d7c",
    Shell: "#89e051",
    Kotlin: "#A97BFF",
    Swift: "#F05138",
};

function formatRelativeTime(dateString?: string | null): string {
    if (!dateString) return "";
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / (1000 * 60));
    if (diffMins < 1) return "just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function KpiCard({
                     label,
                     value,
                     hint,
                     icon: Icon,
                     badge,
                 }: {
    label: string;
    value: string | number;
    hint?: string;
    icon: typeof FolderGit2;
    badge?: React.ReactNode;
}) {
    return (
        <div className="flex flex-col justify-between rounded-xl border border-border/70 bg-card p-4 shadow-xs">
            <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-muted-foreground">{label}</span>
                <div className="flex items-center gap-1.5">
                    {badge}
                    <div className="rounded-md bg-muted/60 p-1.5 text-muted-foreground">
                        <Icon className="size-3.5" />
                    </div>
                </div>
            </div>
            <div className="mt-3">
                <div className="font-heading text-2xl font-bold tracking-tight text-foreground">
                    {value}
                </div>
                {hint && (
                    <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
                )}
            </div>
        </div>
    );
}

export function OverviewDashboard() {
    const reposQuery = useRepos(0, 100);
    const recentSessionsQuery = useRecentChatSessions(10);
    const syncAllMutation = useSyncAllRepos();

    const repos = useMemo(() => reposQuery.data?.content ?? [], [reposQuery.data?.content]);
    const totalRepos = reposQuery.data?.totalElements ?? repos.length;

    const readyCount = repos.filter((r) => r.indexStatus === "READY").length;
    const indexingCount = repos.filter((r) => r.indexStatus === "INDEXING").length;
    const pendingCount = repos.filter((r) => r.indexStatus === "PENDING").length;
    const failedCount = repos.filter((r) => r.indexStatus === "FAILED").length;
    const privateCount = repos.filter((r) => r.isPrivate).length;

    const readyPercentage = totalRepos > 0 ? Math.round((readyCount / totalRepos) * 100) : 0;
    const indexingPercentage = totalRepos > 0 ? Math.round((indexingCount / totalRepos) * 100) : 0;
    const pendingPercentage = totalRepos > 0 ? Math.round((pendingCount / totalRepos) * 100) : 0;
    const failedPercentage = totalRepos > 0 ? Math.round((failedCount / totalRepos) * 100) : 0;

    // Searchable chunks from READY repos
    const totalChunks = repos
        .filter((r) => r.indexStatus === "READY")
        .reduce((sum, r) => sum + (r.chunkCount || 0), 0);

    // Language breakdown
    const languageDistribution = useMemo(() => {
        if (!repos.length) return [];
        const counts: Record<string, number> = {};
        for (const repo of repos) {
            const lang = repo.language || "Other";
            counts[lang] = (counts[lang] || 0) + 1;
        }
        return Object.entries(counts)
            .map(([name, count]) => ({
                name,
                count,
                percentage: Math.round((count / repos.length) * 100),
                color: LANGUAGE_COLORS[name] || "#8b949e",
            }))
            .sort((a, b) => b.count - a.count);
    }, [repos]);

    const recentSessions = useMemo(() => recentSessionsQuery.data ?? [], [recentSessionsQuery.data]);

    // Recent conversations grouped by repository (representing latest 10 sessions)
    const recentConversationsByRepo = useMemo(() => {
        if (!recentSessions.length) return [];
        const map = new Map<string, { repoName: string; count: number; repoId: string }>();
        for (const session of recentSessions) {
            const repo = repos.find((r) => r.id === session.repositoryId);
            const name = repo?.fullName || repo?.name || "Repository";
            const current = map.get(session.repositoryId) || {
                repoName: name,
                count: 0,
                repoId: session.repositoryId,
            };
            current.count += 1;
            map.set(session.repositoryId, current);
        }
        return Array.from(map.values()).sort((a, b) => b.count - a.count);
    }, [recentSessions, repos]);

    return (
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
            {/* Top KPI Row */}
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                {reposQuery.isLoading ? (
                    Array.from({ length: 4 }).map((_, index) => (
                        <Skeleton key={index} className="h-28 rounded-xl" />
                    ))
                ) : (
                    <>
                        <KpiCard
                            label="Total Repositories"
                            value={totalRepos}
                            hint={`${readyCount} indexed · ${privateCount} private`}
                            icon={FolderGit2}
                        />
                        <KpiCard
                            label="Repositories Ready"
                            value={readyCount}
                            hint={`${readyPercentage}% of connected repos ready`}
                            icon={CheckCircle2}
                            badge={
                                readyPercentage > 0 && (
                                    <span className="rounded-md bg-emerald-500/10 px-1.5 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-400">
                                        {readyPercentage}%
                                    </span>
                                )
                            }
                        />
                        <KpiCard
                            label="Searchable Chunks"
                            value={totalChunks.toLocaleString()}
                            hint={`Vector embeddings across ${readyCount} repos`}
                            icon={Layers}
                        />
                        <KpiCard
                            label="Indexing Status"
                            value={
                                failedCount > 0
                                    ? `${failedCount} Failed`
                                    : indexingCount > 0
                                        ? `${indexingCount} Indexing`
                                        : pendingCount > 0
                                            ? `${pendingCount} Pending`
                                            : "All Ready"
                            }
                            hint={`${indexingCount} indexing · ${pendingCount} pending · ${failedCount} failed`}
                            icon={failedCount > 0 ? AlertCircle : Sparkles}
                            badge={
                                failedCount > 0 ? (
                                    <span className="rounded-md bg-destructive/10 px-1.5 py-0.5 text-[11px] font-medium text-destructive">
                                        Action needed
                                    </span>
                                ) : undefined
                            }
                        />
                    </>
                )}
            </div>

            {/* Analytics Section: Indexing Status Breakdown & Language Distribution */}
            <div className="grid gap-6 lg:grid-cols-2">
                {/* Indexing Status Breakdown */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <div className="flex items-center justify-between gap-2">
                            <div>
                                <CardTitle className="font-heading text-sm font-semibold">
                                    Repository Indexing Status
                                </CardTitle>
                                <CardDescription className="text-xs">
                                    Current state of all {totalRepos} connected repositories
                                </CardDescription>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={syncAllMutation.isPending}
                                onClick={() => syncAllMutation.mutate()}
                                title="Sync all repositories"
                                className="h-7 gap-1.5 text-xs font-medium"
                            >
                                <RefreshCw className={cn("size-3", syncAllMutation.isPending && "animate-spin")} />
                                <span>{syncAllMutation.isPending ? "Syncing…" : "Sync All"}</span>
                            </Button>
                        </div>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-1">
                        {reposQuery.isLoading ? (
                            <Skeleton className="h-24 rounded-lg" />
                        ) : (
                            <>
                                {/* Segmented Progress Bar */}
                                <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted/60">
                                    {readyPercentage > 0 && (
                                        <div
                                            style={{ width: `${readyPercentage}%` }}
                                            className="bg-emerald-500 transition-all"
                                            title={`Ready: ${readyCount} (${readyPercentage}%)`}
                                        />
                                    )}
                                    {indexingPercentage > 0 && (
                                        <div
                                            style={{ width: `${indexingPercentage}%` }}
                                            className="bg-sky-500 transition-all"
                                            title={`Indexing: ${indexingCount} (${indexingPercentage}%)`}
                                        />
                                    )}
                                    {pendingPercentage > 0 && (
                                        <div
                                            style={{ width: `${pendingPercentage}%` }}
                                            className="bg-amber-500 transition-all"
                                            title={`Pending: ${pendingCount} (${pendingPercentage}%)`}
                                        />
                                    )}
                                    {failedPercentage > 0 && (
                                        <div
                                            style={{ width: `${failedPercentage}%` }}
                                            className="bg-rose-500 transition-all"
                                            title={`Failed: ${failedCount} (${failedPercentage}%)`}
                                        />
                                    )}
                                </div>

                                {/* Status Legend Grid */}
                                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/30 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-emerald-500" />
                                            <span>Ready</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {readyCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {readyPercentage}% of total
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/30 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-sky-500" />
                                            <span>Indexing</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {indexingCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {indexingPercentage}% of total
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/30 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-amber-500" />
                                            <span>Pending</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {pendingCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {pendingPercentage}% of total
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/30 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-rose-500" />
                                            <span>Failed</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {failedCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {failedPercentage}% of total
                                        </span>
                                    </div>
                                </div>
                            </>
                        )}
                    </CardContent>
                </Card>

                {/* Language Distribution */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <CardTitle className="font-heading text-sm font-semibold">
                            Language Distribution
                        </CardTitle>
                        <CardDescription className="text-xs">
                            Primary programming languages across connected repositories
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-1">
                        {reposQuery.isLoading ? (
                            <Skeleton className="h-24 rounded-lg" />
                        ) : languageDistribution.length === 0 ? (
                            <p className="text-xs text-muted-foreground">No language data available.</p>
                        ) : (
                            <>
                                {/* Segmented Language Bar */}
                                <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted/60">
                                    {languageDistribution.slice(0, 6).map((lang) => (
                                        <div
                                            key={lang.name}
                                            style={{
                                                width: `${lang.percentage}%`,
                                                backgroundColor: lang.color,
                                            }}
                                            className="transition-all"
                                            title={`${lang.name}: ${lang.count} (${lang.percentage}%)`}
                                        />
                                    ))}
                                </div>

                                {/* Top Languages List */}
                                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                                    {languageDistribution.slice(0, 6).map((lang) => (
                                        <div
                                            key={lang.name}
                                            className="flex items-center justify-between rounded-lg border border-border/60 bg-muted/20 px-2.5 py-1.5 text-xs"
                                        >
                                            <div className="flex items-center gap-1.5 truncate">
                                                <span
                                                    className="size-2 shrink-0 rounded-full"
                                                    style={{ backgroundColor: lang.color }}
                                                />
                                                <span className="truncate font-medium">{lang.name}</span>
                                            </div>
                                            <span className="ml-1 shrink-0 text-muted-foreground">
                                                {lang.count} ({lang.percentage}%)
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </CardContent>
                </Card>
            </div>

            {/* Bottom Section: Recent Conversations & Activity by Repo */}
            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
                {/* Recent Conversations */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <div className="flex items-center justify-between gap-2">
                            <div>
                                <CardTitle className="font-heading text-sm font-semibold">
                                    Recent Conversations
                                </CardTitle>
                                <CardDescription className="text-xs">
                                    Jump back into your recent chat sessions
                                </CardDescription>
                            </div>
                            <Link
                                href="/dashboard"
                                className="text-xs font-medium text-primary hover:underline"
                            >
                                All repositories
                            </Link>
                        </div>
                    </CardHeader>
                    <CardContent className="pt-1">
                        {recentSessionsQuery.isLoading ? (
                            <div className="space-y-2">
                                {Array.from({ length: 4 }).map((_, index) => (
                                    <Skeleton key={index} className="h-12 rounded-lg" />
                                ))}
                            </div>
                        ) : recentSessions.length === 0 ? (
                            <div className="flex flex-col items-center justify-center gap-2 py-8 text-center">
                                <MessageSquare className="size-8 text-muted-foreground/40" />
                                <p className="text-sm font-medium text-foreground">No conversations yet</p>
                                <p className="text-xs text-muted-foreground max-w-sm">
                                    Open any indexed repository from the Repositories page to start chatting with your codebase.
                                </p>
                                <Link
                                    href="/dashboard"
                                    className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
                                >
                                    Browse repositories
                                    <ArrowRight className="size-3" />
                                </Link>
                            </div>
                        ) : (
                            <div className="divide-y divide-border/60 overflow-hidden rounded-lg border border-border/60">
                                {recentSessions.map((session) => {
                                    const repo = repos.find((r) => r.id === session.repositoryId);
                                    return (
                                        <Link
                                            key={session.id}
                                            href={`/chat/${session.repositoryId}?sessionId=${session.id}`}
                                            className="group flex items-center justify-between gap-3 p-3 transition-colors hover:bg-muted/40"
                                        >
                                            <div className="flex min-w-0 items-center gap-2.5">
                                                <div className="rounded-md bg-muted/60 p-1.5 text-muted-foreground group-hover:text-foreground">
                                                    <MessageSquare className="size-3.5" />
                                                </div>
                                                <div className="min-w-0">
                                                    <p className="truncate text-xs font-medium text-foreground group-hover:text-primary">
                                                        {session.title || "Untitled conversation"}
                                                    </p>
                                                    <p className="truncate text-[11px] text-muted-foreground">
                                                        {repo?.fullName || "Repository"}
                                                    </p>
                                                </div>
                                            </div>

                                            <div className="flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
                                                {session.isShared && (
                                                    <span className="inline-flex items-center gap-1 rounded bg-muted/50 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                                                        <Share2 className="size-2.5" />
                                                        Shared
                                                    </span>
                                                )}
                                                <span className="inline-flex items-center gap-1 text-[11px]">
                                                    <Clock className="size-3" />
                                                    {formatRelativeTime(session.createdAt)}
                                                </span>
                                                <ArrowRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
                                            </div>
                                        </Link>
                                    );
                                })}
                            </div>
                        )}
                    </CardContent>
                </Card>

                {/* Recent Conversations by Repository */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <CardTitle className="font-heading text-sm font-semibold">
                            Recent Chat Activity
                        </CardTitle>
                        <CardDescription className="text-xs">
                            Repositories active in your latest 10 chat sessions
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="pt-1">
                        {recentSessionsQuery.isLoading ? (
                            <div className="space-y-2">
                                {Array.from({ length: 3 }).map((_, index) => (
                                    <Skeleton key={index} className="h-10 rounded-lg" />
                                ))}
                            </div>
                        ) : recentConversationsByRepo.length === 0 ? (
                            <div className="py-6 text-center text-xs text-muted-foreground">
                                No active repositories yet.
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {recentConversationsByRepo.map((item) => (
                                    <div
                                        key={item.repoId}
                                        className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-muted/20 p-2.5 text-xs transition-colors hover:bg-muted/40"
                                    >
                                        <div className="min-w-0 flex-1">
                                            <p className="truncate font-medium text-foreground">
                                                {item.repoName}
                                            </p>
                                            <p className="text-[11px] text-muted-foreground">
                                                {item.count} {item.count === 1 ? "session" : "sessions"} in recent chats
                                            </p>
                                        </div>
                                        <Link
                                            href={`/chat/${item.repoId}`}
                                            className="inline-flex items-center gap-1 rounded-md border border-border/80 bg-background px-2 py-1 text-[11px] font-medium text-foreground shadow-2xs hover:bg-muted"
                                        >
                                            <span>Chat</span>
                                            <ArrowRight className="size-2.5" />
                                        </Link>
                                    </div>
                                ))}
                            </div>
                        )}
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}