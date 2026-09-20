"use client";

import { useState } from "react";
import {
    AlertTriangle,
    ExternalLink,
    LogOut,
    RefreshCw,
    Trash2,
} from "lucide-react";

import { GitHubIcon } from "@/components/icons/github-icon";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
    useCurrentUser,
    useDeleteAccount,
    useLogout,
    useSyncProfile,
} from "@/hooks/use-auth";

export function SettingsDashboard() {
    const { data: user } = useCurrentUser();
    const logout = useLogout();
    const syncProfile = useSyncProfile();
    const deleteAccount = useDeleteAccount();

    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [confirmText, setConfirmText] = useState("");

    const handleOpenDialog = () => {
        setConfirmText("");
        setIsDeleteDialogOpen(true);
    };

    const handleCloseDialog = () => {
        if (deleteAccount.isPending) return;
        setIsDeleteDialogOpen(false);
        setConfirmText("");
    };

    const handleDelete = () => {
        if (confirmText !== "DELETE") return;
        deleteAccount.mutate();
    };

    return (
        <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-4 md:p-6">
            {/* Merged Profile & Connected GitHub Account Section */}
            <Card>
                <CardHeader>
                    <CardTitle>Profile</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="flex items-center gap-4">
                        <div className="relative size-14 shrink-0 overflow-hidden rounded-xl">
                            {user?.avatarUrl ? (
                                /* eslint-disable-next-line @next/next/no-img-element */
                                <img
                                    src={user.avatarUrl}
                                    alt={user?.displayName || "User"}
                                    referrerPolicy="no-referrer"
                                    className="size-full object-cover"
                                />
                            ) : (
                                <div className="flex size-full items-center justify-center bg-muted text-lg font-medium text-muted-foreground select-none">
                                    {(user?.displayName ?? "DP").slice(0, 2).toUpperCase()}
                                </div>
                            )}
                        </div>
                        <div className="min-w-0">
                            <p className="truncate font-medium">{user?.displayName}</p>
                            <p className="truncate text-sm text-muted-foreground">
                                @{user?.githubUsername}
                            </p>
                        </div>
                    </div>

                    <Separator />

                    <div className="grid gap-3 text-sm">
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-muted-foreground">Display name</span>
                            <span className="font-medium">{user?.displayName ?? "—"}</span>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-muted-foreground">GitHub account</span>
                            <span className="inline-flex items-center gap-1.5 font-medium">
                                <GitHubIcon className="size-4" />
                                @{user?.githubUsername ?? "—"}
                            </span>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-muted-foreground">Profile link</span>
                            {user?.githubUsername ? (
                                <a
                                    href={`https://github.com/${user.githubUsername}`}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
                                >
                                    github.com/{user.githubUsername}
                                    <ExternalLink className="size-3.5" />
                                </a>
                            ) : (
                                <span className="text-muted-foreground">—</span>
                            )}
                        </div>
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-muted-foreground">Status</span>
                            <Badge
                                variant="outline"
                                className="border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 gap-1.5 px-2.5 py-0.5"
                            >
                                <span className="size-1.5 rounded-full bg-emerald-500" />
                                Connected
                            </Badge>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Appearance Section */}
            <Card>
                <CardHeader>
                    <CardTitle>Appearance</CardTitle>
                    <CardDescription>
                        Customize how GitBot looks on your device.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="flex items-center justify-between gap-4">
                        <div className="space-y-1">
                            <Label>Theme</Label>
                            <p className="text-sm text-muted-foreground">
                                Select your preferred color theme.
                            </p>
                        </div>
                        <ModeToggle />
                    </div>
                </CardContent>
            </Card>

            {/* Account Actions Section */}
            <Card>
                <CardHeader>
                    <CardTitle>Account</CardTitle>
                    <CardDescription>
                        Manage your session and sync your latest profile information.
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 sm:flex-row">
                    <Button
                        variant="outline"
                        className="justify-start"
                        onClick={() => syncProfile.mutate()}
                        disabled={syncProfile.isPending}
                    >
                        <RefreshCw className={syncProfile.isPending ? "animate-spin" : ""} data-icon="inline-start" />
                        {syncProfile.isPending ? "Syncing Profile…" : "Sync Profile"}
                    </Button>
                    <Button
                        variant="outline"
                        className="justify-start"
                        onClick={() => logout.mutate()}
                        disabled={logout.isPending}
                    >
                        <LogOut data-icon="inline-start" />
                        Log out
                    </Button>
                </CardContent>
            </Card>

            {/* Danger Zone Section */}
            <Card className="border-destructive/40 bg-destructive/5 dark:bg-destructive/10">
                <CardHeader>
                    <div className="flex items-center gap-2 text-destructive">
                        <AlertTriangle className="size-5" />
                        <CardTitle className="text-destructive">Danger Zone</CardTitle>
                    </div>
                    <CardDescription className="text-destructive/80">
                        Irreversible actions that permanently delete your GitBot account and all associated data.
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="text-sm text-muted-foreground space-y-2">
                        <p>
                            Deleting your account is permanent. The following data will be erased immediately:
                        </p>
                        <ul className="list-inside list-disc space-y-1 text-xs text-muted-foreground">
                            <li>All indexed repositories and code vector embeddings</li>
                            <li>All chat sessions, messages, and feedback reports</li>
                            <li>All stored user credentials, tokens, and active sessions</li>
                        </ul>
                        <p className="text-xs font-medium text-foreground/80 pt-1">
                            Note: Your repositories and files on GitHub will not be touched or deleted.
                        </p>
                    </div>

                    <Separator className="border-destructive/20" />

                    <div className="flex items-center justify-between gap-4">
                        <div className="space-y-0.5">
                            <p className="text-sm font-medium">Delete account</p>
                            <p className="text-xs text-muted-foreground">
                                Permanently delete your GitBot data and sign out.
                            </p>
                        </div>
                        <Button
                            variant="destructive"
                            onClick={handleOpenDialog}
                            id="delete-account-trigger-button"
                        >
                            <Trash2 data-icon="inline-start" />
                            Delete Account
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {/* Delete Account Confirmation Dialog */}
            <Dialog open={isDeleteDialogOpen} onOpenChange={(open) => !open && handleCloseDialog()}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <div className="flex items-center gap-2 text-destructive">
                            <AlertTriangle className="size-5" />
                            <DialogTitle className="text-destructive">Delete Account</DialogTitle>
                        </div>
                        <DialogDescription className="space-y-2 pt-2">
                            <span>
                                This action is <strong className="font-semibold text-foreground">permanent and cannot be undone</strong>.
                                All your indexed repositories, vector embeddings, chat sessions, and messages will be permanently deleted.
                            </span>
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-3 py-2">
                        <p className="text-sm text-muted-foreground">
                            To confirm deletion, type <strong className="font-mono text-destructive select-all">DELETE</strong> below:
                        </p>
                        <Input
                            id="delete-account-confirm-input"
                            value={confirmText}
                            onChange={(e) => setConfirmText(e.target.value)}
                            placeholder="Type DELETE to confirm"
                            autoComplete="off"
                            spellCheck={false}
                            disabled={deleteAccount.isPending}
                        />
                    </div>

                    <DialogFooter className="gap-2 sm:gap-0">
                        <Button
                            variant="outline"
                            onClick={handleCloseDialog}
                            disabled={deleteAccount.isPending}
                        >
                            Cancel
                        </Button>
                        <Button
                            id="delete-account-confirm-button"
                            variant="destructive"
                            disabled={confirmText !== "DELETE" || deleteAccount.isPending}
                            onClick={handleDelete}
                        >
                            {deleteAccount.isPending ? (
                                <>
                                    <RefreshCw className="animate-spin" data-icon="inline-start" />
                                    Deleting Account…
                                </>
                            ) : (
                                <>
                                    <Trash2 data-icon="inline-start" />
                                    Permanently Delete
                                </>
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}