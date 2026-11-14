# Feedback bot

Collects feedback from the Telegram group. The bot keeps the group's messages for three days;
when the owner reacts to one with 👀, it is saved to the `feedback` table with the message it
answers and five messages either side. Taking the reaction back removes it while it is open.

## Setup

1. In BotFather, turn the bot's privacy mode off (`/setprivacy` → Disable), then make it an
   admin of the group with no rights; it needs both to see messages and reactions.
2. Create the database and fill its id into `wrangler.jsonc`:

   ```sh
   npx wrangler d1 create oolong-feedback
   npx wrangler d1 execute oolong-feedback --remote --file schema.sql
   ```

3. Deploy, then give it the group's id and the owner's Telegram user id:

   ```sh
   npx wrangler deploy
   npx wrangler secret put GROUP_ID
   npx wrangler secret put OWNER_ID
   ```

4. Point the bot at the worker; it takes updates only from Telegram's addresses:

   ```sh
   curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
     -d url=https://oolong-feedback-bot.<subdomain>.workers.dev \
     -d 'allowed_updates=["message","edited_message","message_reaction"]'
   ```

## Reading

The console of `tools/crash-reports` shows the feedback and sets its status. From the command line:

```sh
npx wrangler d1 execute oolong-feedback --remote --command "SELECT * FROM feedback WHERE status = 'open'"
npx wrangler d1 execute oolong-feedback --remote --command "UPDATE feedback SET status = 'fixed', fixed_by = '<commit>', note = '…' WHERE message_id = …"
```

`status` is `open`, `planned` (taken into other work), `fixed`, `declined` or `duplicate`. Taking the
👀 back removes an item only while it is `open`.
