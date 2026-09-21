"use client";

import Image from "next/image";
import { ExternalLink, FolderGit2, Mail, PlayCircle } from "lucide-react";
import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { GitHubIcon } from "@/components/icons/github-icon";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { PROJECT_CONFIG } from "@/lib/project-config";

export function AboutGitBotDialog({
    open,
    onOpenChange,
}: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-md gap-5 p-6">
                <DialogHeader className="gap-2 text-left">
                    <div className="flex items-center gap-3">
                        <GitBotIcon className="size-10 shrink-0 rounded-xl" />
                        <div>
                            <DialogTitle className="font-heading text-lg font-semibold">
                                {PROJECT_CONFIG.name}
                            </DialogTitle>
                            <p className="text-xs text-muted-foreground">
                                {PROJECT_CONFIG.tagline}
                            </p>
                        </div>
                    </div>
                    <DialogDescription className="text-xs text-muted-foreground pt-1">
                        {PROJECT_CONFIG.description}
                    </DialogDescription>
                </DialogHeader>

                {/* Developer Information */}
                <div className="rounded-xl border border-border/70 bg-muted/30 p-4 space-y-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                            <Image
                                src={PROJECT_CONFIG.developer.avatarUrl}
                                alt={PROJECT_CONFIG.developer.name}
                                width={28}
                                height={28}
                                className="size-7 rounded-full object-cover border border-border/80 shadow-2xs shrink-0"
                            />
                            <div>
                                <p className="text-xs font-semibold text-foreground">
                                    {PROJECT_CONFIG.developer.name}
                                </p>
                                <p className="text-[11px] text-muted-foreground">
                                    {PROJECT_CONFIG.developer.role}
                                </p>
                            </div>
                        </div>
                        <span className="rounded-md border border-border/80 bg-background px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                            Creator
                        </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-border/50 text-xs">
                        <a
                            href={PROJECT_CONFIG.developer.githubUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 rounded-lg border border-border/80 bg-background px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:bg-muted"
                        >
                            <GitHubIcon className="size-3.5" />
                            <span>GitHub</span>
                            <ExternalLink className="size-2.5 opacity-60" />
                        </a>
                        <a
                            href={`mailto:${PROJECT_CONFIG.developer.email}`}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-border/80 bg-background px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:bg-muted"
                        >
                            <Mail className="size-3.5" />
                            <span>{PROJECT_CONFIG.developer.email}</span>
                        </a>
                    </div>
                </div>

                {/* Project Links & Portfolio Note */}
                <div className="space-y-3">
                    <p className="text-xs text-muted-foreground leading-relaxed">
                        {PROJECT_CONFIG.portfolioNote}
                    </p>

                    <div className="grid grid-cols-2 gap-2">
                        <a
                            href={PROJECT_CONFIG.repositoryUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center justify-center gap-1.5 rounded-lg border border-border/80 bg-background px-3 py-2 text-xs font-medium text-foreground transition-colors hover:bg-muted"
                        >
                            <FolderGit2 className="size-3.5" />
                            <span>Source Code</span>
                            <ExternalLink className="size-2.5 opacity-60" />
                        </a>
                        <a
                            href={PROJECT_CONFIG.demoUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center justify-center gap-1.5 rounded-lg border border-border/80 bg-background px-3 py-2 text-xs font-medium text-foreground transition-colors hover:bg-muted"
                        >
                            <PlayCircle className="size-3.5" />
                            <span>Watch Demo</span>
                            <ExternalLink className="size-2.5 opacity-60" />
                        </a>
                    </div>
                </div>

                <DialogFooter className="sm:justify-end pt-1">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onOpenChange(false)}
                        className="text-xs"
                    >
                        Close
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
