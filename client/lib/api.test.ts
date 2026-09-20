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
