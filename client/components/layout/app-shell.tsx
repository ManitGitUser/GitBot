"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bug, Info, LogOut, MessageSquare, RefreshCw, Settings, Sparkles } from "lucide-react";

import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { FeatureTutorial, ONBOARDING_STORAGE_KEY } from "@/components/onboarding/feature-tutorial";
import { AboutGitBotDialog } from "@/components/about/about-gitbot-dialog";
import { getBugReportMailto } from "@/lib/project-config";

import { ModeToggle } from "@/components/ui/mode-toggle";
import { useCurrentUser, useLogout, useSyncProfile } from "@/hooks/use-auth";
import { useRecentChatSessions } from "@/hooks/use-chat";
import { buttonVariants } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuGroup,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import {
    Sidebar,
    SidebarContent,
    SidebarFooter,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarInset,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarProvider,
    SidebarTrigger,
} from "@/components/ui/sidebar";
import {
    dashboardNavGroups,
    isDashboardNavActive,
} from "@/lib/dashboard-nav";
import { cn } from "@/lib/utils";

export function AppShell({
                             children,
                             title,
                             description,
                             actions,
                             hideHeader = false,
                             className,
                             contentClassName,
                             activeSessionId,
                         }: {
    children: React.ReactNode;
    title?: string;
    description?: string;
    actions?: React.ReactNode;
    hideHeader?: boolean;
    className?: string;
    contentClassName?: string;
    activeSessionId?: string | null;
}) {
    const pathname = usePathname();
    const router = useRouter();
    const { data: user, isLoading: isAuthLoading } = useCurrentUser();
    console.log("[REAL APP USER]", user);
    console.log("[REAL APP AVATAR]", JSON.stringify(user?.avatarUrl));
    const { data: recentSessions } = useRecentChatSessions(10);
    const logout = useLogout();
    const syncProfile = useSyncProfile();
    const [tutorialOpen, setTutorialOpen] = useState(false);
    const [aboutOpen, setAboutOpen] = useState(false);

    useEffect(() => {
        // Only open tutorial if user is confirmed authenticated and hasn't completed onboarding
        if (isAuthLoading || !user) return;
        try {
            const completed = localStorage.getItem(ONBOARDING_STORAGE_KEY);
            if (completed !== "true") {
                setTimeout(() => setTutorialOpen(true), 0);
            }
        } catch {
            // ignore
        }
    }, [user, isAuthLoading]);

    return (
        <SidebarProvider>
            <Sidebar variant="inset" collapsible="icon">
                <SidebarHeader>
                    <SidebarMenu>
                        <SidebarMenuItem>
                            <SidebarMenuButton
                                size="lg"
                                onClick={() => setAboutOpen(true)}
                                tooltip="About GitBot"
                                className="group-data-[collapsible=icon]:justify-center cursor-pointer"
                            >
                                <GitBotIcon className="size-8 shrink-0 rounded-[10px]" />
                                <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
                                    <span className="truncate font-semibold">GitBot</span>
                                    <span className="truncate text-xs text-muted-foreground">
                    Chat with your code
                  </span>
                                </div>
                            </SidebarMenuButton>
                        </SidebarMenuItem>
                    </SidebarMenu>
                </SidebarHeader>

                <SidebarContent>
                    {dashboardNavGroups.map((group) => (
                        <div key={group.label} className="contents">
                            <SidebarGroup>
                                <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
                                <SidebarGroupContent>
                                    <SidebarMenu>
                                        {group.items.map((item) => (
                                            <SidebarMenuItem key={item.href}>
                                                <SidebarMenuButton
                                                    isActive={isDashboardNavActive(
                                                        pathname,
                                                        item.href,
                                                        item.exact
                                                    )}
                                                    tooltip={item.title}
                                                    render={<Link href={item.href} />}
                                                >
                                                    <item.icon />
                                                    <span>{item.title}</span>
                                                </SidebarMenuButton>
                                            </SidebarMenuItem>
                                        ))}
                                    </SidebarMenu>
                                </SidebarGroupContent>
                            </SidebarGroup>

                            {group.label === "Workspace" && (
                                <SidebarGroup>
                                    <SidebarGroupLabel>Recent chats</SidebarGroupLabel>
                                    <SidebarGroupContent>
                                        <SidebarMenu>
                                            {recentSessions && recentSessions.length > 0 ? (
                                                recentSessions.slice(0, 10).map((session) => {
                                                    const isChatActive =
                                                        pathname.startsWith("/chat/") &&
                                                        activeSessionId === session.id;
                                                    return (
                                                        <SidebarMenuItem key={session.id}>
                                                            <SidebarMenuButton
                                                                isActive={isChatActive}
                                                                tooltip={session.title || "New chat"}
                                                                render={
                                                                    <Link
                                                                        href={`/chat/${session.repositoryId}?sessionId=${session.id}`}
                                                                    />
                                                                }
                                                            >
                                                                <MessageSquare />
                                                                <span>{session.title || "New chat"}</span>
                                                            </SidebarMenuButton>
                                                        </SidebarMenuItem>
                                                    );
                                                })
                                            ) : (
                                                <div className="px-3 py-1.5 text-xs text-muted-foreground group-data-[collapsible=icon]:hidden">
                                                    No recent chats
                                                </div>
                                            )}
                                        </SidebarMenu>
                                    </SidebarGroupContent>
                                </SidebarGroup>
                            )}
                        </div>
                    ))}

                    <SidebarGroup>
                        <SidebarGroupLabel>Help & Resources</SidebarGroupLabel>
                        <SidebarGroupContent>
                            <SidebarMenu>
                                <SidebarMenuItem>
                                    <SidebarMenuButton
                                        onClick={() => setTutorialOpen(true)}
                                        tooltip="Feature Tutorial"
                                    >
                                        <Sparkles />
                                        <span>Feature Tutorial</span>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                                <SidebarMenuItem>
                                    <SidebarMenuButton
                                        onClick={() => setAboutOpen(true)}
                                        tooltip="About GitBot"
                                    >
                                        <Info />
                                        <span>About GitBot</span>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                                <SidebarMenuItem>
                                    <SidebarMenuButton
                                        tooltip="Report a bug"
                                        render={<a href={getBugReportMailto()} />}
                                    >
                                        <Bug />
                                        <span>Report a bug</span>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                            </SidebarMenu>
                        </SidebarGroupContent>
                    </SidebarGroup>
                </SidebarContent>

                <SidebarFooter>
                    <SidebarMenu>
                        <SidebarMenuItem>
                            <DropdownMenu>
                                <DropdownMenuTrigger
                                    render={
                                        <SidebarMenuButton
                                            size="lg"
                                            className="data-[popup-open]:bg-sidebar-accent"
                                        />
                                    }
                                >
                                    <div className="relative size-8 shrink-0 overflow-hidden rounded-lg">
                                        {user?.avatarUrl ? (
                                            /* eslint-disable-next-line @next/next/no-img-element */
                                            <img
                                                src={user.avatarUrl}
                                                alt={user?.displayName || user?.githubUsername || "User"}
                                                referrerPolicy="no-referrer"
                                                className="size-full object-cover"
                                            />
                                        ) : (
                                            <div className="flex size-full items-center justify-center bg-muted text-sm font-medium text-muted-foreground select-none">
                                                {(user?.displayName || user?.githubUsername || "U").slice(0, 2).toUpperCase()}
                                            </div>
                                        )}
                                    </div>
                                    <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
                    <span className="truncate font-medium">
                      {user?.displayName}
                    </span>
                                        <span className="truncate text-xs text-muted-foreground">
                      @{user?.githubUsername}
                    </span>
                                    </div>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent
                                    className="min-w-56 rounded-lg"
                                    side="top"
                                    align="start"
                                    sideOffset={8}
                                >
                                    <DropdownMenuGroup>
                                        <DropdownMenuLabel className="font-normal">
                                             <div className="flex flex-col gap-1">
                        <span className="text-sm font-medium">
                          {user?.displayName}
                        </span>
                                                <span className="text-xs text-muted-foreground">
                          Connected via GitHub
                        </span>
                                             </div>
                                        </DropdownMenuLabel>
                                    </DropdownMenuGroup>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem
                                        onClick={() => syncProfile.mutate()}
                                        disabled={syncProfile.isPending}
                                    >
                                        <RefreshCw className={syncProfile.isPending ? "animate-spin" : ""} />
                                        {syncProfile.isPending ? "Syncing Profile…" : "Sync Profile"}
                                    </DropdownMenuItem>
                                    <DropdownMenuItem onClick={() => setTutorialOpen(true)}>
                                        <Sparkles />
                                        Feature Tutorial
                                    </DropdownMenuItem>
                                    <DropdownMenuItem onClick={() => setAboutOpen(true)}>
                                        <Info />
                                        About GitBot
                                    </DropdownMenuItem>
                                    <DropdownMenuItem onClick={() => router.push("/dashboard/settings")}>
                                        <Settings />
                                        Settings
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem
                                        onClick={() => logout.mutate()}
                                        disabled={logout.isPending}
                                    >
                                        <LogOut />
                                        Log out
                                    </DropdownMenuItem>
                                </DropdownMenuContent>
                            </DropdownMenu>
                        </SidebarMenuItem>
                    </SidebarMenu>
                </SidebarFooter>
            </Sidebar>

            <SidebarInset className={cn("min-w-0 md:h-[calc(100svh-1rem)] md:max-h-[calc(100svh-1rem)]", className)}>
                {!hideHeader && (
                    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
                        <SidebarTrigger className="-ml-1" />
                        <Separator orientation="vertical" className="mr-2 h-4" />
                        <div className="flex min-w-0 flex-1 items-center justify-between gap-3">
                            <div className="min-w-0">
                                {title && (
                                    <h1 className="truncate font-heading text-sm font-medium">
                                        {title}
                                    </h1>
                                )}
                                {description && (
                                    <p className="truncate text-xs text-muted-foreground">
                                        {description}
                                    </p>
                                )}
                            </div>
                            <div className="flex items-center gap-2">
                                {actions}
                                <ModeToggle />
                            </div>
                        </div>
                    </header>
                )}
                <div className={cn("flex flex-1 flex-col min-h-0 min-w-0", contentClassName ?? "overflow-y-auto")}>
                    {children}
                </div>
            </SidebarInset>

            <FeatureTutorial open={tutorialOpen} onOpenChange={setTutorialOpen} />
            <AboutGitBotDialog open={aboutOpen} onOpenChange={setAboutOpen} />
        </SidebarProvider>
    );
}

export function BrandMark({ className }: { className?: string }) {
    return (
        <div
            className={cn(
                "flex items-center gap-2.5 font-semibold tracking-tight",
                className
            )}
        >
            <GitBotIcon className="size-8 rounded-[10px]" />
            <span className="font-heading text-[1.05rem] leading-none">GitBot</span>
        </div>
    );
}

export function GhostButtonLink({
                                    href,
                                    children,
                                    className,
                                }: {
    href: string;
    children: React.ReactNode;
    className?: string;
}) {
    return (
        <Link
            href={href}
            className={cn(buttonVariants({ variant: "ghost", size: "sm" }), className)}
        >
            {children}
        </Link>
    );
}