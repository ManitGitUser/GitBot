"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type PageResponse, type Repository } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { toast } from "@/components/ui/toast";


const INDEXING_POLL_MS = 2000;

function hasIndexingRepos(repos: Repository[] | undefined) {
    return repos?.some((repo) => repo.indexStatus === "INDEXING") ?? false;
}

function updateRepoInListCache(
    queryClient: ReturnType<typeof useQueryClient>,
    repo: Repository
) {
    queryClient.setQueriesData<PageResponse<Repository>>(
        { queryKey: [...queryKeys.repos.all, "list"] },
        (current) => {
            if (!current) return current;
            return {
                ...current,
                content: current.content.map((item) => (item.id === repo.id ? repo : item)),
            };
        }
    );
}

export function useRepos(page = 0, size = 10) {
    return useQuery({
        queryKey: queryKeys.repos.list(page, size),
        queryFn: async () => {
            const res = await api.listRepos(page, size, false);
            if (res.totalElements === 0 && page === 0) {
                return api.listRepos(page, size, true);
            }
            return res;
        },
        staleTime: 30_000,
        refetchInterval: (query) =>
            hasIndexingRepos(query.state.data?.content) ? INDEXING_POLL_MS : false,
    });
}

export function useRepository(repoId: string) {
    return useQuery({
        queryKey: queryKeys.repos.detail(repoId),
        queryFn: () => api.getRepo(repoId),
        enabled: Boolean(repoId),
        refetchInterval: (query) =>
            query.state.data?.indexStatus === "INDEXING" ? INDEXING_POLL_MS : false,
    });
}

export function useIndexStatus(repoId: string, enabled = false) {
    return useQuery({
        queryKey: queryKeys.repos.status(repoId),
        queryFn: () => api.indexStatus(repoId),
        enabled: Boolean(repoId) && enabled,
        refetchInterval: (query) =>
            query.state.data?.indexStatus === "INDEXING" ? 1500 : false,
    });
}

export function useStartIndexing() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (repoId: string) => api.startIndex(repoId),
        onSuccess: (repo) => {
            queryClient.setQueryData(queryKeys.repos.detail(repo.id), repo);
            updateRepoInListCache(queryClient, repo);
            void queryClient.invalidateQueries({
                queryKey: queryKeys.repos.status(repo.id),
            });
            toast.add({
                title: "Indexing started",
                description: `Indexing ${repo.fullName}…`,
                type: "loading",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Could not start indexing",
                description: error.message,
                type: "error",
            });
        },
    });
}


export function useSyncRepo() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (repoId: string) => api.syncRepo(repoId),
        onSuccess: (data) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.repos.all,
            });
            const hasNewCommit = Boolean(
                data.currentCommitSha &&
                data.indexedCommitSha &&
                data.currentCommitSha !== data.indexedCommitSha
            );
            if (hasNewCommit) {
                toast.add({
                    title: "New commit available",
                    description: data.message || "A newer commit was detected. Click Reindex to update.",
                    type: "success",
                });
            } else {
                toast.add({
                    title: "Repository up to date",
                    description: data.message || "No new commits found.",
                    type: "success",
                });
            }
        },
        onError: (error: Error) => {
            toast.add({
                title: "Unable to sync repository",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useSyncAllRepos() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: () => api.syncAllRepos(),
        onSuccess: (data) => {
            void queryClient.invalidateQueries({
                queryKey: queryKeys.repos.all,
            });
            const details: string[] = [];
            if (data.newRepositories > 0) {
                details.push(`${data.newRepositories} new`);
            }
            if (data.repositoriesWithNewCommits > 0) {
                details.push(`${data.repositoriesWithNewCommits} with new commits`);
            }
            const desc = details.length > 0
                ? `Repositories synced — ${details.join(", ")}.`
                : "All repositories are up to date.";
            toast.add({
                title: "Sync completed",
                description: desc,
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Unable to sync repositories",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function getRepoProgress(repo: Pick<
    Repository,
    "filesProcessed" | "filesTotal"
>) {
    if (!repo.filesTotal) return 0;
    return Math.min(100, Math.round((repo.filesProcessed / repo.filesTotal) * 100));
}