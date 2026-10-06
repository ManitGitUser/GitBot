"use client"

import * as React from "react"
import { ThemeProvider as NextThemesProvider } from "next-themes"

// Suppress React 19 / Next.js development console error:
// "Encountered a script tag while rendering React component. Scripts inside React components are never executed when rendering on the client."
// next-themes injects an inline script for SSR theme flash (FOUC) prevention, which React 19 flags in dev mode.
if (process.env.NODE_ENV === "development") {
    const patchKey = "__gitbot_script_warning_patched__"
    const target = typeof window !== "undefined" ? window : globalThis
    if (!(target as Record<string, unknown>)[patchKey]) {
        (target as Record<string, unknown>)[patchKey] = true
        const originalConsoleError = console.error
        console.error = (...args: unknown[]) => {
            const isScriptTagWarning = args.some(
                (arg) =>
                    typeof arg === "string" &&
                    arg.includes("Encountered a script tag while rendering React component")
            )
            if (isScriptTagWarning) {
                return
            }
            originalConsoleError(...args)
        }
    }
}

export function ThemeProvider({
    children,
    ...props
}: React.ComponentProps<typeof NextThemesProvider>) {
    return <NextThemesProvider {...props}>{children}</NextThemesProvider>
}