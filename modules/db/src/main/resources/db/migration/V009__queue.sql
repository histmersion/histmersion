-- hist-db V009 — the bookkeeping queues (plan §5.1 row `bookkeeping`, §5.2 stream 2.8).
--
-- Replaces the research workspace's three hand-edited Markdown files with rows: `meta/ACQUIRE.md`
-- (what the USER must buy/borrow), `meta/TASKS.md` (the AGENT's backlog) and `meta/LOG.md` (the
-- chronological worklog). `histdb queue export` renders the three files back out of these rows and
-- `histdb queue import` parses them back in, so the Obsidian-readable files stay a projection of the
-- database rather than the source of truth.
--
-- `status`:
--   open  — an actionable item, `- [ ] text` in ACQUIRE/TASKS, a plain bullet in LOG
--   done  — `- [x] text`; `done_at` records when
--   note  — a VERBATIM Markdown block (preamble prose, a table, a numbered list, a `---` rule) that
--           belongs under `topic`'s heading path. Notes are never listed by `queue <list>`; they
--           exist so an imported file can be exported again without losing its hand-written prose.
-- Deliberately NOT constrained, so a later stream can add a status without a migration; `list` IS
-- constrained, because the three lists are the whole point of the convention.
--
-- `topic` is a heading PATH joined with " > ": "🟡 Outstanding > Bronze Age collapse" renders as a
-- `## ` heading then a `### ` one. Depth 3 renders as a bold group line (`**…**`), which is how the
-- real files group items. NULL topic = the file preamble, rendered before every heading.
--
-- `created_at` doubles as the LOG's chronology: entries are grouped by their heading and ordered
-- newest-first on it, so `queue log add` lands on top the way an appended log entry should.

CREATE TABLE IF NOT EXISTS queue_items (
  id         serial      PRIMARY KEY,
  list       text        NOT NULL CHECK (list IN ('acquire', 'tasks', 'log')),
  text       text        NOT NULL,
  topic      text,
  status     text        NOT NULL DEFAULT 'open',
  created_at timestamptz NOT NULL DEFAULT now(),
  done_at    timestamptz
);

-- the listing query is always (list, status) filtered and id ordered; the export reads a whole list
CREATE INDEX IF NOT EXISTS queue_items_list ON queue_items (list, status, id);
