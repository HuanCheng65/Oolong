/**
 * Collects feedback from the group. The bot keeps the group's messages for [KEEP_DAYS]; when the
 * owner reacts to one with [MARK], it is saved as feedback together with the message it answers
 * and the messages around it. Every few minutes a model writes up what was saved in a standard form,
 * with the screenshots that go with it (see [summarize]), and notes when it reports the same thing as
 * feedback already there. Taking the reaction back drops it again while it is still open.
 */

const MARK = "👀";
const KEEP_DAYS = 3;
// Messages before and after the marked one that are saved with it
const AROUND = 5;
// Hosted on Workers AI, within its free allowance; good at Chinese
const MODEL = "@cf/qwen/qwen3.8-27b";
// Screenshots shown to the model with one piece of feedback, at most
const IMAGES = 4;
// Feedback fixed within this many days is still shown to the model as something a report may repeat
const FIXED_DAYS = 60;
// The app's releases, to tell which version was the newest when a message was sent
const RELEASES = "https://api.github.com/repos/HuanCheng65/Oolong/releases?per_page=30";

export default {
    async fetch(request, env) {
        if (request.method !== "POST") return new Response("ok");
        if (!fromTelegram(request.headers.get("CF-Connecting-IP"))) {
            return new Response("forbidden", { status: 403 });
        }
        const update = await request.json();
        const message = update.message ?? update.edited_message;
        if (message && String(message.chat.id) === env.GROUP_ID) {
            await keep(env.DB, message);
        } else if (update.message_reaction && String(update.message_reaction.chat.id) === env.GROUP_ID) {
            await react(env.DB, update.message_reaction, env.OWNER_ID);
        }
        return new Response("ok");
    },

    async scheduled(controller, env) {
        const before = Math.floor(Date.now() / 1000) - KEEP_DAYS * 86400;
        await env.DB.prepare("DELETE FROM messages WHERE date < ?").bind(before).run();
        // Written up here rather than as it is marked: with screenshots to fetch and read, it takes
        // longer than a reply to Telegram may. One that fails is tried again on the next run.
        const { results } = await env.DB.prepare("SELECT message_id FROM feedback WHERE summary IS NULL ORDER BY marked_at LIMIT 10").all();
        if (!results.length) return;
        const releases = await fetchReleases();
        for (const row of results) await summarize(env, row.message_id, releases);
    },
};

// The addresses Telegram sends webhooks from
const TELEGRAM_NETWORKS = [["149.154.160.0", 20], ["91.108.4.0", 22]];

function fromTelegram(ip) {
    const value = (address) => address.split(".").reduce((n, part) => n * 256 + Number(part), 0);
    if (!ip || !/^\d+\.\d+\.\d+\.\d+$/.test(ip)) return false;
    return TELEGRAM_NETWORKS.some(([base, bits]) => {
        const size = 2 ** (32 - bits);
        return Math.floor(value(ip) / size) === Math.floor(value(base) / size);
    });
}

async function keep(db, message) {
    const from = message.from ?? {};
    const name = [from.first_name, from.last_name].filter(Boolean).join(" ") || from.username || null;
    const photo = message.photo?.at(-1)?.file_id ?? null;
    await db
        .prepare(
            `INSERT INTO messages (message_id, user_id, user_name, text, reply_to, photo, date)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT (message_id) DO UPDATE SET text = excluded.text, photo = excluded.photo`,
        )
        .bind(
            message.message_id,
            from.id ?? null,
            name,
            message.text ?? message.caption ?? null,
            message.reply_to_message?.message_id ?? null,
            photo,
            message.date,
        )
        .run();
}

/** Saves or drops feedback as the owner marks a message or takes the mark back. */
async function react(db, reaction, ownerId) {
    if (String(reaction.user?.id) !== ownerId) return;
    const has = (list) => list.some((r) => r.type === "emoji" && r.emoji === MARK);
    const id = reaction.message_id;
    if (has(reaction.new_reaction) && !has(reaction.old_reaction)) {
        const context = await around(db, id);
        await db
            .prepare("INSERT OR IGNORE INTO feedback (message_id, marked_at, context) VALUES (?, ?, ?)")
            .bind(id, reaction.date, JSON.stringify(context))
            .run();
    } else if (!has(reaction.new_reaction) && has(reaction.old_reaction)) {
        await db.prepare("DELETE FROM feedback WHERE message_id = ? AND status = 'open'").bind(id).run();
    }
}

/** The marked message, the one it answers, and the messages kept just before and after it. */
async function around(db, id) {
    const columns = "message_id, user_id, user_name, text, reply_to, photo, date";
    const { results } = await db
        .prepare(
            `SELECT ${columns} FROM (SELECT ${columns} FROM messages WHERE message_id < ? ORDER BY message_id DESC LIMIT ?)
             UNION SELECT ${columns} FROM messages WHERE message_id = ?
             UNION SELECT ${columns} FROM (SELECT ${columns} FROM messages WHERE message_id > ? ORDER BY message_id LIMIT ?)
             UNION SELECT ${columns} FROM messages WHERE message_id = (SELECT reply_to FROM messages WHERE message_id = ?)
             ORDER BY message_id`,
        )
        .bind(id, AROUND, id, id, AROUND, id)
        .all();
    return results;
}

const PROMPT = `你在整理 Oolong 用户群里的反馈。Oolong 是一款安卓贴吧客户端，原名贴吧 Lite。
用户消息里依次有：已有的反馈、消息发出时的最新版本、群聊片段。片段里带 ★ 的那条是被标记为反馈的消息，其余是上下文；每行开头是消息编号和发送时间，标「开发者」的是应用作者；附上的截图按［图片 n］的顺序对应。

先弄清这些说法：
- 「xx吧」是贴吧里一个讨论区的名字，如「复制粘贴吧」「抗压背锅吧」，在句子里是地点，不是动作。
- 帖子里的回复叫楼层，楼层下面的回复叫楼中楼；「回贴」一般指这两种。吧里的帖子列表有「最新」「精华」「热门」，吧规和简介在「关于本吧」。
- 应用的主要页面有首页、动态、消息；动态里有推荐、关注、热榜、话题榜。
- 外观设置里，界面风格分「悬浮」和「经典」，楼中楼样式分「时间线」和「经典」（群里有人把时间线样式叫「吊带」）；搜索栏可以放在「首页顶部」；「模糊」是半透明模糊效果的开关。
- 截图不一定是 Oolong：用户常拿官方贴吧、Telegram 等别的应用截图作对比。只有能看出是 Oolong 的截图，才算 Oolong 的现象。

整理时：
- 只整理 ★ 那条说的这一件事。上下文里别人或同一个人说的其他问题不要合进来。
- 只依据聊天内容和截图，没提到的字段填空字符串或空数组。steps 只写原话或截图能直接看出来的操作，看不出来就留空，不要为了完整去补。
- 原话没说、靠截图或上下文推断出来的字段（如 page、device、steps），把字段名放进 guessed。
- version：原话或截图里有版本就照写；说「最新版」就填给出的最新版本；都没有就留空，不要放进 missing。
- missing：只列排查这一条真正需要、而聊天和截图里都没有的信息，最多三项。建议和界面问题通常用不到机型和系统，没有就留空数组。
- kind：报 bug 用「问题」，想要新功能或改动用「建议」；用户以提问的口气描述了一个异常，也算「问题」；只是在问怎么用，才算「疑问」。
- same_as：如果这条和已有反馈里的某一条说的是同一个问题或同一个诉求，填那条的编号（数字）；只是相关、或同一页面的不同问题，填 null。没把握也填 null。

只输出一个 JSON 对象，不要其他文字：
{
  "kind": "问题" | "建议" | "疑问" | "其他",
  "title": "一句话标题，说清是什么问题或想要什么，不超过 30 字",
  "description": "两三句话描述现象或诉求",
  "steps": ["复现步骤，按顺序"],
  "expected": "期望的结果",
  "actual": "实际的结果",
  "version": "应用版本",
  "device": "机型",
  "system": "系统及版本，如 Android 15、HyperOS 2",
  "page": "涉及的页面或功能",
  "missing": ["排查这一条还缺的信息"],
  "guessed": ["推断出来的字段名"],
  "same_as": null
}`;

const STATUS_NAMES = { open: "未处理", planned: "已计划", fixed: "已修复" };
const FIELDS = ["steps", "expected", "actual", "version", "device", "system", "page"];

/**
 * Has the model write up the feedback saved under [id] in a standard form, kept as JSON in its
 * summary along with who reported it and a link to the message. When the model finds it reports the
 * same thing as earlier feedback, that one's id goes in duplicate_of; the status is left for the owner.
 * A version nobody gave is taken to be the newest of [releases] when the message was sent, and noted
 * as guessed. A failure is noted and tried again.
 */
async function summarize(env, id, releases) {
    const row = await env.DB.prepare("SELECT context FROM feedback WHERE message_id = ?").bind(id).first();
    if (!row) return;
    const context = JSON.parse(row.context);
    const marked = context.find((m) => m.message_id === id);
    // The screenshots that belong with it: its own, the one it answers, and the reporter's around it
    const shown = context
        .filter((m) => m.photo && (m.message_id === id || m.message_id === marked?.reply_to || sameSender(m, marked)))
        .slice(0, IMAGES);
    const lines = context.map((m) => {
        const who = `${m.user_name ?? "匿名"}${m.user_id && String(m.user_id) === env.OWNER_ID ? "（开发者）" : ""}`;
        const reply = m.reply_to ? `（回复 ${m.reply_to}）` : "";
        const index = shown.indexOf(m);
        const photo = !m.photo ? "" : index >= 0 ? `［图片 ${index + 1}］` : "［图片，未附上］";
        return `${m.message_id === id ? "★ " : ""}[${m.message_id} ${clock(m.date)}] ${who}${reply}：${photo}${m.text ?? ""}`;
    });
    const existing = await earlier(env.DB, id);
    const release = marked ? releaseAt(releases, marked.date) : null;
    const text = [
        "已有的反馈：",
        ...(existing.length ? existing.map((f) => `- ${f.message_id}：${f.title}（${STATUS_NAMES[f.status]}）`) : ["（无）"]),
        "",
        `消息发出时的最新版本：${release ? `${release.version}（${clock(release.date)} 发布）` : "未知"}`,
        "",
        "群聊片段：",
        ...lines,
    ].join("\n");
    try {
        const images = env.BOT_TOKEN ? await Promise.all(shown.map((m) => download(env.BOT_TOKEN, m.photo))) : [];
        const result = await env.AI.run(MODEL, {
            messages: [
                { role: "system", content: PROMPT },
                {
                    role: "user",
                    content: [
                        { type: "text", text },
                        ...images.filter(Boolean).map((url) => ({ type: "image_url", image_url: { url } })),
                    ],
                },
            ],
            // Picking facts out needs little thinking
            reasoning_effort: "low",
            max_tokens: 2000,
        });
        const output = result.choices?.[0]?.message?.content ?? result.response ?? "";
        const summary = JSON.parse(output.slice(output.indexOf("{"), output.lastIndexOf("}") + 1));
        const guessed = new Set(Array.isArray(summary.guessed) ? summary.guessed.filter((f) => FIELDS.includes(f)) : []);
        if (!summary.version && release) {
            summary.version = release.version;
            guessed.add("version");
        }
        summary.guessed = [...guessed];
        const duplicateOf = sameAs(summary.same_as, existing);
        delete summary.same_as;
        summary.reporter = marked?.user_name ?? null;
        summary.link = `https://t.me/c/${env.GROUP_ID.replace(/^-100/, "")}/${id}`;
        await env.DB.prepare("UPDATE feedback SET summary = ?, summary_error = NULL, duplicate_of = ? WHERE message_id = ?")
            .bind(JSON.stringify(summary), duplicateOf, id)
            .run();
    } catch (e) {
        await env.DB.prepare("UPDATE feedback SET summary_error = ? WHERE message_id = ?").bind(String(e).slice(0, 500), id).run();
    }
}

/**
 * The feedback a new report may repeat: what is still open or planned, and what was fixed lately,
 * newest first. One already noted as repeating another is left out, so a match lands on the first.
 */
async function earlier(db, id) {
    const since = Math.floor(Date.now() / 1000) - FIXED_DAYS * 86400;
    const { results } = await db
        .prepare(
            `SELECT message_id, status, json_extract(summary, '$.title') AS title FROM feedback
             WHERE message_id != ? AND summary IS NOT NULL AND duplicate_of IS NULL
               AND (status IN ('open', 'planned') OR (status = 'fixed' AND marked_at > ?))
             ORDER BY marked_at DESC LIMIT 60`,
        )
        .bind(id, since)
        .all();
    return results.filter((f) => f.title);
}

/** The id the model gave as the feedback this one repeats, if it is one of [existing]. */
function sameAs(value, existing) {
    const id = Number(value);
    return value != null && existing.some((f) => f.message_id === id) ? id : null;
}

/** The app's published releases, newest first, as {version, date}; none if GitHub can't be reached. */
async function fetchReleases() {
    try {
        const response = await fetch(RELEASES, {
            headers: { Accept: "application/vnd.github+json", "User-Agent": "oolong-feedback-bot" },
            cf: { cacheTtl: 3600, cacheEverything: true },
        });
        if (!response.ok) return [];
        return (await response.json())
            .filter((r) => !r.draft && r.published_at)
            .map((r) => ({ version: r.tag_name.replace(/^v/, ""), date: Math.floor(Date.parse(r.published_at) / 1000) }))
            .sort((a, b) => b.date - a.date);
    } catch {
        return [];
    }
}

/** The newest of [releases] out by [date]. */
function releaseAt(releases, date) {
    return releases.find((r) => r.date <= date) ?? null;
}

/** [date] in seconds as month, day and time in Beijing, as the group reads it. */
function clock(date) {
    const parts = Object.fromEntries(
        new Intl.DateTimeFormat("en-GB", {
            timeZone: "Asia/Shanghai",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            hourCycle: "h23",
        })
            .formatToParts(new Date(date * 1000))
            .map((p) => [p.type, p.value]),
    );
    return `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

/** Whether [a] and [b] were sent by the same person; by name for feedback saved before ids were kept. */
function sameSender(a, b) {
    if (!b) return false;
    return b.user_id ? a.user_id === b.user_id : a.user_name === b.user_name;
}

/** The picture Telegram keeps under [fileId], as a data URL for the model, or null if it can't be had. */
async function download(token, fileId) {
    try {
        const file = await (await fetch(`https://api.telegram.org/bot${token}/getFile?file_id=${encodeURIComponent(fileId)}`)).json();
        if (!file.ok) return null;
        const bytes = new Uint8Array(await (await fetch(`https://api.telegram.org/file/bot${token}/${file.result.file_path}`)).arrayBuffer());
        let binary = "";
        for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
        return `data:image/jpeg;base64,${btoa(binary)}`;
    } catch {
        return null;
    }
}
