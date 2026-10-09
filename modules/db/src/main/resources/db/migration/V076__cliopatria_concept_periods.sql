-- V076: a Cliopatria polity CONCEPT spans its time slices (2026-10-09).
--
-- The Cliopatria import stores every time slice of a polity as its own dated place
-- (`cliopatria_<slug>_<from>_<to>`) under a grouping node `cliopatria_<slug>` with no shape — and
-- that node was imported with NO period, so all 1 604 of them were "valid at every year" and showed
-- in every time-filtered view (a search at 2500 BCE offered the Free City of Kraków). The importer
-- now gives a concept the span of the dated slices that name it, as polity or as `MemberOf`; this
-- does the same for the stored ones. A concept none of whose slices is dated keeps the open range.
UPDATE places p
SET period = s.span
FROM (
  SELECT r.parent_id, int8range(min(lower(c.period)), max(upper(c.period)), '[)') AS span
  FROM place_relations r JOIN places c ON c.id = r.child_id
  WHERE r.parent_id LIKE 'cliopatria\_%' AND c.id LIKE 'cliopatria\_%'
    AND NOT lower_inf(c.period) AND NOT upper_inf(c.period) AND NOT isempty(c.period)
  GROUP BY r.parent_id
) s
WHERE p.id = s.parent_id
  AND p.geom IS NULL AND lower_inf(p.period) AND upper_inf(p.period)
  AND p.source LIKE 'Cliopatria%';
