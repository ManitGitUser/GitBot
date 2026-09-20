import React from "react";

import { cn } from "@/lib/utils";

export interface GitBotIconProps extends React.ImgHTMLAttributes<HTMLImageElement> {
    variant?: "color" | "mono";
}

export function GitBotIcon({
    className,
    variant: _variant,
    alt = "GitBot",
    ...props
}: GitBotIconProps) {
    void _variant;
    return (
        // eslint-disable-next-line @next/next/no-img-element
        <img
            src="/gitbot-logo.png"
            alt={alt}
            className={cn("object-contain shrink-0 select-none", className)}
            {...props}
        />
    );
}

export function GitBotLogo({
    className,
    ...props
}: React.HTMLAttributes<HTMLDivElement>) {
    return (
        <div
            className={cn(
                "flex items-center gap-2.5 font-semibold tracking-tight",
                className
            )}
            {...props}
        >
            <GitBotIcon className="size-8 rounded-[10px]" />
            <span className="font-heading text-[1.05rem] leading-none">GitBot</span>
        </div>
    );
}