"use client";

import { useEffect } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Button, buttonVariants } from "@/components/ui/button";
import { AlertCircle, ArrowLeft } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";

export default function ChatError({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        console.error("Chat error:", error);
    }, [error]);

    return (
        <AppShell title="Repository chat error">
            <div className="flex h-full w-full flex-col items-center justify-center gap-4 text-center px-4">
                <div className="rounded-full bg-destructive/10 p-3 text-destructive">
                    <AlertCircle className="size-8" />
                </div>
                <div className="space-y-1">
                    <h2 className="text-lg font-semibold">Failed to load chat session</h2>
                    <p className="text-sm text-muted-foreground max-w-md">
                        {error.message || "An unexpected error occurred while loading this chat."}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Button onClick={() => reset()} variant="outline">
                        Try again
                    </Button>
                    <Link href="/dashboard" className={cn(buttonVariants({ variant: "default" }))}>
                        <ArrowLeft className="size-4 mr-2" />
                        Back to Dashboard
                    </Link>
                </div>
            </div>
        </AppShell>
    );
}
