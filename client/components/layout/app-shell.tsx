"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Settings, Sparkles } from "lucide-react";

import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { FeatureTutorial, ONBOARDING_STORAGE_KEY } from "@/components/onboarding/feature-tutorial";

import { ModeToggle } from "@/components/ui/mode-toggle";
import { useCurrentUser, useLogout } from "@/hooks/use-auth";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
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
                         }: {
    children: React.ReactNode;
    title?: string;
    description?: string;
    actions?: React.ReactNode;
    hideHeader?: boolean;
}) {
    const pathname = usePathname();
    const router = useRouter();
    const { data: user, isLoading: isAuthLoading } = useCurrentUser();
    const logout = useLogout();
    const [tutorialOpen, setTutorialOpen] = useState(false);

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
                                render={<Link href="/dashboard" />}
                                tooltip="GitBot"
                            >
                                <GitBotIcon className="size-8 rounded-[10px]" />
                                <div className="grid flex-1 text-left text-sm leading-tight">
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
                        <SidebarGroup key={group.label}>
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
                                    <Avatar className="size-8 rounded-lg">
                                        <AvatarImage
                                            src={user?.avatarUrl ?? undefined}
                                            alt={user?.displayName}
                                        />
                                        <AvatarFallback className="rounded-lg">
                                            {(user?.displayName ?? "DP").slice(0, 2).toUpperCase()}
                                        </AvatarFallback>
                                    </Avatar>
                                    <div className="grid flex-1 text-left text-sm leading-tight">
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
                                    <DropdownMenuItem onClick={() => setTutorialOpen(true)}>
                                        <Sparkles />
                                        Feature Tutorial
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

            <SidebarInset>
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
                <div className="flex flex-1 flex-col">{children}</div>
            </SidebarInset>

            <FeatureTutorial open={tutorialOpen} onOpenChange={setTutorialOpen} />
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
        <Button variant="ghost" size="sm" className={className} render={<Link href={href} />}>
            {children}
        </Button>
    );
}