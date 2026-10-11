-- Histmersion V083 — the web UI's "something new" marks (core `UiNotices`).
--
-- The toolbar draws a window's button bold while that window has items created or changed after the
-- reader last opened it. `seen_at` is the newest change the opened window showed (the `latest` of
-- `GET /api/ui/counters`), so an item that arrives while the page is loading is still news next time.
-- Kept here rather than in the browser so every browser and device agrees; one row per window key
-- (`goals`, `tasks`, `approvals`, `acquire`, `log`), written by `POST /api/ui/seen/{board}`, and the
-- marker only moves forward.
CREATE TABLE IF NOT EXISTS ui_seen (
  board   text        PRIMARY KEY,
  seen_at timestamptz NOT NULL
);

-- A baseline at upgrade time, so an existing corpus does not light up every button at once: what is
-- already there is not news. A board added later starts unmarked (anything in it is new until opened).
INSERT INTO ui_seen (board, seen_at)
SELECT b, now() FROM unnest(ARRAY['goals', 'tasks', 'approvals', 'acquire', 'log']) AS b
ON CONFLICT (board) DO NOTHING;
