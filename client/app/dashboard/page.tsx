import type { Metadata } from "next";
import { RequireAuth } from "@/components/providers/require-auth";
import { AppShell } from "@/components/layout/app-shell";
import { RepoDashboard } from "@/components/dashboard/repo-dashboard";

export const metadata: Metadata = {
    title: "Repositories",
};

export default function DashboardPage() {
    return (
        <RequireAuth>
            <AppShell hideHeader>
                <RepoDashboard/>
            </AppShell>
        </RequireAuth>
    );
}


