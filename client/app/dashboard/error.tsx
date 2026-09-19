"use client";

import { useEffect } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { AlertCircle } from "lucide-react";

export default function DashboardError({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        console.error("Dashboard error:", error);
    }, [error]);

    return (
        <AppShell hideHeader>
            <div className="flex h-[50vh] w-full flex-col items-center justify-center gap-4 text-center px-4">
                <div className="rounded-full bg-destructive/10 p-3 text-destructive">
                    <AlertCircle className="size-8" />
                </div>
                <div className="space-y-1">
                    <h2 className="text-lg font-semibold">Failed to load dashboard</h2>
                    <p className="text-sm text-muted-foreground max-w-md">
                        {error.message || "An unexpected error occurred while loading your repositories."}
                    </p>
                </div>
                <Button onClick={() => reset()} variant="outline">
                    Try again
                </Button>
            </div>
        </AppShell>
    );
}
