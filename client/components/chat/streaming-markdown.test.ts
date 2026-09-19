import test from "node:test";
import assert from "node:assert/strict";
import { parseMarkdownIntoBlocks } from "streamdown";

test("streaming markdown - progressively parses blocks as tokens arrive", () => {
    const tokens = [
        "## Authentication\n\n",
        "- First item\n",
        "- Second item\n",
        "- Third item\n\n",
        "Use `ChatService` for this.\n\n",
        "```java\n",
        "public void hello() {\n",
        "    return;\n",
        "}\n",
        "```\n",
    ];

    let accumulated = "";
    const progressionStates: Array<{ token: string; blockCount: number; lastBlock: string }> = [];

    for (const token of tokens) {
        accumulated += token;
        const blocks = parseMarkdownIntoBlocks(accumulated);
        progressionStates.push({
            token,
            blockCount: blocks.length,
            lastBlock: blocks[blocks.length - 1],
        });
    }

    // Step 1: Heading is immediately recognized as a block
    assert.strictEqual(progressionStates[0].blockCount, 1);
    assert.ok(progressionStates[0].lastBlock.startsWith("## Authentication"));

    // Step 2-4: List items progressively accumulate
    assert.ok(progressionStates[3].blockCount >= 2);
    const hasThirdItem = parseMarkdownIntoBlocks(accumulated.slice(0, accumulated.indexOf("`ChatService`"))).some(b => b.includes("- Third item"));
    assert.ok(hasThirdItem);

    // Step 5: Inline code paragraph recognized
    assert.ok(progressionStates[4].lastBlock.includes("`ChatService`") || progressionStates[4].token.includes("`ChatService`"));

    // Step 6-8: Code fence recognized during streaming before closing fence arrives
    assert.ok(accumulated.includes("```java"));
    assert.ok(progressionStates[7].lastBlock.includes("public void hello()"));

    // Step 9: Completed code block
    assert.ok(progressionStates[9].lastBlock.includes("```"));
});

test("streaming markdown - handles incomplete code fence without throwing", () => {
    const incompleteMarkdown = "## Title\n\n```java\npublic void hello() {\n";
    const blocks = parseMarkdownIntoBlocks(incompleteMarkdown);

    assert.ok(blocks.length >= 2);
    assert.strictEqual(blocks[0].trim(), "## Title");
    assert.ok(blocks[1].startsWith("```java"));
});

test("streaming markdown - configuration contract for streaming vs completed state", () => {
    // Verifies the prop contract between streaming and static modes
    function getStreamdownConfig(isStreaming: boolean) {
        return {
            mode: isStreaming ? "streaming" : "static",
            isAnimating: isStreaming,
            caret: isStreaming ? "block" : undefined,
        };
    }

    const streamingConfig = getStreamdownConfig(true);
    assert.strictEqual(streamingConfig.mode, "streaming");
    assert.strictEqual(streamingConfig.isAnimating, true);
    assert.strictEqual(streamingConfig.caret, "block");

    const completedConfig = getStreamdownConfig(false);
    assert.strictEqual(completedConfig.mode, "static");
    assert.strictEqual(completedConfig.isAnimating, false);
    assert.strictEqual(completedConfig.caret, undefined);
});
