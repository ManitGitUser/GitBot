"use client";

import { formatDistanceToNow } from "date-fns";
import {
    GitFork,
    MoreHorizontal,
    Pencil,
    Plus,
    RotateCcw,
    Search,
    Trash2,
    X,
} from "lucide-react";
import { useMemo, useState } from "react";

import { IndexStatusBadge } from "@/components/dashboard/repo-status";
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
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import {
    useChatSessions,
    useCreateChatSession,
    useDeleteChatSession,
    useRenameChatSession,
} from "@/hooks/use-chat";
import { useStartIndexing } from "@/hooks/use-repos";
import type { ChatSession, Repository } from "@/lib/api";
import { cn } from "@/lib/utils";

export function ChatSidebar({
    repo,
    sessionId,
    onSelectSession,
    onSessionDeleted,
}: {
    repo: Repository;
    sessionId: string | null;
    onSelectSession: (id: string) => void;
    onSessionDeleted?: (id: string) => void;
}) {
    const ready = repo.indexStatus === "READY";
    const sessionsQuery = useChatSessions(repo.id, ready);
    const createSession = useCreateChatSession(repo.id);
    const deleteSession = useDeleteChatSession(repo.id);
    const renameSession = useRenameChatSession(repo.id);
    const reindex = useStartIndexing();

    const [searchQuery, setSearchQuery] = useState("");
    const [renamingSession, setRenamingSession] = useState<ChatSession | null>(null);
    const [renameTitle, setRenameTitle] = useState("");
    const [deletingSession, setDeletingSession] = useState<ChatSession | null>(null);

    const allSessions = useMemo(() => sessionsQuery.data ?? [], [sessionsQuery.data]);
    const showSearch = allSessions.length > 5;

    const filteredSessions = useMemo(() => {
        if (!searchQuery.trim()) return allSessions;
        const q = searchQuery.toLowerCase().trim();
        return allSessions.filter((s) => s.title.toLowerCase().includes(q));
    }, [allSessions, searchQuery]);

    function startRename(session: ChatSession, e: React.MouseEvent) {
        e.stopPropagation();
        setRenamingSession(session);
        setRenameTitle(session.title);
    }

    function confirmRename() {
        if (!renamingSession || !renameTitle.trim()) return;
        renameSession.mutate(
            { sessionId: renamingSession.id, title: renameTitle.trim() },
            {
                onSuccess: () => setRenamingSession(null),
            }
        );
    }

    function startDelete(session: ChatSession, e: React.MouseEvent) {
        e.stopPropagation();
        setDeletingSession(session);
    }

    function confirmDelete() {
        if (!deletingSession) return;
        const targetId = deletingSession.id;
        deleteSession.mutate(targetId, {
            onSuccess: () => {
                setDeletingSession(null);
                if (onSessionDeleted) {
                    onSessionDeleted(targetId);
                }
            },
        });
    }

    return (
        <aside className="flex w-full flex-col border-b bg-card/40 md:w-72 md:border-r md:border-b-0">
            <div className="space-y-3 p-4">
                <div className="space-y-1">
                    <p className="truncate text-sm font-medium">{repo.fullName}</p>
                    <div className="flex flex-wrap items-center gap-2">
                        <IndexStatusBadge status={repo.indexStatus} />
                        {repo.isPrivate && (
                            <span className="text-xs text-muted-foreground">Private</span>
                        )}
                    </div>
                </div>

                <div className="flex gap-2">
                    <Button
                        size="sm"
                        className="flex-1"
                        disabled={!ready || createSession.isPending}
                        onClick={() =>
                            createSession.mutate("New chat", {
                                onSuccess: (session) => onSelectSession(session.id),
                            })
                        }
                    >
                        {createSession.isPending ? (
                            <Spinner />
                        ) : (
                            <Plus data-icon="inline-start" />
                        )}
                        New chat
                    </Button>
                    <Button
                        size="sm"
                        variant="outline"
                        disabled={reindex.isPending || repo.indexStatus === "INDEXING"}
                        onClick={() => reindex.mutate(repo.id)}
                        aria-label="Re-index repository"
                        title="Re-index repository"
                    >
                        {reindex.isPending ? <Spinner /> : <RotateCcw />}
                    </Button>
                </div>
            </div>

            <Separator />

            {/* Optional search if sessions > 5 */}
            {showSearch && (
                <div className="p-2 pb-0">
                    <div className="relative flex items-center">
                        <Search className="pointer-events-none absolute left-2.5 size-3.5 text-muted-foreground" />
                        <Input
                            placeholder="Filter conversations..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="h-8 pl-8 pr-7 text-xs"
                            aria-label="Filter chat sessions"
                        />
                        {searchQuery && (
                            <button
                                type="button"
                                onClick={() => setSearchQuery("")}
                                className="absolute right-2 p-0.5 text-muted-foreground hover:text-foreground"
                                aria-label="Clear filter"
                            >
                                <X className="size-3" />
                            </button>
                        )}
                    </div>
                </div>
            )}

            <div className="flex items-center justify-between px-4 py-2 text-xs font-medium text-muted-foreground">
                <span>Conversations</span>
                {allSessions.length > 0 && (
                    <span className="text-[10px] text-muted-foreground/80">
                        {filteredSessions.length} {filteredSessions.length === 1 ? "chat" : "chats"}
                    </span>
                )}
            </div>

            <ScrollArea className="flex-1">
                <div className="space-y-1 px-2 pb-4" role="navigation" aria-label="Chat sessions">
                    {!ready && (
                        <p className="px-2 py-4 text-xs text-center text-muted-foreground">
                            Sessions unlock once indexing finishes.
                        </p>
                    )}

                    {sessionsQuery.isLoading &&
                        Array.from({ length: 3 }).map((_, i) => (
                            <Skeleton key={i} className="h-12 w-full rounded-xl" />
                        ))}

                    {filteredSessions.map((session) => {
                        const isSelected = sessionId === session.id;
                        const isBranch = Boolean(session.parentSessionId);

                        return (
                            <div
                                key={session.id}
                                onClick={() => onSelectSession(session.id)}
                                className={cn(
                                    "group relative flex cursor-pointer items-center justify-between rounded-xl px-3 py-2.5 transition-colors hover:bg-muted/80",
                                    isSelected
                                        ? "bg-muted font-medium text-foreground shadow-2xs border-l-2 border-primary pl-2.5"
                                        : "text-muted-foreground hover:text-foreground"
                                )}
                                role="button"
                                tabIndex={0}
                                aria-current={isSelected ? "page" : undefined}
                                aria-label={`Conversation: ${session.title}`}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter" || e.key === " ") {
                                        e.preventDefault();
                                        onSelectSession(session.id);
                                    }
                                }}
                            >
                                <div className="min-w-0 flex-1 pr-2">
                                    <div className="flex items-center gap-1.5">
                                        {isBranch && (
                                            <span title="Branched conversation" className="inline-flex shrink-0">
                                                <GitFork
                                                    className="size-3 text-primary/70"
                                                    aria-label="Branched conversation"
                                                />
                                            </span>
                                        )}
                                        <p className="truncate text-sm">{session.title}</p>
                                    </div>
                                    <p className="text-xs opacity-70">
                                        {formatDistanceToNow(new Date(session.createdAt), {
                                            addSuffix: true,
                                        })}
                                    </p>
                                </div>

                                <div className="opacity-100 sm:opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                                    <DropdownMenu>
                                        <DropdownMenuTrigger
                                            render={
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    className="size-7 text-muted-foreground hover:text-foreground"
                                                    onClick={(e) => e.stopPropagation()}
                                                    aria-label={`Options for ${session.title}`}
                                                />
                                            }
                                        >
                                            <MoreHorizontal className="size-3.5" />
                                        </DropdownMenuTrigger>
                                        <DropdownMenuContent align="end">
                                            <DropdownMenuItem
                                                onClick={(e) => startRename(session, e as unknown as React.MouseEvent)}
                                            >
                                                <Pencil className="mr-2 size-3.5" />
                                                Rename
                                            </DropdownMenuItem>
                                            <DropdownMenuItem
                                                variant="destructive"
                                                onClick={(e) => startDelete(session, e as unknown as React.MouseEvent)}
                                            >
                                                <Trash2 className="mr-2 size-3.5" />
                                                Delete
                                            </DropdownMenuItem>
                                        </DropdownMenuContent>
                                    </DropdownMenu>
                                </div>
                            </div>
                        );
                    })}

                    {ready && sessionsQuery.isSuccess && allSessions.length === 0 && (
                        <div className="px-3 py-8 text-center text-xs text-muted-foreground">
                            <p className="font-medium">No conversations yet</p>
                            <p className="mt-1 opacity-80">Start a new chat to begin exploring this repo.</p>
                        </div>
                    )}

                    {ready && searchQuery && filteredSessions.length === 0 && allSessions.length > 0 && (
                        <div className="px-3 py-6 text-center text-xs text-muted-foreground">
                            <p>No conversations match &ldquo;{searchQuery}&rdquo;</p>
                            <Button
                                variant="link"
                                size="sm"
                                className="mt-1 h-auto p-0 text-xs"
                                onClick={() => setSearchQuery("")}
                            >
                                Clear search
                            </Button>
                        </div>
                    )}
                </div>
            </ScrollArea>

            {/* Rename Dialog */}
            <Dialog open={Boolean(renamingSession)} onOpenChange={(open) => !open && setRenamingSession(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Rename conversation</DialogTitle>
                        <DialogDescription>
                            Enter a descriptive title for this conversation.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-1.5 py-2">
                        <Input
                            value={renameTitle}
                            onChange={(e) => setRenameTitle(e.target.value)}
                            maxLength={200}
                            placeholder="Conversation title"
                            autoFocus
                            onKeyDown={(e) => {
                                if (e.key === "Enter") {
                                    e.preventDefault();
                                    confirmRename();
                                }
                            }}
                        />
                        <div className="flex justify-end text-[11px] text-muted-foreground">
                            {renameTitle.length} / 200
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setRenamingSession(null)}>
                            Cancel
                        </Button>
                        <Button
                            disabled={!renameTitle.trim() || renameSession.isPending}
                            onClick={confirmRename}
                        >
                            {renameSession.isPending ? <Spinner /> : null}
                            Save
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Delete Confirmation Alert Dialog */}
            <AlertDialog open={Boolean(deletingSession)} onOpenChange={(open) => !open && setDeletingSession(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete conversation?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This will permanently delete this conversation, its message history, and feedback reports.
                            This action cannot be undone.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onClick={() => setDeletingSession(null)}>
                            Cancel
                        </AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={deleteSession.isPending}
                            onClick={confirmDelete}
                        >
                            {deleteSession.isPending ? <Spinner /> : null}
                            Delete conversation
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </aside>
    );
}