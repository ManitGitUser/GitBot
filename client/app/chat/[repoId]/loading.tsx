import { Spinner } from "@/components/ui/spinner";

export default function ChatLoading() {
    return (
        <div className="flex h-svh w-full flex-col items-center justify-center gap-3">
            <Spinner className="size-8 text-primary" />
            <p className="text-sm text-muted-foreground">Opening repository chat…</p>
        </div>
    );
}
