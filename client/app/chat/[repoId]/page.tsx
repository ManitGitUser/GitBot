"use client";

import { Suspense, use } from "react";

import { ChatView }  from "@/components/chat/chat-view";
import { AppShell } from "@/components/layout/app-shell";
import { RequireAuth } from "@/components/providers/require-auth";
import { Skeleton } from "@/components/ui/skeleton";

export default function ChatPage({
                                     params,
                                 }: {
    params: Promise<{ repoId: string }>;
}) {
    const { repoId } = use(params);

    return (
        <RequireAuth>
            <Suspense
                fallback={
                    <AppShell title="Loading chat…">
                        <div className="grid flex-1 gap-4 p-4 md:grid-cols-[18rem_1fr]">
                            <Skeleton className="min-h-80 rounded-2xl" />
                            <Skeleton className="min-h-80 rounded-2xl" />
                        </div>
                    </AppShell>
                }
            >
                <ChatView repoId={repoId} />
            </Suspense>
        </RequireAuth>
    );
}