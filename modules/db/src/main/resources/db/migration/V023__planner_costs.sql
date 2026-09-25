-- hist-db V023 — planner cost model for SSD-backed storage.
--
-- With the default random_page_cost (4.0, tuned for spinning disks) the planner prefers a sequential
-- scan of `source_chunks` whenever a word matches more than a few percent of chunks. A seq scan must
-- de-TOAST every chunk's tsvector just to test `@@`, which costs ~1 s on the corpus; the GIN bitmap
-- scan touches only the matching rows (~20 ms). 1.1 is the standard value for SSD/NVMe and makes the
-- planner take the index. Applies to every new session of this database (all roles).
DO $$
BEGIN
  EXECUTE format('ALTER DATABASE %I SET random_page_cost = 1.1', current_database());
END
$$;
