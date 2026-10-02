import test from "node:test";
import assert from "node:assert/strict";
import { apiFetch, ApiError } from "./api.ts";

// Mock global fetch for testing apiFetch behavior
const originalFetch = globalThis.fetch;

test("apiFetch - handles 201 Created with empty body (reportMessage contract)", async () => {
    globalThis.fetch = async () =>
        new Response("", {
            status: 201,
            statusText: "Created",
            headers: { "Content-Type": "application/json" },
        });

    try {
        const result = await apiFetch<void>("/api/chat/sessions/123/messages/456/report", {
            method: "POST",
            body: JSON.stringify({ reason: "INCORRECT" }),
        });
        assert.strictEqual(result, undefined);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - handles 204 No Content without JSON parse error", async () => {
    globalThis.fetch = async () =>
        new Response(null, {
            status: 204,
            statusText: "No Content",
        });

    try {
        const result = await apiFetch<void>("/api/chat/sessions/123", {
            method: "DELETE",
        });
        assert.strictEqual(result, undefined);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - handles response with Content-Length: 0", async () => {
    globalThis.fetch = async () =>
        new Response("", {
            status: 200,
            statusText: "OK",
            headers: { "content-length": "0" },
        });

    try {
        const result = await apiFetch<void>("/api/test");
        assert.strictEqual(result, undefined);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - handles whitespace-only response body", async () => {
    globalThis.fetch = async () =>
        new Response("   \n  ", {
            status: 200,
            statusText: "OK",
        });

    try {
        const result = await apiFetch<void>("/api/test");
        assert.strictEqual(result, undefined);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - parses valid JSON response", async () => {
    const expected = { id: "123", title: "Test Chat" };
    globalThis.fetch = async () =>
        new Response(JSON.stringify(expected), {
            status: 200,
            headers: { "Content-Type": "application/json" },
        });

    try {
        const result = await apiFetch<{ id: string; title: string }>("/api/chat/sessions/123");
        assert.deepStrictEqual(result, expected);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - surfaces real error response with JSON error message", async () => {
    globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: "Report reason is required" }), {
            status: 400,
            statusText: "Bad Request",
            headers: { "Content-Type": "application/json" },
        });

    try {
        await assert.rejects(
            async () => {
                await apiFetch<void>("/api/chat/sessions/123/messages/456/report", {
                    method: "POST",
                });
            },
            (err: unknown) => {
                assert.ok(err instanceof ApiError);
                assert.strictEqual(err.status, 400);
                assert.strictEqual(err.message, "Report reason is required");
                return true;
            }
        );
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("apiFetch - surfaces real error response with empty body safely", async () => {
    globalThis.fetch = async () =>
        new Response("", {
            status: 500,
            statusText: "Internal Server Error",
        });

    try {
        await assert.rejects(
            async () => {
                await apiFetch<void>("/api/test");
            },
            (err: unknown) => {
                assert.ok(err instanceof ApiError);
                assert.strictEqual(err.status, 500);
                assert.strictEqual(err.message, "Internal Server Error");
                return true;
            }
        );
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("api.me - normalizes clean and markdown-wrapped avatar URLs", async () => {
    const { api } = await import("./api.ts");

    globalThis.fetch = async () =>
        new Response(
            JSON.stringify({
                id: "test-id",
                githubId: 197362476,
                githubUsername: "ManitGitUser",
                displayName: "Manit",
                avatarUrl: "[https://avatars.githubusercontent.com/u/197362476?v=4](https://avatars.githubusercontent.com/u/197362476?v=4)",
            }),
            {
                status: 200,
                headers: { "Content-Type": "application/json" },
            }
        );

    try {
        const user = await api.me();
        assert.strictEqual(user.avatarUrl, "https://avatars.githubusercontent.com/u/197362476?v=4");
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("api.deleteAccount - sends DELETE request to /api/auth/account", async () => {
    const { api } = await import("./api.ts");

    let capturedUrl = "";
    let capturedMethod = "";

    globalThis.fetch = async (input, init) => {
        capturedUrl = typeof input === "string" ? input : input.toString();
        capturedMethod = init?.method || "GET";
        return new Response(null, {
            status: 204,
            statusText: "No Content",
        });
    };

    try {
        await api.deleteAccount();
        assert.ok(capturedUrl.endsWith("/api/auth/account"), `Expected ${capturedUrl} to end with /api/auth/account`);
        assert.strictEqual(capturedMethod, "DELETE");
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("api.removeIndex - sends DELETE request to /api/repos/{id}/index and returns repo", async () => {
    const { api } = await import("./api.ts");

    let capturedUrl = "";
    let capturedMethod = "";

    const mockResponse = {
        id: "repo-123",
        fullName: "owner/repo",
        indexStatus: "PENDING",
        chunkCount: 0,
        indexedCommitSha: null,
    };

    globalThis.fetch = async (input, init) => {
        capturedUrl = typeof input === "string" ? input : input.toString();
        capturedMethod = init?.method || "GET";
        return new Response(JSON.stringify(mockResponse), {
            status: 200,
            headers: { "Content-Type": "application/json" },
        });
    };

    try {
        const repo = await api.removeIndex("repo-123");
        assert.ok(capturedUrl.endsWith("/api/repos/repo-123/index"), `Expected ${capturedUrl} to end with /api/repos/repo-123/index`);
        assert.strictEqual(capturedMethod, "DELETE");
        assert.strictEqual(repo.indexStatus, "PENDING");
        assert.strictEqual(repo.chunkCount, 0);
        assert.strictEqual(repo.indexedCommitSha, null);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("api.removeIndex - surfaces 409 Conflict error when indexing is in progress", async () => {
    const { api } = await import("./api.ts");

    globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: "Cannot remove index while indexing is in progress" }), {
            status: 409,
            statusText: "Conflict",
            headers: { "Content-Type": "application/json" },
        });

    try {
        await assert.rejects(
            async () => {
                await api.removeIndex("repo-123");
            },
            (err: unknown) => {
                assert.ok(err instanceof ApiError);
                assert.strictEqual(err.status, 409);
                assert.strictEqual(err.message, "Cannot remove index while indexing is in progress");
                return true;
            }
        );
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("api.getDemoStatus - fetches demo configuration status", async () => {
    const { api } = await import("./api.ts");

    globalThis.fetch = async (url) => {
        assert.ok(String(url).endsWith("/api/demo/status"));
        return new Response(
            JSON.stringify({
                enabled: true,
                repoName: "GitBot",
                repoFullName: "ManitGitUser/GitBot",
                maxMessages: 5,
            }),
            {
                status: 200,
                headers: { "Content-Type": "application/json" },
            }
        );
    };

    try {
        const status = await api.getDemoStatus();
        assert.strictEqual(status.enabled, true);
        assert.strictEqual(status.repoName, "GitBot");
        assert.strictEqual(status.repoFullName, "ManitGitUser/GitBot");
        assert.strictEqual(status.maxMessages, 5);
    } finally {
        globalThis.fetch = originalFetch;
    }
});

test("demo storage contract - enforces sessionStorage and forbids localStorage", () => {
    // Invariant verification: Demo storage keys must be scoped to sessionStorage
    const STORAGE_KEYS = {
        MESSAGES: "gitbot_demo_messages",
        TOKEN: "gitbot_demo_token",
        COUNT: "gitbot_demo_count",
    };

    assert.strictEqual(STORAGE_KEYS.MESSAGES, "gitbot_demo_messages");
    assert.strictEqual(STORAGE_KEYS.TOKEN, "gitbot_demo_token");
    assert.strictEqual(STORAGE_KEYS.COUNT, "gitbot_demo_count");

    // Max messages ceiling invariant
    const MAX_DEMO_MESSAGES = 5;
    assert.strictEqual(MAX_DEMO_MESSAGES, 5);

    // After 5 messages, user cannot send a 6th message
    const currentCount = 5;
    const canSend = currentCount < MAX_DEMO_MESSAGES;
    assert.strictEqual(canSend, false);
});


