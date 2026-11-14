# Crash reports

A Worker that takes crash reports and usage statistics the app sends, each only with the user's
consent, and a console that reads them. Release builds are obfuscated, so each crash report is first
turned back into the original classes, methods and lines with its build's mapping, within the free
plan's 10 ms of CPU per invocation; reports of one problem are grouped across builds. Usage
statistics carry no identifier and are kept only as counts.

- `src/index.js` is the Worker: `POST /crash`, `POST /stats`, `/console`, and the scheduled runs.
- `src/group.js` says which reports are one problem.
- `src/retrace.js` retraces a trace from looked-up records.
- `src/mapping.js` reads an R8 mapping file and lays it out as shards.
- `src/shards.js` reads records from a build's mapping file, at once or over several runs.
- `src/retention.js` says which builds' mapping files to drop.
- `src/stats.js` counts heartbeats and gives the overview.
- `src/console.js`, `src/console.html` and `src/access.js` are the console and its sign-in check.
- `src/feedback.js` reads the group's feedback for the console and sets its status.
- `scripts/shard.mjs` writes a build's mapping file; `scripts/upload.py` puts it in the bucket.

Commands below run from this directory.

## Crash reports

`POST /crash` takes JSON of at most 64 KB:

| Field | |
|---|---|
| `version` | The build's versionName, with `+<commit>` on CI builds |
| `kind` | `crash`, `anr` or `native` |
| `trace` | The obfuscated stack trace; for an ANR, the main thread |
| `install` | A random id the app makes once, to count the installs a problem reaches |
| `time`, `sdk`, `device`, `rom`, `page`, `thread` | Optional |

It answers 202 once the report is kept, 400 for a malformed one, 429 past 20 reports from one
install in a day, and 503 while 5,000 wait to be filed.

Every minute the scheduled run files waiting reports. A trace seen before in the same build goes
straight to its problem. A new one needs lines from its build's mapping file: they are read 24
shards per run and kept in `crash_lines`, where later traces of the build find them, and once all
are there the trace is retraced and grouped. A build without a mapping file (a debug build, or one
whose file has gone) is grouped by its obfuscated names.

A problem is its kind, the exception at the root of the trace and the first three frames of the
app's own code, without line numbers. A problem marked fixed opens again, shown as come back, when
it shows up in a build it hadn't come from before; builds that had it before the fix still have it.

## Usage statistics

`POST /stats` takes one heartbeat a device sends at most once a UTC day. It carries no identifier;
active devices are counted from its flags, the first heartbeat of the ISO week and of the month.

| Field | |
|---|---|
| `version`, `channel` | versionName; `stable`, `beta` or empty |
| `sdk`, `brand`, `rom`, `form` | Android API level; manufacturer; ROM family and major version; `phone`, `tablet` or `foldable` |
| `crashed` | Whether the app crashed or froze since the previous heartbeat |
| `week`, `month` | First heartbeat of the ISO week, of the month |
| `installWeek` | Weeks since install, 0 to 7; left out later |
| `model`, `romVersion`, `locale` | Only with `month`: counted on their own, never crossed |

A heartbeat only adds to counts and is not kept, nor is anything else of the request. The answer
is `{ "rate": r }`: the share of devices that should send, 1 unless `STATS_RATE` is set on the
Worker to stay within the free plan; each heartbeat then counts for 1 / r. The console never shows
a count under 5.

## Setup

The app posts to `/crash` and `/stats` on the update host. That host serves the update bucket; the
Worker runs on routes for these paths and `/console` in front of it, and every other path stays
with the bucket. The host is kept out of the repository, so the routes are passed when deploying:
set `UPDATE_HOST` to the host name and pass them with every deploy.

```sh
npx wrangler d1 create oolong-crash            # then fill its id into wrangler.jsonc
npx wrangler r2 bucket create oolong-mappings  # private: no public access, no custom domain
npx wrangler d1 execute oolong-crash --remote --file schema.sql
npx wrangler deploy --route "$UPDATE_HOST/crash" --route "$UPDATE_HOST/stats" --route "$UPDATE_HOST/console*"
```

The console sits behind Cloudflare Access: a self-hosted application for `$UPDATE_HOST/console`
that lets in the owner's email. The Worker checks Access's token again, so set on it
`ACCESS_TEAM` (the team's `*.cloudflareaccess.com` host) and `ACCESS_AUD` (the application's
audience tag). Without them the console answers 401 to everyone.

## Reading

Open `https://$UPDATE_HOST/console`. Without Access:

```sh
npx wrangler d1 execute oolong-crash --remote --command "SELECT id, title, reports, last_version FROM crash_groups WHERE status = 'open' ORDER BY last_seen DESC"
```

To try it locally, put a mapping file in the local bucket, write `CONSOLE_DEV="1"` to `.dev.vars`
(ignored by git; it lets the console in without Access), run `npx wrangler dev --local
--test-scheduled`, post reports and call `/__scheduled?cron=*+*+*+*+*` for each run.

## Feedback

The console's 反馈 tab shows the feedback the bot collects from the Telegram group
(`tools/feedback-bot`): each item's write-up, the messages saved around it and a link to it in the
group, with its status. It reads the bot's database through the `FEEDBACK` binding in
`wrangler.jsonc`, the same `oolong-feedback` database the bot writes, and sets only `status`,
`fixed_by` and `note`; the bot adds items and their write-ups and never changes those. Without the
binding the tab says so and the rest of the console works as before.

Statuses are the bot's: `open`, `planned`, `fixed`, `declined` and `duplicate`. A note given with a
status replaces the one there; reopening keeps it.

## Mapping files in CI

Every CI build of a push writes its mapping file and uploads it (`ci.yml`); a release marks its
build as released, as `released/<versionName>` (`release.yml`). Both need the repository secrets
`CLOUDFLARE_API_TOKEN` (Workers R2 Storage Write) and `CLOUDFLARE_ACCOUNT_ID`, and skip the step
without them.

Once a day the Worker drops expired files: the latest 20 released builds keep theirs; any other
build's goes once it is 14 days old and no report has come from it in 14 days.

## Mapping file

A build's mapping becomes one file, kept in the private bucket as `mapping/<versionName>.bin`, `versionName`
exactly as the app reports it (with `+<commit>` on CI builds). A lookup reads it by range: the
header once, then each shard it needs.

| Bytes | Holds |
|---|---|
| 0–3 | `OLM1` |
| 4–7 | Shard count `n`, u32 little-endian |
| 8 – 8 + 4(n + 1) | Where each shard starts and the last ends, u32 little-endian |
| Then | The shards, each gzipped on its own |

A shard is lines `key<TAB>json`. A line's key is an obfuscated class name, holding `{ n, f?, y? }`
for the class, or `<class> <method>`, holding `{ n, f?, y?, g }` with that method's groups. A line
goes to shard `fnv1a(key) % n`, FNV-1a over the key's UTF-8 bytes; a lookup decompresses that
shard, finds `\n<key>\t` and parses that line alone. The fields are described at the top of
`src/mapping.js`.

With 4096 shards a mapping of 20,000 classes makes a 13 MB file with shards of at most 20 KB, and a
trace costs about 1.6 ms of CPU (3.6 ms at the 95th percentile) over some 9 reads.

```sh
node scripts/shard.mjs ../../app/build/outputs/mapping/release/mapping.txt mapping.bin
```

## Tests

```sh
node --test test/*.test.mjs
```

`test/fixture/` is the part of a release mapping a sample trace touches, with what R8's own retrace
makes of it. `test/compare.mjs` checks against R8 at scale: it builds traces from a whole mapping,
reaching every kind of entry in it, retraces them both ways and reports the differences.

```sh
JAVA=/usr/lib/jvm/java-17-openjdk-amd64/bin/java \
R8_JAR=$(ls ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/builder/<agp>/*/builder-<agp>.jar) \
node test/compare.mjs ../../app/build/outputs/mapping/release/mapping.txt 2000
```

Use the R8 that wrote the mapping (`# compiler_version` at its top); AGP's builder jar carries it.

Frames with a line number come out as R8's do, with these exceptions, where this gives more or is
more exact:

- An outline's call site whose code maps to the method's own lines: R8 shows no frame; this shows
  the line.
- An outline's call site in one of several overloads sharing an obfuscated name: R8 offers or picks
  the lines of another overload; this takes the call site's own.

For a frame without a line number, the candidates listed may differ from R8's: R8 sometimes lists
fewer, or none. In about 6,000 generated traces, 5–6% differ in one of these ways and no other.
