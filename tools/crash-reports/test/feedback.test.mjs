import assert from "node:assert/strict";
import { test } from "node:test";
import { feedbackDetail, item, summary, updateFeedback } from "../src/feedback.js";

test("gives the write-up every field, whatever the model left out", () => {
    const s = summary(JSON.stringify({ kind: "建议", title: " 加个按钮 ", steps: ["打开", 3, " "], reporter: "Mio", link: "https://t.me/c/1447168214/73325" }));
    assert.equal(s.kind, "建议");
    assert.equal(s.title, "加个按钮");
    assert.deepEqual(s.steps, ["打开"]);
    assert.deepEqual(s.missing, []);
    assert.equal(s.expected, "");
    assert.equal(s.link, "https://t.me/c/1447168214/73325");
});

test("lets only a Telegram link and a known kind through", () => {
    const s = summary(JSON.stringify({ kind: "bug", link: "javascript:alert(1)" }));
    assert.equal(s.kind, "其他");
    assert.equal(s.link, null);
    assert.equal(summary(null), null);
    assert.equal(summary("not json"), null);
});

test("shows the failure only while there is no write-up", () => {
    const row = { message_id: 1, marked_at: 1793775313, status: "open", note: null, fixed_by: null, summary: null, summary_error: "timeout" };
    assert.equal(item(row).error, "timeout");
    assert.equal(item(row).marked, 1793775313000);
    assert.equal(item({ ...row, summary: "{}" }).error, null);
});

test("leaves the user ids and photo file ids out of the conversation", async () => {
    const context = [
        { message_id: 9, user_id: 42, user_name: "A", text: "坏了", reply_to: null, photo: "AgAC", date: 1793775300 },
        { message_id: 10, user_id: 43, user_name: "B", text: null, reply_to: 9, photo: null, date: 1793775310 },
    ];
    const db = { prepare: () => ({ bind: () => ({ first: async () => ({ message_id: 9, context: JSON.stringify(context) }) }) }) };
    const d = await feedbackDetail(db, 9);
    assert.deepEqual(d.messages[0], { id: 9, name: "A", text: "坏了", replyTo: null, photo: true, date: 1793775300000, marked: true });
    assert.equal(d.messages[1].marked, false);
    assert.ok(!JSON.stringify(d).includes("AgAC") && !JSON.stringify(d).includes("42"));
});

test("keeps the note unless one is given, and the commit only when fixed", async () => {
    const run = async (body) => {
        const calls = [];
        const db = { prepare: (sql) => ({ bind: (...args) => ({ run: async () => (calls.push([sql, args]), { meta: { changes: 1 } }) }) }) };
        const result = await updateFeedback(db, 7, body);
        return { result, calls };
    };
    assert.deepEqual((await run({ status: "wontfix" })).result, { error: 400 });
    let { calls } = await run({ status: "open" });
    assert.match(calls[0][0], /SET status = \?, fixed_by = \? WHERE/);
    assert.deepEqual(calls[0][1], ["open", null, 7]);
    ({ calls } = await run({ status: "fixed", fixedBy: " a8ee19de ", note: "  " }));
    assert.deepEqual(calls[0][1], ["fixed", "a8ee19de", null, 7]);
    ({ calls } = await run({ status: "duplicate", fixedBy: "a8ee19de", note: "同 73321" }));
    assert.deepEqual(calls[0][1], ["duplicate", null, "同 73321", 7]);
});
