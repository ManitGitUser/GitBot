"use client";

import { formatDistanceToNow } from "date-fns";
import { GitFork, MoreHorizontal, Pencil, Plus, RotateCcw, Trash2 } from "lucide-react";
import { useState } from "react";

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

    const [renamingSession, setRenamingSession] = useState<ChatSession | null>(null);
    const [renameTitle, setRenameTitle] = useState("");
    const [deletingSession, setDeletingSession] = useState<ChatSession | null>(null);

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
        <aside className="flex w-full flex-col border-b md:w-72 md:border-r md:border-b-0">
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
                        <Plus data-icon="inline-start" />
                        New chat
                    </Button>
                    <Button
                        size="sm"
                        variant="outline"
                        disabled={reindex.isPending || repo.indexStatus === "INDEXING"}
                        onClick={() => reindex.mutate(repo.id)}
                        aria-label="Re-index repository"
                    >
                        <RotateCcw />
                    </Button>
                </div>
            </div>

            <Separator />

            <div className="px-4 py-2 text-xs font-medium text-muted-foreground">
                Sessions
            </div>

            <ScrollArea className="flex-1">
                <div className="space-y-1 px-2 pb-4">
                    {!ready && (
                        <p className="px-2 text-xs text-muted-foreground">
                            Sessions unlock after indexing completes.
                        </p>
                    )}

                    {sessionsQuery.isLoading &&
                        Array.from({ length: 3 }).map((_, i) => (
                            <Skeleton key={i} className="h-12 rounded-xl" />
                        ))}

                    {sessionsQuery.data?.map((session) => {
                        const isSelected = sessionId === session.id;
                        const isBranch = Boolean(session.parentSessionId);

                        return (
                            <div
                                key={session.id}
                                onClick={() => onSelectSession(session.id)}
                                className={cn(
                                    "group relative flex cursor-pointer items-center justify-between rounded-xl px-3 py-2.5 transition-colors hover:bg-muted",
                                    isSelected && "bg-muted"
                                )}
                            >
                                <div className="min-w-0 flex-1 pr-2">
                                    <div className="flex items-center gap-1.5">
                                        {isBranch && (
                                            <GitFork
                                                className="size-3 text-muted-foreground shrink-0"
                                                aria-label="Branched chat"
                                            />
                                        )}
                                        <p className="truncate text-sm font-medium">{session.title}</p>
                                    </div>
                                    <p className="text-xs text-muted-foreground">
                                        {formatDistanceToNow(new Date(session.createdAt), {
                                            addSuffix: true,
                                        })}
                                    </p>
                                </div>

                                <div className="opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                                    <DropdownMenu>
                                        <DropdownMenuTrigger
                                            render={
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    className="size-7"
                                                    onClick={(e) => e.stopPropagation()}
                                                    aria-label="Chat options"
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

                    {ready && sessionsQuery.isSuccess && sessionsQuery.data.length === 0 && (
                        <p className="px-2 text-xs text-muted-foreground">
                            No chats yet. Start one to begin.
                        </p>
                    )}
                </div>
            </ScrollArea>

            {/* Rename Dialog */}
            <Dialog open={Boolean(renamingSession)} onOpenChange={(open) => !open && setRenamingSession(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Rename chat</DialogTitle>
                        <DialogDescription>
                            Enter a new title for this chat conversation.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="py-2">
                        <Input
                            value={renameTitle}
                            onChange={(e) => setRenameTitle(e.target.value)}
                            maxLength={200}
                            placeholder="Chat title"
                            onKeyDown={(e) => {
                                if (e.key === "Enter") {
                                    e.preventDefault();
                                    confirmRename();
                                }
                            }}
                        />
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setRenamingSession(null)}>
                            Cancel
                        </Button>
                        <Button
                            disabled={!renameTitle.trim() || renameSession.isPending}
                            onClick={confirmRename}
                        >
                            Save
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Delete Confirmation Alert Dialog */}
            <AlertDialog open={Boolean(deletingSession)} onOpenChange={(open) => !open && setDeletingSession(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete chat conversation?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This will permanently delete this chat session, its messages, and any reports.
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
                            Delete
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </aside>
    );
}