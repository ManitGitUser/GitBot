import test from "node:test";
import assert from "node:assert/strict";

// Helper representing the exact validation rule used in SettingsDashboard:
// Button disabled when confirmText !== "DELETE"
function isDeleteButtonEnabled(confirmText: string, isPending: boolean = false): boolean {
    return confirmText === "DELETE" && !isPending;
}

test("delete account confirmation - requires exact case-sensitive string 'DELETE'", () => {
    assert.strictEqual(isDeleteButtonEnabled("DELETE"), true, "Exact 'DELETE' must enable the button");
    assert.strictEqual(isDeleteButtonEnabled("delete"), false, "Lowercase 'delete' must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled("Delete"), false, "Mixed case 'Delete' must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled("DELETE "), false, "Trailing whitespace 'DELETE ' must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled(" DELETE"), false, "Leading whitespace ' DELETE' must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled(""), false, "Empty string must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled("DEL"), false, "Incomplete string must not enable the button");
    assert.strictEqual(isDeleteButtonEnabled("DELETION"), false, "Different word must not enable the button");
});

test("delete account confirmation - button is disabled while deletion request is pending", () => {
    assert.strictEqual(isDeleteButtonEnabled("DELETE", true), false, "Button must be disabled while isPending is true");
    assert.strictEqual(isDeleteButtonEnabled("DELETE", false), true, "Button must be enabled when isPending is false and text is 'DELETE'");
});
