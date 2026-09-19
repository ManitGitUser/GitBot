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
import {
    PieChart,
    Pie,
    Cell,
    Label,
    BarChart,
    Bar,
    XAxis,
    YAxis,
    ResponsiveContainer,
    Tooltip,
    LabelList,
} from "recharts";

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

    // Donut chart data for indexing status
    const indexingChartData = useMemo(() => {
        if (totalRepos === 0) {
            return [{ name: "No Repositories", value: 1, color: "var(--muted)", percentage: 0 }];
        }
        return [
            { name: "Ready", value: readyCount, color: "#10b981", percentage: readyPercentage },
            { name: "Indexing", value: indexingCount, color: "#0ea5e9", percentage: indexingPercentage },
            { name: "Pending", value: pendingCount, color: "#f59e0b", percentage: pendingPercentage },
            { name: "Failed", value: failedCount, color: "#f43f5e", percentage: failedPercentage },
        ].filter((item) => item.value > 0);
    }, [totalRepos, readyCount, indexingCount, pendingCount, failedCount, readyPercentage, indexingPercentage, pendingPercentage, failedPercentage]);

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
                color: LANGUAGE_COLORS[name] || "#64748b",
            }))
            .sort((a, b) => b.count - a.count);
    }, [repos]);

    const recentSessions = useMemo(() => recentSessionsQuery.data ?? [], [recentSessionsQuery.data]);

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

            {/* Analytics Section: Repository Indexing Status (Donut Chart) & Language Distribution (Horizontal Bar Chart) */}
            <div className="grid gap-6 lg:grid-cols-2">
                {/* Repository Indexing Status Donut Chart */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <div className="flex items-center justify-between gap-2">
                            <div>
                                <CardTitle className="font-heading text-sm font-semibold">
                                    Repository Indexing Status
                                </CardTitle>
                                <CardDescription className="text-xs">
                                    Distribution of index states across {totalRepos} connected repositories
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
                            <Skeleton className="h-56 rounded-lg" />
                        ) : (
                            <div className="flex flex-col items-center">
                                {/* Donut Chart */}
                                <div className="h-48 w-full">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Tooltip
                                                content={({ active, payload }) => {
                                                    if (active && payload && payload.length) {
                                                        const data = payload[0].payload;
                                                        if (totalRepos === 0) return null;
                                                        return (
                                                            <div className="rounded-lg border border-border bg-popover px-3 py-1.5 text-xs shadow-md">
                                                                <div className="flex items-center gap-1.5">
                                                                    <span
                                                                        className="size-2 rounded-full"
                                                                        style={{ backgroundColor: data.color }}
                                                                    />
                                                                    <span className="font-semibold text-foreground">{data.name}</span>
                                                                </div>
                                                                <p className="mt-1 text-muted-foreground">
                                                                    {data.value} {data.value === 1 ? "repository" : "repositories"} ({data.percentage}%)
                                                                </p>
                                                            </div>
                                                        );
                                                    }
                                                    return null;
                                                }}
                                            />
                                            <Pie
                                                data={indexingChartData}
                                                dataKey="value"
                                                nameKey="name"
                                                innerRadius={55}
                                                outerRadius={75}
                                                paddingAngle={indexingChartData.length > 1 ? 3 : 0}
                                                strokeWidth={2}
                                                stroke="var(--background)"
                                            >
                                                {indexingChartData.map((entry, index) => (
                                                    <Cell key={`cell-${index}`} fill={entry.color} />
                                                ))}
                                                <Label
                                                    content={({ viewBox }) => {
                                                        if (viewBox && "cx" in viewBox && "cy" in viewBox) {
                                                            return (
                                                                <text
                                                                    x={viewBox.cx}
                                                                    y={viewBox.cy}
                                                                    textAnchor="middle"
                                                                    dominantBaseline="middle"
                                                                >
                                                                    <tspan
                                                                        x={viewBox.cx}
                                                                        y={(viewBox.cy || 0) - 3}
                                                                        className="fill-foreground font-heading text-2xl font-bold"
                                                                    >
                                                                        {totalRepos}
                                                                    </tspan>
                                                                    <tspan
                                                                        x={viewBox.cx}
                                                                        y={(viewBox.cy || 0) + 18}
                                                                        className="fill-muted-foreground text-[11px] font-medium"
                                                                    >
                                                                        Total Repos
                                                                    </tspan>
                                                                </text>
                                                            );
                                                        }
                                                        return null;
                                                    }}
                                                />
                                            </Pie>
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>

                                {/* Status Legend Grid */}
                                <div className="grid w-full grid-cols-2 gap-2.5 pt-2 sm:grid-cols-4">
                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/20 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-emerald-500" />
                                            <span>Ready</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {readyCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {readyPercentage}%
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/20 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-sky-500" />
                                            <span>Indexing</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {indexingCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {indexingPercentage}%
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/20 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-amber-500" />
                                            <span>Pending</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {pendingCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {pendingPercentage}%
                                        </span>
                                    </div>

                                    <div className="flex flex-col rounded-lg border border-border/60 bg-muted/20 p-2.5">
                                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                            <span className="size-2 rounded-full bg-rose-500" />
                                            <span>Failed</span>
                                        </div>
                                        <span className="mt-1 font-heading text-lg font-bold text-foreground">
                                            {failedCount}
                                        </span>
                                        <span className="text-[11px] text-muted-foreground">
                                            {failedPercentage}%
                                        </span>
                                    </div>
                                </div>
                            </div>
                        )}
                    </CardContent>
                </Card>

                {/* Repository Languages Horizontal Bar Chart */}
                <Card className="rounded-xl border border-border/70 shadow-xs">
                    <CardHeader className="pb-3">
                        <CardTitle className="font-heading text-sm font-semibold">
                            Repository Languages
                        </CardTitle>
                        <CardDescription className="text-xs">
                            Distribution of primary programming languages across repositories
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="pt-1">
                        {reposQuery.isLoading ? (
                            <Skeleton className="h-56 rounded-lg" />
                        ) : languageDistribution.length === 0 ? (
                            <div className="flex h-56 flex-col items-center justify-center text-center">
                                <p className="text-xs text-muted-foreground">No language data available.</p>
                            </div>
                        ) : (
                            <div className="h-56 w-full">
                                <ResponsiveContainer width="100%" height="100%">
                                    <BarChart
                                        data={languageDistribution.slice(0, 6)}
                                        layout="vertical"
                                        margin={{ top: 8, right: 45, left: 10, bottom: 8 }}
                                    >
                                        <XAxis type="number" hide />
                                        <YAxis
                                            dataKey="name"
                                            type="category"
                                            tickLine={false}
                                            axisLine={false}
                                            width={85}
                                            tick={{ fontSize: 12, fill: "var(--foreground)" }}
                                        />
                                        <Tooltip
                                            content={({ active, payload }) => {
                                                if (active && payload && payload.length) {
                                                    const data = payload[0].payload;
                                                    return (
                                                        <div className="rounded-lg border border-border bg-popover px-3 py-1.5 text-xs shadow-md">
                                                            <div className="flex items-center gap-1.5">
                                                                <span
                                                                    className="size-2 rounded-full"
                                                                    style={{ backgroundColor: data.color }}
                                                                />
                                                                <span className="font-semibold text-foreground">{data.name}</span>
                                                            </div>
                                                            <p className="mt-1 text-muted-foreground">
                                                                {data.count} {data.count === 1 ? "repository" : "repositories"} ({data.percentage}%)
                                                            </p>
                                                        </div>
                                                    );
                                                }
                                                return null;
                                            }}
                                        />
                                        <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                                            {languageDistribution.slice(0, 6).map((entry, index) => (
                                                <Cell key={`lang-cell-${index}`} fill={entry.color} />
                                            ))}
                                            <LabelList
                                                dataKey="count"
                                                position="right"
                                                formatter={(val: unknown) => {
                                                    const count = Number(val);
                                                    const pct = totalRepos > 0 ? Math.round((count / totalRepos) * 100) : 0;
                                                    return `${count} (${pct}%)`;
                                                }}
                                                className="fill-muted-foreground text-[11px] font-medium"
                                            />
                                        </Bar>
                                    </BarChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </CardContent>
                </Card>
            </div>

            {/* Bottom Section: Single Useful Recent Conversations Section */}
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
        </div>
    );
}