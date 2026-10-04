"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  ArrowRight,
  ExternalLink,
  Info,
  Mail,
  PlayCircle,
  Sparkles,
} from "lucide-react";

import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { GitHubIcon } from "@/components/icons/github-icon";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

import { cn } from "@/lib/utils";
import { getGithubLoginUrl } from "@/lib/api";
import { PROJECT_CONFIG } from "@/lib/project-config";

export default function HomePage() {
  const [aboutOpen, setAboutOpen] = useState(false);

  return (
    <div className="relative min-h-svh overflow-hidden flex flex-col justify-between">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(from_var(--primary)_l_c_h/0.1),transparent_60%)]" />
      <header className="relative z-10 w-full border-b border-border/50 bg-background/60 backdrop-blur-md">
        <div className="mx-auto flex h-14 w-full max-w-6xl xl:max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <a
            href={PROJECT_CONFIG.repositoryUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs sm:text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors"
            aria-label="GitBot on GitHub"
          >
            <GitHubIcon className="size-4" />
            <span className="font-medium">GitBot on GitHub</span>
            <ExternalLink className="size-3 opacity-60" />
          </a>
          <div className="flex items-center gap-2">
            <ModeToggle />
          </div>
        </div>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-6xl xl:max-w-7xl flex-1 flex-col justify-between px-4 sm:px-6 lg:px-8 pt-10 sm:pt-16 pb-6 sm:pb-8">
        {/* Hero Section */}
        <section className="mx-auto max-w-3xl space-y-6 sm:space-y-7 text-center my-auto">
          <div className="mx-auto flex size-16 items-center justify-center rounded-2xl border border-border/50 bg-black shadow-md overflow-hidden">
            <GitBotIcon className="size-16 rounded-2xl" />
          </div>
          <div className="space-y-3">
            <h1 className="font-heading text-4xl font-semibold tracking-tight sm:text-5xl">
              GitBot
            </h1>
            <p className="text-base sm:text-lg text-muted-foreground text-balance">
              {PROJECT_CONFIG.description}
            </p>
          </div>
          <div className="flex flex-col items-center gap-2.5 pt-1">
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Link
                href="/demo"
                className={cn(
                  buttonVariants({ size: "lg" }),
                  "inline-flex items-center gap-2 shadow-sm font-medium"
                )}
              >
                <Sparkles className="size-4 text-amber-400" />
                Try GitBot
                <ArrowRight className="size-4" />
              </Link>
              <a
                href={getGithubLoginUrl()}
                className={cn(
                  buttonVariants({ variant: "outline", size: "lg" }),
                  "inline-flex items-center gap-2"
                )}
              >
                <GitHubIcon className="size-4" />
                Sign in with GitHub
              </a>
            </div>
            <p className="text-xs text-muted-foreground">
              No sign-in required · Explore GitBot&apos;s own codebase in demo mode
            </p>
          </div>
        </section>

        {/* Bottom Options Above Footer */}
        <div className="flex flex-wrap items-center gap-2.5 pt-8">
          <a
            href={PROJECT_CONFIG.demoUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground shadow-2xs"
            )}
          >
            <PlayCircle className="size-3.5 text-primary" />
            <span>Watch Demo</span>
            <ExternalLink className="size-2.5 opacity-60" />
          </a>
          <button
            type="button"
            onClick={() => setAboutOpen(true)}
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground shadow-2xs cursor-pointer"
            )}
          >
            <Info className="size-3.5 text-primary" />
            <span>About Developer</span>
          </button>
        </div>
      </main>

      <footer className="relative z-10 w-full border-t border-border/50 bg-background/40 py-6 text-xs text-muted-foreground">
        <div className="mx-auto flex max-w-6xl xl:max-w-7xl flex-col sm:flex-row items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
          <p>© {new Date().getFullYear()} GitBot. Released under the MIT License. Built by {PROJECT_CONFIG.developer.name}.</p>
          <div className="flex items-center gap-4">
            <a
              href={PROJECT_CONFIG.repositoryUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-foreground transition-colors"
            >
              GitHub
            </a>
            <a
              href={PROJECT_CONFIG.licenseUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-foreground transition-colors"
            >
              MIT License
            </a>
            <a
              href={`mailto:${PROJECT_CONFIG.developer.email}`}
              className="hover:text-foreground transition-colors"
            >
              Contact
            </a>
          </div>
        </div>
      </footer>

      {/* About Developer Modal Dialog */}
      <Dialog open={aboutOpen} onOpenChange={setAboutOpen}>
        <DialogContent className="max-w-md p-6 sm:p-7 gap-5 rounded-2xl">
          <DialogHeader className="gap-1.5 text-left">
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center rounded-md border border-border/70 bg-muted/60 px-2 py-0.5 text-xs font-medium text-muted-foreground">
                Software Engineer
              </span>
            </div>
            <div className="mt-2 flex items-center gap-3.5">
              <Image
                src={PROJECT_CONFIG.developer.avatarUrl}
                alt={PROJECT_CONFIG.developer.name}
                width={44}
                height={44}
                className="size-11 rounded-full object-cover border border-border/80 shadow-2xs shrink-0"
              />
              <div>
                <DialogTitle className="font-heading text-base font-semibold text-foreground tracking-tight">
                  {PROJECT_CONFIG.developer.name}
                </DialogTitle>
                <DialogDescription className="text-xs text-muted-foreground">
                  {PROJECT_CONFIG.developer.role}
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <p className="text-sm text-muted-foreground leading-relaxed">
            Engineered GitBot as an end-to-end RAG application combining GitHub repository indexing, vector retrieval with pgvector, and contextual code chat.
          </p>

          <div className="flex flex-wrap items-center gap-2.5 pt-4 border-t border-border/60">
            <a
              href={PROJECT_CONFIG.developer.githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={cn(
                buttonVariants({ variant: "outline", size: "sm" }),
                "gap-1.5 text-xs sm:text-sm font-medium px-3 py-1.5"
              )}
            >
              <GitHubIcon className="size-3.5" />
              <span>GitHub Profile</span>
              <ExternalLink className="size-2.5 opacity-60" />
            </a>
            <a
              href={`mailto:${PROJECT_CONFIG.developer.email}`}
              className={cn(
                buttonVariants({ variant: "outline", size: "sm" }),
                "gap-1.5 text-xs sm:text-sm font-medium px-3 py-1.5"
              )}
            >
              <Mail className="size-3.5" />
              <span>{PROJECT_CONFIG.developer.email}</span>
            </a>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}