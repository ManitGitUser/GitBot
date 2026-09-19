import { AppShell } from "@/components/layout/app-shell";
import { Spinner } from "@/components/ui/spinner";

export default function ChatLoading() {
    return (
        <AppShell title="Loading chat…">
            <div className="flex h-full w-full flex-col items-center justify-center gap-3">
                <Spinner className="size-8 text-primary" />
                <p className="text-sm text-muted-foreground">Opening repository chat…</p>
            </div>
        </AppShell>
    );
}
