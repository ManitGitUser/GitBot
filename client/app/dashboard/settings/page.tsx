import type { Metadata } from "next";
import { RequireAuth } from "@/components/providers/require-auth";
import { AppShell } from "@/components/layout/app-shell";
import { SettingsDashboard } from "@/components/dashboard/settings-dashboard";

export const metadata: Metadata = {
    title: "Settings",
};

export default function SettingsPage() {
    return (
        <RequireAuth>
            <AppShell
                title="Settings"
                description="Profile, appearance, and account preferences"
            >
                <SettingsDashboard />
            </AppShell>
        </RequireAuth>
    );
}