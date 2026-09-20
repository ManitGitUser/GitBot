import Link from "next/link";
import {
    ArrowRight,
    ExternalLink,
    FolderGit2,
    Mail,
    MessageSquareCode,
    PlayCircle,
    Sparkles,
    User,
} from "lucide-react";

import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { GitHubIcon } from "@/components/icons/github-icon";
import { BrandMark } from "@/components/layout/app-shell";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { buttonVariants } from "@/components/ui/button";

import { cn } from "@/lib/utils";
import { getGithubLoginUrl } from "@/lib/api";
import { PROJECT_CONFIG } from "@/lib/project-config";

export default function HomePage() {
  return (
      <div className="relative min-h-svh overflow-hidden flex flex-col justify-between">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(from_var(--primary)_l_c_h/0.1),transparent_60%)]" />
        <header className="relative z-10 mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
          <BrandMark />
          <div className="flex items-center gap-2">
            <ModeToggle />
            <Link
                href="/login"
                className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
            >
              Sign in
            </Link>
          </div>
        </header>

        <main className="relative z-10 mx-auto flex w-full max-w-5xl flex-col gap-20 px-4 py-12 md:py-20">
          {/* Hero Section */}
          <section className="mx-auto max-w-2xl space-y-7 text-center">
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
            <div className="flex flex-wrap items-center justify-center gap-3 pt-1">
              <a
                  href={getGithubLoginUrl()}
                  className={cn(
                      buttonVariants({ size: "lg" }),
                      "inline-flex items-center gap-1.5"
                  )}
              >
                <FolderGit2 className="size-4" />
                Continue with GitHub
                <ArrowRight className="size-4" />
              </a>
              <Link
                  href="/login"
                  className={cn(buttonVariants({ variant: "outline", size: "lg" }))}
              >
                See how it works
              </Link>
            </div>
          </section>

          {/* Feature Highlights */}
          <section className="grid gap-5 md:grid-cols-3">
            {[
              {
                title: "Connect GitHub",
                body: "OAuth with repo scope for public and private repositories.",
                icon: FolderGit2,
              },
              {
                title: "Index with RAG",
                body: "Chunk and embed your code into Postgres + pgvector.",
                icon: Sparkles,
              },
              {
                title: "Ask anything",
                body: "Get grounded answers with clickable source citations.",
                icon: MessageSquareCode,
              },
            ].map((item) => (
                <div
                    key={item.title}
                    className="flex flex-col justify-start rounded-2xl border border-border/70 bg-card/80 p-6 sm:p-7 shadow-xs backdrop-blur"
                >
                  <div className="mb-5 flex size-11 items-center justify-center rounded-xl border border-border/50 bg-muted/80 text-foreground">
                    <item.icon className="size-5" />
                  </div>
                  <h2 className="font-heading text-base font-semibold text-foreground tracking-tight">{item.title}</h2>
                  <p className="mt-2 text-sm text-muted-foreground leading-relaxed">{item.body}</p>
                </div>
            ))}
          </section>

          {/* Explore the Project CTA & Developer Intro */}
          <section className="grid gap-6 md:grid-cols-2 items-stretch">
            {/* Explore Project */}
            <div className="flex flex-col justify-between rounded-2xl border border-border/70 bg-card/80 p-7 sm:p-8 backdrop-blur shadow-xs">
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-primary">
                  Open Source
                </span>
                <h3 className="mt-3 font-heading text-xl font-semibold text-foreground tracking-tight">
                  Explore the Project
                </h3>
                <p className="mt-2.5 text-sm text-muted-foreground leading-relaxed">
                  {PROJECT_CONFIG.portfolioNote}
                </p>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <a
                    href={PROJECT_CONFIG.demoUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={cn(
                        buttonVariants({ variant: "outline", size: "sm" }),
                        "gap-1.5 text-xs sm:text-sm font-medium px-3.5 py-2"
                    )}
                >
                  <PlayCircle className="size-3.5" />
                  <span>Watch the demo</span>
                  <ExternalLink className="size-2.5 opacity-60" />
                </a>
                <a
                    href={PROJECT_CONFIG.repositoryUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={cn(
                        buttonVariants({ variant: "outline", size: "sm" }),
                        "gap-1.5 text-xs sm:text-sm font-medium px-3.5 py-2"
                    )}
                >
                  <GitHubIcon className="size-3.5" />
                  <span>View the source code</span>
                  <ExternalLink className="size-2.5 opacity-60" />
                </a>
              </div>
            </div>

            {/* Built by Developer */}
            <div className="flex flex-col justify-between rounded-2xl border border-border/70 bg-card/80 p-7 sm:p-8 backdrop-blur shadow-xs">
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  About the Developer
                </span>
                <div className="mt-3 flex items-center gap-3.5">
                  <div className="flex size-11 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <User className="size-5" />
                  </div>
                  <div>
                    <h3 className="font-heading text-base font-semibold text-foreground">
                      {PROJECT_CONFIG.developer.name}
                    </h3>
                    <p className="text-xs text-muted-foreground">
                      {PROJECT_CONFIG.developer.role}
                    </p>
                  </div>
                </div>
                <p className="mt-3.5 text-sm text-muted-foreground leading-relaxed">
                  Engineered GitBot as an end-to-end RAG application combining GitHub repository indexing, vector retrieval with pgvector, and contextual code chat.
                </p>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-2.5 pt-4 border-t border-border/60">
                <a
                    href={PROJECT_CONFIG.developer.githubUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 rounded-lg border border-border/80 bg-background px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-muted"
                >
                  <GitHubIcon className="size-3.5" />
                  <span>GitHub Profile</span>
                  <ExternalLink className="size-2.5 opacity-60" />
                </a>
                <a
                    href={`mailto:${PROJECT_CONFIG.developer.email}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-border/80 bg-background px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                >
                  <Mail className="size-3.5" />
                  <span>{PROJECT_CONFIG.developer.email}</span>
                </a>
              </div>
            </div>
          </section>
        </main>

        <footer className="relative z-10 border-t border-border/60 py-6 text-center text-xs text-muted-foreground">
          <div className="mx-auto flex max-w-5xl flex-col sm:flex-row items-center justify-between gap-2 px-4">
            <p>© {new Date().getFullYear()} GitBot. Built by {PROJECT_CONFIG.developer.name}.</p>
            <div className="flex items-center gap-4">
              <a
                  href={PROJECT_CONFIG.developer.githubUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-foreground transition-colors"
              >
                GitHub
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
      </div>
  );
}