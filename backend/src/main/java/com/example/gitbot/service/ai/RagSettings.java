package com.example.gitbot.service.ai;

public final class RagSettings {
    /** Default number of code chunks to fetch from the vector database per question. */
    public static final int DEFAULT_TOP_K_CHUNKS = 10;
    public static final int TOP_K_CHUNKS = DEFAULT_TOP_K_CHUNKS;

    /** Default character budget for retrieved code context sent to the LLM. */
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 12_000;

    /** Default maximum number of adjacent neighbor chunks to include. */
    public static final int DEFAULT_MAX_NEIGHBORING_CHUNKS = 4;

    /** Max time (ms) to keep an SSE stream open while the model is responding. */
    public static final long STREAM_TIMEOUT_MS = 180_000L;

    /** Metadata key stored on each embedded document (must match {@link com.example.gitbot.service.indexing.CodeChunker}). */
    public static final String METADATA_REPO_ID = "repoId";

    private RagSettings() {}
}
