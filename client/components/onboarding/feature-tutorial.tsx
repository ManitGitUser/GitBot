"use client";

import { useState, useEffect, useCallback } from "react";
import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Code2,
    FileText,
    FolderGit2,
    GitFork,
    Share2,
    Sparkles,
} from "lucide-react";

import { GitBotIcon } from "@/components/icons/gitbot-icon";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

export const ONBOARDING_STORAGE_KEY = "gitbot:onboarding-completed";

interface TutorialSlide {
    title: string;
    subtitle: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
    accentColor: string;
    points: string[];
}

const slides: TutorialSlide[] = [
    {
        title: "Welcome to GitBot",
        subtitle: "AI-Powered Repository Assistant",
        description:
            "GitBot connects directly to your GitHub repositories, giving you an intelligent chat assistant that understands your actual codebase, dependencies, and architecture.",
        icon: GitBotIcon,
        accentColor: "bg-black text-white border-border/60",
        points: [
            "Seamless GitHub OAuth integration",
            "Deep codebase understanding via RAG",
            "Instant answers grounded in real code",
        ],
    },
    {
        title: "Repository Intelligence",
        subtitle: "Deterministic Code Chunking & Embeddings",
        description:
            "When you index a repository, GitBot splits code files into deterministic, line-preserving chunks and embeds them for high-accuracy similarity search.",
        icon: FolderGit2,
        accentColor: "text-blue-500 bg-blue-500/10 border-blue-500/20",
        points: [
            "1-based exact line number preservation",
            "Metadata-rich chunks (language, path, run ID)",
            "Safe re-indexing with idempotent run tracking",
        ],
    },
    {
        title: "Code-Aware Answers",
        subtitle: "Syntax-Highlighted, Streaming Responses",
        description:
            "Ask questions in natural language. GitBot synthesizes answers with syntax-highlighted code blocks, architecture explanations, and implementation details.",
        icon: Code2,
        accentColor: "text-indigo-500 bg-indigo-500/10 border-indigo-500/20",
        points: [
            "Real-time streaming token by token",
            "Preserved code indentation and formatting",
            "Full Markdown support with syntax highlighting",
        ],
    },
    {
        title: "Source Citations",
        subtitle: "Traceable Chunks & Deep Linking",
        description:
            "Every answer is backed by source citations. Interactive chips show the exact files, functions, and line ranges retrieved from the repository.",
        icon: FileText,
        accentColor: "text-amber-500 bg-amber-500/10 border-amber-500/20",
        points: [
            "Clickable citation chips with file paths",
            "Verified line ranges (e.g., L10-L45)",
            "Direct links to source context",
        ],
    },
    {
        title: "Continuous Conversations",
        subtitle: "Multiple Sessions & Branching",
        description:
            "Maintain separate conversation threads for different topics. Branch from any assistant message to explore alternative approaches without losing previous context.",
        icon: GitFork,
        accentColor: "text-purple-500 bg-purple-500/10 border-purple-500/20",
        points: [
            "Multiple named chat sessions per repository",
            "Branch new conversations from any message",
            "Filter, search, rename, and delete sessions",
        ],
    },
    {
        title: "Assistant Message Actions",
        subtitle: "Copy, Retry, Report & Share",
        description:
            "Every assistant message provides a rich toolbar for copying code, regenerating interrupted responses, submitting feedback, or sharing public transcripts.",
        icon: Share2,
        accentColor: "text-rose-500 bg-rose-500/10 border-rose-500/20",
        points: [
            "One-click copy to clipboard",
            "Retry failed or interrupted generations",
            "Shareable read-only conversation links",
        ],
    },
    {
        title: "Ready to Explore",
        subtitle: "Get Started with Your First Repository",
        description:
            "You're all set! Head over to your dashboard, select or index a repository, and start asking questions. You can reopen this tutorial anytime from the sidebar.",
        icon: CheckCircle2,
        accentColor: "text-emerald-500 bg-emerald-500/10 border-emerald-500/20",
        points: [
            "Visit the Repositories dashboard",
            "Trigger repository indexing",
            "Start your first intelligent conversation",
        ],
    },
];

export function FeatureTutorial({
    open,
    onOpenChange,
}: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}) {
    const [currentSlide, setCurrentSlide] = useState(0);

    const handleComplete = useCallback(() => {
        try {
            localStorage.setItem(ONBOARDING_STORAGE_KEY, "true");
        } catch {
            // ignore
        }
        onOpenChange(false);
    }, [onOpenChange]);

    const handleNext = useCallback(() => {
        if (currentSlide < slides.length - 1) {
            setCurrentSlide((prev) => prev + 1);
        } else {
            handleComplete();
        }
    }, [currentSlide, handleComplete]);

    const handlePrev = useCallback(() => {
        if (currentSlide > 0) {
            setCurrentSlide((prev) => prev - 1);
        }
    }, [currentSlide]);

    // Keyboard navigation
    useEffect(() => {
        if (!open) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "ArrowRight") {
                e.preventDefault();
                handleNext();
            } else if (e.key === "ArrowLeft") {
                e.preventDefault();
                handlePrev();
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [open, handleNext, handlePrev]);

    const slide = slides[currentSlide];
    const SlideIcon = slide.icon;
    const isLastSlide = currentSlide === slides.length - 1;

    return (
        <Dialog
            open={open}
            onOpenChange={(nextOpen) => {
                if (!nextOpen) {
                    handleComplete();
                } else {
                    onOpenChange(nextOpen);
                }
            }}
        >
            <DialogContent
                className="max-w-lg sm:max-w-xl p-0 overflow-hidden gap-0 rounded-2xl"
                showCloseButton={true}
            >
                {/* Header Banner */}
                <div className="relative border-b bg-muted/40 px-6 pt-6 pb-5 pr-12">
                    <div className="flex items-center justify-between gap-3 mb-3">
                        <Badge variant="secondary" className="text-xs font-medium">
                            <Sparkles className="mr-1 size-3 text-primary" />
                            Feature Guide &bull; {currentSlide + 1} of {slides.length}
                        </Badge>
                    </div>

                    <div className="flex items-start gap-4">
                        <div
                            className={cn(
                                "flex size-12 shrink-0 items-center justify-center rounded-xl border p-2.5 shadow-2xs transition-colors",
                                slide.accentColor
                            )}
                        >
                            <SlideIcon className="size-full" />
                        </div>
                        <div className="space-y-1">
                            <DialogTitle className="text-lg font-semibold tracking-tight">
                                {slide.title}
                            </DialogTitle>
                            <p className="text-xs font-medium text-muted-foreground">
                                {slide.subtitle}
                            </p>
                        </div>
                    </div>
                </div>

                {/* Body Content */}
                <div className="px-6 py-5 space-y-4">
                    <DialogDescription className="text-sm leading-relaxed text-foreground/90">
                        {slide.description}
                    </DialogDescription>

                    <div className="rounded-xl border bg-muted/20 p-3.5 space-y-2">
                        <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                            Key Highlights
                        </p>
                        <ul className="space-y-1.5 text-xs text-muted-foreground">
                            {slide.points.map((point, index) => (
                                <li key={index} className="flex items-center gap-2">
                                    <div className="size-1.5 rounded-full bg-primary shrink-0" />
                                    <span>{point}</span>
                                </li>
                            ))}
                        </ul>
                    </div>
                </div>

                {/* Footer with Controls */}
                <DialogFooter className="border-t bg-muted/30 px-6 py-3.5 flex items-center justify-between sm:justify-between">
                    {/* Slide Dots */}
                    <div
                        className="flex items-center gap-1.5"
                        role="tablist"
                        aria-label="Tutorial slides"
                    >
                        {slides.map((_, idx) => (
                            <button
                                key={idx}
                                type="button"
                                role="tab"
                                aria-selected={idx === currentSlide}
                                aria-label={`Go to slide ${idx + 1}`}
                                onClick={() => setCurrentSlide(idx)}
                                className={cn(
                                    "size-2 rounded-full transition-all duration-200 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
                                    idx === currentSlide
                                        ? "w-6 bg-primary"
                                        : "bg-muted-foreground/30 hover:bg-muted-foreground/60"
                                )}
                            />
                        ))}
                    </div>

                    {/* Navigation Buttons */}
                    <div className="flex items-center gap-2">
                        {currentSlide > 0 && (
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handlePrev}
                                className="h-8 gap-1 text-xs"
                            >
                                <ChevronLeft className="size-3.5" />
                                Back
                            </Button>
                        )}
                        <Button
                            size="sm"
                            onClick={handleNext}
                            className="h-8 gap-1 text-xs"
                        >
                            <span>{isLastSlide ? "Start Exploring" : "Next"}</span>
                            {!isLastSlide && <ChevronRight className="size-3.5" />}
                        </Button>
                    </div>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
