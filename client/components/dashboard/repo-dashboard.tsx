"use client";

import { useState } from "react";
import { FolderGit2 } from "lucide-react";

import { DashboardHeader } from "@/components/dashboard/dashboard-header";
import { RepoCard } from "@/components/dashboard/repo-card";
import { RepoListItem } from "@/components/dashboard/repo-list-item";
import { Button, buttonVariants } from "@/components/ui/button";
import {
    Empty,
    EmptyDescription,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { useRepos, useSyncAllRepos } from "@/hooks/use-repos";
import { ApiError, getGithubLoginUrl, type IndexStatus } from "@/lib/api";

type FilterStatus = "ALL" | IndexStatus;

const VIEW_MODE_STORAGE_KEY = "gitbot_repo_view_mode";
const STATUS_STORAGE_KEY = "gitbot_repo_filter_status";
const VISIBILITY_STORAGE_KEY = "gitbot_repo_filter_visibility";

export function RepoDashboard() {
    const [page, setPage] = useState(0);
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState<FilterStatus>(() => {
        if (typeof window === "undefined") return "ALL";
        try {
            const saved = localStorage.getItem(STATUS_STORAGE_KEY);
            if (
                saved === "ALL" ||
                saved === "READY" ||
                saved === "INDEXING" ||
                saved === "PENDING" ||
                saved === "FAILED"
            ) {
                return saved as FilterStatus;
            }
        } catch {
            // ignore
        }
        return "ALL";
    });
    const [visibility, setVisibility] = useState<"all" | "public" | "private">(() => {
        if (typeof window === "undefined") return "all";
        try {
            const saved = localStorage.getItem(VISIBILITY_STORAGE_KEY);
            if (saved === "all" || saved === "public" || saved === "private") {
                return saved as "all" | "public" | "private";
            }
        } catch {
            // ignore
        }
        return "all";
    });

    const reposQuery = useRepos(page, 10, status, visibility, search);
    const syncAllMutation = useSyncAllRepos();

    const [viewMode, setViewMode] = useState<"list" | "grid">(() => {
        if (typeof window === "undefined") return "list";
        try {
            const saved = localStorage.getItem(VIEW_MODE_STORAGE_KEY);
            if (saved === "grid" || saved === "list") {
                return saved;
            }
        } catch {
            // ignore
        }
        return "list";
    });

    const handleViewModeChange = (mode: "list" | "grid") => {
        setViewMode(mode);
        try {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode);
        } catch {
            // ignore
        }
    };

    const handleSearchChange = (value: string) => {
        setSearch(value);
        setPage(0);
    };

    const handleVisibilityChange = (value: "all" | "public" | "private") => {
        setVisibility(value);
        setPage(0);
        try {
            localStorage.setItem(VISIBILITY_STORAGE_KEY, value);
        } catch {
            // ignore
        }
    };

    const handleStatusChange = (value: FilterStatus) => {
        setStatus(value);
        setPage(0);
        try {
            localStorage.setItem(STATUS_STORAGE_KEY, value);
        } catch {
            // ignore
        }
    };

    const pageData = reposQuery.data;
    const repos = pageData?.content ?? [];

    const readyCount =
        status === "READY"
            ? (pageData?.totalElements ?? 0)
            : (pageData?.content?.filter((r) => r.indexStatus === "READY").length ?? 0);

    return (
        <div className="flex min-h-full flex-col">
            <DashboardHeader
                search={search}
                onSearchChange={handleSearchChange}
                visibility={visibility}
                onVisibilityChange={handleVisibilityChange}
                status={status}
                onStatusChange={handleStatusChange}
                totalCount={pageData?.totalElements}
                readyCount={readyCount}
                onSyncAll={() => syncAllMutation.mutate()}
                isSyncingAll={syncAllMutation.isPending}
                viewMode={viewMode}
                onViewModeChange={handleViewModeChange}
            />

            <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
                {reposQuery.isLoading && (
                    viewMode === "list" ? (
                        <div className="divide-y divide-border/60 rounded-xl border border-border/70 bg-card overflow-hidden">
                            {Array.from({ length: 6 }).map((_, i) => (
                                <div key={i} className="flex flex-col gap-2 p-4">
                                    <Skeleton className="h-5 w-48 rounded" />
                                    <Skeleton className="h-4 w-96 max-w-full rounded" />
                                    <Skeleton className="h-3.5 w-64 rounded" />
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="grid gap-3.5 sm:grid-cols-2 xl:grid-cols-3">
                            {Array.from({ length: 6 }).map((_, i) => (
                                <Skeleton key={i} className="h-52 rounded-xl" />
                            ))}
                        </div>
                    )
                )}

                {reposQuery.isError && (() => {
                    const err = reposQuery.error;
                    const isAuth = err instanceof ApiError && err.status === 401;
                    const isColdStart = err instanceof ApiError && (err.status === 502 || err.status === 503);
                    return (
                        <Empty className="border border-dashed">
                            <EmptyHeader>
                                <EmptyMedia variant="icon">
                                    <FolderGit2 />
                                </EmptyMedia>
                                <EmptyTitle>
                                    {isAuth
                                        ? "Session expired"
                                        : isColdStart
                                            ? "Server starting up"
                                            : "Couldn’t load repositories"}
                                </EmptyTitle>
                                <EmptyDescription>
                                    {err instanceof Error ? err.message : "An error occurred while fetching repositories."}
                                </EmptyDescription>
                            </EmptyHeader>
                            {isAuth ? (
                                <a href={getGithubLoginUrl()} className={buttonVariants()}>
                                    Sign in with GitHub
                                </a>
                            ) : (
                                <Button onClick={() => void reposQuery.refetch()}>Try again</Button>
                            )}
                        </Empty>
                    );
                })()}

                {reposQuery.isSuccess && repos.length === 0 && (
                    <Empty className="border border-dashed">
                        <EmptyHeader>
                            <EmptyMedia variant="icon">
                                <FolderGit2 />
                            </EmptyMedia>
                            <EmptyTitle>No repositories match</EmptyTitle>
                            <EmptyDescription>
                                Try clearing filters or syncing again.
                            </EmptyDescription>
                        </EmptyHeader>
                    </Empty>
                )}

                {reposQuery.isSuccess && repos.length > 0 && (
                    viewMode === "list" ? (
                        <div className="divide-y divide-border/60 rounded-xl border border-border/70 bg-card overflow-hidden shadow-xs">
                            {repos.map((repo) => (
                                <RepoListItem key={repo.id} repo={repo} />
                            ))}
                        </div>
                    ) : (
                        <div className="grid gap-3.5 sm:grid-cols-2 xl:grid-cols-3">
                            {repos.map((repo) => (
                                <RepoCard key={repo.id} repo={repo} />
                            ))}
                        </div>
                    )
                )}

                {reposQuery.isSuccess && pageData && pageData.totalElements > 0 && (
                    <div className="flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-border/40 pt-4 mt-2">
                        <p className="text-sm text-muted-foreground">
                            Showing <span className="font-medium text-foreground">{page * 10 + 1}</span>–<span className="font-medium text-foreground">{Math.min((page + 1) * 10, pageData.totalElements)}</span> of <span className="font-medium text-foreground">{pageData.totalElements}</span> repositories
                        </p>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setPage((p) => Math.max(0, p - 1))}
                                disabled={!pageData.hasPrevious}
                            >
                                Previous
                            </Button>
                            <span className="text-sm text-muted-foreground px-2">
                                Page {pageData.page + 1} of {Math.max(1, pageData.totalPages)}
                            </span>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setPage((p) => p + 1)}
                                disabled={!pageData.hasNext}
                            >
                                Next
                            </Button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}