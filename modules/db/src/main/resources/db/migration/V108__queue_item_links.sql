-- V108: a bookkeeping item says where it came from. `goal_slug` = the research goal it serves;
-- `created_job` = the agent run or background job that wrote it (a worklog line, a backlog item, an item to
-- acquire); `closed_job` = the run or job that ticked it off. A person's own item has none of them. No
-- foreign keys: a goal may be renamed or deleted and a job pruned while the item and its history stay.
ALTER TABLE queue_items ADD COLUMN goal_slug text;
ALTER TABLE queue_items ADD COLUMN created_job text;
ALTER TABLE queue_items ADD COLUMN closed_job text;

-- What the existing rows already say. A goal that links an item (goal_links, kind queue-item) is its goal —
-- the first by slug when several do; else an acquisition item a goal's plan or watcher filed under the topic
-- "goal <slug>" is that goal's. An item an ingest job fulfilled was closed by that job.
UPDATE queue_items q SET goal_slug = l.goal_slug
  FROM (SELECT ref, min(goal_slug) AS goal_slug FROM goal_links WHERE kind = 'queue-item' AND ref ~ '^[0-9]+$' GROUP BY ref) l
 WHERE q.id = l.ref::int;
UPDATE queue_items q SET goal_slug = g.slug
  FROM goals g
 WHERE q.goal_slug IS NULL AND q.topic = 'goal ' || g.slug;
UPDATE queue_items q SET closed_job = j.id
  FROM (SELECT DISTINCT ON (acquire_item) acquire_item, id FROM jobs
         WHERE acquire_item IS NOT NULL AND status = 'done' ORDER BY acquire_item, updated_at DESC) j
 WHERE q.id = j.acquire_item AND q.status = 'done';

CREATE INDEX queue_items_goal ON queue_items (goal_slug, list, status) WHERE goal_slug IS NOT NULL;
CREATE INDEX queue_items_created_job ON queue_items (created_job) WHERE created_job IS NOT NULL;
CREATE INDEX queue_items_closed_job ON queue_items (closed_job) WHERE closed_job IS NOT NULL;
-- the activity list reads the worklog newest first, as it reads the jobs (V086) and the decisions
CREATE INDEX queue_items_log_page ON queue_items (created_at, id) WHERE list = 'log' AND status <> 'note';
CREATE INDEX approvals_decided_page ON approvals (decided_at, id) WHERE decided_at IS NOT NULL;
