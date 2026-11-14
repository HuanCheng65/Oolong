/**
 * The console: one page and the JSON it reads, behind Cloudflare Access (see access.js). Usage
 * statistics come from stats.js with small counts masked; crash problems from the crash tables;
 * feedback from the group from the feedback bot's database (feedback.js), bound as FEEDBACK.
 */

import { accessEmail } from "./access.js";
import page from "./console.html";
import { feedbackDetail, feedbackList, updateFeedback } from "./feedback.js";
import { overview } from "./stats.js";

const DAY = 86_400_000;
// A problem first seen within this long, and still open, is shown as new
const NEW_FOR = 3 * DAY;
const STATUSES = new Set(["open", "fixed", "ignored"]);

export async function handleConsole(request, env, url) {
    if (!(await accessEmail(request, env))) return new Response("sign in through Cloudflare Access", { status: 401 });
    const path = url.pathname.replace(/\/+$/, "");
    if (path === "/console") {
        return new Response(page, {
            headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-frame-options": "DENY", "referrer-policy": "no-referrer" },
        });
    }
    if (path === "/console/api/overview" && request.method === "GET") {
        return json(await overview(env.DB, url.searchParams.get("channel") ?? "", Date.now()));
    }
    if (path === "/console/api/groups" && request.method === "GET") return json(await groups(env.DB, Date.now()));
    const match = /^\/console\/api\/groups\/(\d+)$/.exec(path);
    if (match && request.method === "GET") {
        const detail = await group(env.DB, Number(match[1]));
        return detail ? json(detail) : new Response("not found", { status: 404 });
    }
    if (match && request.method === "POST") return update(env.DB, Number(match[1]), await request.json().catch(() => null));
    if (path === "/console/api/feedback" || path.startsWith("/console/api/feedback/")) return handleFeedback(request, env.FEEDBACK, path);
    return new Response("not found", { status: 404 });
}

/** The feedback list, one item, or a change to an item's status; 503 until FEEDBACK is bound. */
async function handleFeedback(request, db, path) {
    if (!db) return new Response("feedback database not bound", { status: 503 });
    if (path === "/console/api/feedback" && request.method === "GET") return json(await feedbackList(db));
    const match = /^\/console\/api\/feedback\/(\d+)$/.exec(path);
    if (match && request.method === "GET") {
        const detail = await feedbackDetail(db, Number(match[1]));
        return detail ? json(detail) : new Response("not found", { status: 404 });
    }
    if (match && request.method === "POST") {
        const result = await updateFeedback(db, Number(match[1]), await request.json().catch(() => null));
        return result.ok ? json(result) : new Response(result.error === 400 ? "bad status" : "not found", { status: result.error });
    }
    return new Response("not found", { status: 404 });
}

/** Every problem, with its reports per day over the last 14 days. */
async function groups(db, now) {
    const since = new Date(now - 13 * DAY).toISOString().slice(0, 10);
    const { results: rows } = await db
        .prepare(
            `SELECT g.id, g.kind, g.title, g.status, g.reports, g.first_version, g.first_seen, g.last_version, g.last_seen,
                    g.reopened_at, g.fixed_by, g.note, (SELECT COUNT(*) FROM crash_installs i WHERE i.group_id = g.id) AS installs
             FROM crash_groups g ORDER BY g.last_seen DESC`,
        )
        .all();
    const { results: counts } = await db
        .prepare("SELECT group_id, day, SUM(reports) AS reports FROM crash_counts WHERE day >= ? GROUP BY group_id, day")
        .bind(since)
        .all();
    const days = Array.from({ length: 14 }, (_, i) => new Date(now - (13 - i) * DAY).toISOString().slice(0, 10));
    return rows.map((g) => {
        const mine = new Map(counts.filter((c) => c.group_id === g.id).map((c) => [c.day, c.reports]));
        return {
            ...g,
            isNew: g.status === "open" && now - g.first_seen < NEW_FOR,
            returned: g.status === "open" && g.reopened_at !== null,
            daily: days.map((d) => mine.get(d) ?? 0),
        };
    });
}

/** One problem with its reports per build and its latest samples. */
async function group(db, id) {
    const g = await db.prepare("SELECT * FROM crash_groups WHERE id = ?").bind(id).first();
    if (!g) return null;
    const { results: versions } = await db
        .prepare("SELECT version, SUM(reports) AS reports FROM crash_counts WHERE group_id = ? GROUP BY version ORDER BY reports DESC")
        .bind(id)
        .all();
    const { results: samples } = await db
        .prepare("SELECT id, received, version, body, retraced FROM crash_samples WHERE group_id = ? ORDER BY id DESC")
        .bind(id)
        .all();
    return {
        id: g.id,
        versions,
        samples: samples.map((s) => {
            const body = JSON.parse(s.body);
            return { id: s.id, received: s.received, version: s.version, device: body.device, sdk: body.sdk, rom: body.rom, page: body.page, thread: body.thread, retraced: s.retraced };
        }),
    };
}

/** Marks a problem fixed (with the commit and a note), ignored, or open again. */
async function update(db, id, body) {
    const status = body?.status;
    if (!STATUSES.has(status)) return new Response("bad status", { status: 400 });
    const text = (value, max) => (typeof value === "string" ? value.trim().slice(0, max) : null);
    const result = await db
        .prepare(
            // Set by hand, a status is no longer a problem come back by itself
            "UPDATE crash_groups SET status = ?, fixed_by = ?, note = ?, reopened_at = NULL WHERE id = ?",
        )
        .bind(status, status === "fixed" ? text(body.fixedBy, 64) : null, text(body.note, 200), id)
        .run();
    return result.meta.changes ? json({ ok: true }) : new Response("not found", { status: 404 });
}

function json(value) {
    return new Response(JSON.stringify(value), { headers: { "content-type": "application/json", "cache-control": "no-store" } });
}
