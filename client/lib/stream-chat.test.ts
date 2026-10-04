import test from "node:test";
import assert from "node:assert/strict";

function parseSseChunk(chunk: string) {
    const tokens: string[] = [];
    const parts = chunk.split("\n\n");

    for (const part of parts) {
        if (!part.trim()) continue;

        const lines = part.split("\n");
        let event = "message";
        const dataLines: string[] = [];

        for (const line of lines) {
            if (line.startsWith("event:")) {
                event = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
                const rawLine = line.startsWith("data: ") ? line.slice(6) : line.slice(5);
                dataLines.push(rawLine);
            }
        }

        const data = dataLines.join("\n");
        if (!data && (event !== "token" || dataLines.length === 0)) continue;

        if (event === "token") {
            let tokenText = data;
            if (!data && dataLines.length > 0) {
                tokenText = " ";
            } else {
                try {
                    const parsed = JSON.parse(data);
                    if (typeof parsed === "string") {
                        tokenText = parsed;
                    }
                } catch {
                    // Plain text
                }
            }
            tokens.push(tokenText);
        }
    }

    return tokens;
}

test("stream-chat - preserves spaces in JSON-encoded SSE tokens", () => {
    // Tokens emitted as JSON strings (Spring SseEmitter output with jsonMapper)
    const stream = [
        'event:token\ndata:"Hello"\n\n',
        'event:token\ndata:" world"\n\n',
        'event:token\ndata:" this"\n\n',
        'event:token\ndata:" is"\n\n',
        'event:token\ndata:" a"\n\n',
        'event:token\ndata:" test"\n\n',
    ].join("");

    const tokens = parseSseChunk(stream);
    assert.deepStrictEqual(tokens, ["Hello", " world", " this", " is", " a", " test"]);
    assert.strictEqual(tokens.join(""), "Hello world this is a test");
});

test("stream-chat - preserves standalone whitespace and punctuation tokens", () => {
    const stream = [
        'event:token\ndata:"First"\n\n',
        'event:token\ndata:" "\n\n',
        'event:token\ndata:"Second"\n\n',
        'event:token\ndata:"\\n"\n\n',
        'event:token\ndata:"Third"\n\n',
    ].join("");

    const tokens = parseSseChunk(stream);
    assert.deepStrictEqual(tokens, ["First", " ", "Second", "\n", "Third"]);
    assert.strictEqual(tokens.join(""), "First Second\nThird");
});

test("stream-chat - preserves plain text fallback tokens with spaces", () => {
    const stream = [
        "event:token\ndata:First\n\n",
        "event:token\ndata: \n\n",
        "event:token\ndata:Second\n\n",
    ].join("");

    const tokens = parseSseChunk(stream);
    assert.strictEqual(tokens.join(""), "First Second");
});
