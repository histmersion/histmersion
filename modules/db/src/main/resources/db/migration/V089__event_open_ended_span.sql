-- V089: an OPEN-ENDED event (a start and no end, or an end and no start) covers the years it states,
-- not every year after (or before) them (#1768).
--
-- V017's `outer_span` was int8range(coalesce(not_before, begin_by), coalesce(not_after, end_from)): an
-- event recorded with a start alone had an UNBOUNDED upper end, so it overlapped every later period and
-- stood in every later view of the map (`/api/events/geo`, the events list, a place's dated events).
-- The rule is now core.EventGeo.outer's, which the web already draws by and which the catalogue
-- importers' CatalogDating.span already writes: the one known end stands for both — an event with only a
-- start is an event AT its start (its start window, `not_before … begin_by`, when both are known), like
-- a battle whose end no one recorded. An event that is still going on states an end (the present year);
-- nothing is inferred from its kind. An undated event keeps the unbounded range (both ends NULL), as
-- before: every reader already leaves `lower_inf AND upper_inf` out of a time view, and the map's feed
-- leaves it out by its NULL `uncertainty`. `least`/`greatest` keep a range whose recorded points are out
-- of order from failing the row (EventGeo.outer orders them the same way).
--
-- A generated column cannot be altered in place: drop it (its GiST index goes with it) and add it again.

ALTER TABLE events DROP COLUMN IF EXISTS outer_span;
ALTER TABLE events ADD COLUMN outer_span int8range GENERATED ALWAYS AS (
  int8range(
    least(coalesce(not_before, begin_by, end_from, not_after), coalesce(not_after, end_from, begin_by, not_before)),
    greatest(coalesce(not_before, begin_by, end_from, not_after), coalesce(not_after, end_from, begin_by, not_before)),
    '[]')
) STORED;
CREATE INDEX IF NOT EXISTS events_outer_span ON events USING gist (outer_span);
