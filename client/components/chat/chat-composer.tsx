"use client";

import { useEffect, useRef, useState } from "react";
import { SendHorizontal, Square } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Kbd } from "@/components/ui/kbd";
import { Spinner } from "@/components/ui/spinner";

export function ChatComposer({
    disabled,
    streaming,
    onSend,
    onStop,
}: {
    disabled?: boolean;
    streaming?: boolean;
    onSend: (content: string) => void | Promise<void>;
    onStop?: () => void;
}) {
    const [value, setValue] = useState("");
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Auto focus textarea when streaming completes and input becomes usable
    useEffect(() => {
        if (!streaming && !disabled) {
            textareaRef.current?.focus();
        }
    }, [streaming, disabled]);

    async function submit() {
        const content = value.trim();
        if (!content || disabled || streaming) return;
        setValue("");
        await onSend(content);
    }

    return (
        <div className="border-t bg-background/80 p-4 backdrop-blur">
            <div className="mx-auto max-w-3xl space-y-2">
                <div className="flex items-end gap-2 rounded-2xl border bg-card p-2 shadow-xs transition-colors focus-within:border-ring/60 focus-within:ring-1 focus-within:ring-ring/40">
                    <Textarea
                        ref={textareaRef}
                        value={value}
                        onChange={(e) => setValue(e.target.value)}
                        placeholder="Ask about architecture, files, flows…"
                        disabled={disabled}
                        aria-label="Ask a question about this repository"
                        className="min-h-12 flex-1 border-0 bg-transparent px-3 py-2 shadow-none focus-visible:ring-0 resize-none text-sm"
                        rows={1}
                        onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey) {
                                e.preventDefault();
                                void submit();
                            } else if (e.key === "Escape" && streaming && onStop) {
                                e.preventDefault();
                                onStop();
                            }
                        }}
                    />
                    {streaming ? (
                        <Button
                            size="icon-lg"
                            variant="secondary"
                            onClick={onStop}
                            aria-label="Stop generating response"
                            className="shrink-0 text-destructive hover:bg-destructive/10 hover:text-destructive"
                            title="Stop generating (Esc)"
                        >
                            <Square className="size-4 fill-current" />
                        </Button>
                    ) : (
                        <Button
                            size="icon-lg"
                            disabled={disabled || !value.trim()}
                            onClick={() => void submit()}
                            aria-label="Send question to GitBot"
                            className="shrink-0"
                        >
                            {disabled ? <Spinner /> : <SendHorizontal className="size-4" />}
                        </Button>
                    )}
                </div>

                <div className="flex items-center justify-between px-1 text-xs text-muted-foreground">
                    <span>
                        {streaming ? (
                            <span className="text-muted-foreground/90">
                                Generating response… Press <Kbd>Esc</Kbd> to stop
                            </span>
                        ) : (
                            <span>
                                Press <Kbd>Enter</Kbd> to send · <Kbd>Shift</Kbd> + <Kbd>Enter</Kbd> for newline
                            </span>
                        )}
                    </span>
                    {value.length > 500 && (
                        <span className="text-[11px] opacity-70">
                            {value.length} characters
                        </span>
                    )}
                </div>
            </div>
        </div>
    );
}