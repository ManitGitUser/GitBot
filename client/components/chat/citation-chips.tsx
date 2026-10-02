"use client";

import { ExternalLink } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { Citation } from "@/lib/api";

export function encodeFilePath(filePath: string): string {
    return filePath
        .split("/")
        .map((segment) => encodeURIComponent(segment))
        .join("/");
}

export function citationHref(
    repo: { fullName: string; defaultBranch?: string | null },
    citation: Citation
) {
    const fullName = citation.repoFullName || repo.fullName;
    const branch = repo.defaultBranch || "main";
    const encodedPath = encodeFilePath(citation.filePath);
    const line =
        citation.startLine != null
            ? `#L${citation.startLine}${
                citation.endLine && citation.endLine !== citation.startLine
                    ? `-L${citation.endLine}`
                    : ""
            }`
            : "";
    return `https://github.com/${fullName}/blob/${branch}/${encodedPath}${line}`;
}

export function CitationChips({
                                  repo,
                                  citations,
                              }: {
    repo: { fullName: string; defaultBranch?: string | null };
    citations: Citation[];
}) {
    if (!citations.length) return null;

    return (
        <div className="flex flex-wrap gap-1.5 pt-1">
            {citations.map((citation, index) => (
                <Badge
                    key={`${citation.filePath}-${index}`}
                    variant="outline"
                    render={
                        <a
                            href={citationHref(repo, citation)}
                            target="_blank"
                            rel="noreferrer"
                        />
                    }
                    className="max-w-full gap-1 font-normal"
                >
          <span className="truncate">
            {citation.filePath}
              {citation.startLine != null ? `:${citation.startLine}` : ""}
          </span>
                    <ExternalLink className="size-3 opacity-60" />
                </Badge>
            ))}
        </div>
    );
}