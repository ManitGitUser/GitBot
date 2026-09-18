import { Spinner } from "@/components/ui/spinner";

export default function DashboardLoading() {
    return (
        <div className="flex h-[50vh] w-full flex-col items-center justify-center gap-3">
            <Spinner className="size-8 text-primary" />
            <p className="text-sm text-muted-foreground">Loading repositories…</p>
        </div>
    );
}
