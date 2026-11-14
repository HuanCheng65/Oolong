/**
 * Feedback from the Telegram group, which the feedback bot (tools/feedback-bot) keeps in its own
 * database. The console reads it through the FEEDBACK binding and sets an item's status, as the
 * README there describes; the bot writes only new items and their write-ups, so the two don't meet.
 */

export const FEEDBACK_STATUSES = new Set(["open", "planned", "fixed", "declined", "duplicate"]);

/** Every item, newest first, with its write-up. */
export async function feedbackList(db) {
    const { results } = await db
        .prepare("SELECT message_id, marked_at, status, note, fixed_by, summary, summary_error FROM feedback ORDER BY marked_at DESC")
        .all();
    return results.map(item);
}

/** One item with the conversation saved around it, or null. */
export async function feedbackDetail(db, id) {
    const row = await db.prepare("SELECT message_id, context FROM feedback WHERE message_id = ?").bind(id).first();
    if (!row) return null;
    let context = [];
    try {
        context = JSON.parse(row.context);
    } catch {}
    return {
        id: row.message_id,
        // Without the user ids or the photos' file ids, which the page has no use for
        messages: (Array.isArray(context) ? context : []).map((m) => ({
            id: m.message_id,
            name: m.user_name ?? null,
            text: m.text ?? null,
            replyTo: m.reply_to ?? null,
            photo: Boolean(m.photo),
            date: m.date * 1000,
            marked: m.message_id === row.message_id,
        })),
    };
}

/**
 * Sets an item's status, with the commit that fixed it. A note given replaces the one there; one
 * left out keeps it, so reopening an item doesn't lose what was written about it.
 */
export async function updateFeedback(db, id, body) {
    const status = body?.status;
    if (!FEEDBACK_STATUSES.has(status)) return { error: 400 };
    const text = (value, max) => (typeof value === "string" ? value.trim().slice(0, max) || null : null);
    const fixedBy = status === "fixed" ? text(body.fixedBy, 64) : null;
    const statement = typeof body.note === "string"
        ? db.prepare("UPDATE feedback SET status = ?, fixed_by = ?, note = ? WHERE message_id = ?").bind(status, fixedBy, text(body.note, 200), id)
        : db.prepare("UPDATE feedback SET status = ?, fixed_by = ? WHERE message_id = ?").bind(status, fixedBy, id);
    const result = await statement.run();
    return result.meta.changes ? { ok: true } : { error: 404 };
}

/** A row as the page reads it: the model's write-up with every field in the shape it should have. */
export function item(row) {
    return {
        id: row.message_id,
        marked: row.marked_at * 1000,
        status: row.status,
        note: row.note,
        fixedBy: row.fixed_by,
        summary: summary(row.summary),
        error: row.summary ? null : row.summary_error,
    };
}

const KINDS = new Set(["问题", "建议", "疑问", "其他"]);

/** The write-up the bot saved, or null while there is none; the model may have left fields out. */
export function summary(json) {
    let s;
    try {
        s = JSON.parse(json);
    } catch {
        return null;
    }
    if (!s || typeof s !== "object") return null;
    const str = (v) => (typeof v === "string" ? v.trim() : "");
    const list = (v) => (Array.isArray(v) ? v.filter((x) => typeof x === "string" && x.trim()).map((x) => x.trim()) : []);
    return {
        kind: KINDS.has(s.kind) ? s.kind : "其他",
        title: str(s.title),
        description: str(s.description),
        steps: list(s.steps),
        expected: str(s.expected),
        actual: str(s.actual),
        version: str(s.version),
        device: str(s.device),
        system: str(s.system),
        page: str(s.page),
        missing: list(s.missing),
        reporter: str(s.reporter),
        // The bot writes the link; only a Telegram one is let through to the page
        link: /^https:\/\/t\.me\/c\/\d+\/\d+$/.test(str(s.link)) ? str(s.link) : null,
    };
}
