-- The parts of the demo collection no endpoint writes, applied by scripts/docs/screenshots.sh with psql
-- to the THROWAWAY database after load.mjs (never to a real collection):
--   1. each source's licence (set by acquisition, which the demo does not run);
--   2. one museum picture with its credit line, as the Cleveland Museum of Art collection plugin stores
--      it (MediaRepo.save + link) — a provider picture cannot be uploaded, only imported online;
--   3. two approvals, one decided and one open, as an agent run records them (AgentRepo.propose);
--   4. fixed timestamps, so the screenshots do not change from one run to the next.
-- psql variables: :picture and :thumb, the paths of the picture and of its thumbnail.
\set ON_ERROR_STOP on
BEGIN;

-- 1. licences (Crossref `license` of each version of record, checked 2026-10-19)
UPDATE sources SET license = 'CC-BY-4.0';

-- 2. Miniature stirrup jar, Cyprus, c. 1350–1300 BCE — The Cleveland Museum of Art 1916.2002, CC0
\set picture_b64 `base64 < :'picture' | tr -d '\n'`
\set thumb_b64 `base64 < :'thumb' | tr -d '\n'`
INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height, kind, license, provider, provider_key,
                    source_url, original_url, author, credit, license_url, retrieved_at, caption,
                    thumb, thumb_mime, thumb_width, thumb_height, geom, period)
SELECT NULL, 'cleveland-museum-96291.jpg', 'image/jpeg', b, encode(sha256(b), 'hex'), 640, 640, 'photo',
       'CC0-1.0', 'cleveland-museum', '96291', 'https://clevelandart.org/art/1916.2002',
       'https://openaccess-cdn.clevelandart.org/1916.2002/1916.2002_web.jpg', NULL,
       'Miniature Stirrup Jar, The Cleveland Museum of Art, Gift of the John Huntington Art and Polytechnic Trust, CC0',
       'https://creativecommons.org/publicdomain/zero/1.0/', '2026-10-12T09:30:00Z',
       'Miniature Stirrup Jar, c. 1350–1300 BCE. Cyprus, late Helladic III A2. Cypro-Mycenaean ware.',
       decode(:'thumb_b64', 'base64'), 'image/jpeg', 240, 240,
       (SELECT ST_PointOnSurface(coalesce(geom_clipped, geom)) FROM places WHERE id = 'pleiades_707498'),
       int8range(-1350, -1300, '[]')
FROM (SELECT decode(:'picture_b64', 'base64') AS b) x;
INSERT INTO media_links (asset_uid, target_type, target_id, role, rank, basis)
SELECT uid, 'place', 'pleiades_707498', 'object', 0, 'culture = Cyprus, late Helladic III A2; 1350–1300 BCE'
FROM assets WHERE provider = 'cleveland-museum' AND provider_key = '96291';

-- 3. approvals: the disagreement edge the demo carries, approved; and one more, still open
INSERT INTO approvals (kind, citekey, edge_kind, target, proposal, state, note, result, created_at, decided_at)
VALUES ('edge', 'manning2020_beyond', 'challenges', 'kaniewski2013_environmental',
        jsonb_build_object('kind', 'edge', 'citekey', 'manning2020_beyond', 'edgeKind', 'challenges',
          'target', 'kaniewski2013_environmental',
          'evidence', 'Manning et al. present Tell Tayinat as an alternative to the megadrought-and-collapse narrative that Kaniewski et al. (2013) argue for.',
          'quote', 'offer us one rich window into a resilient and successful response, and alternative to collapse, in the face of regional megadrought in the Old World'),
        'approved', 'checked against the introduction', 'edge added', '2026-10-12T09:40:00Z', '2026-10-12T09:55:00Z'),
       ('edge', 'molloy2022_there', 'challenges', 'kaniewski2013_environmental',
        jsonb_build_object('kind', 'edge', 'citekey', 'molloy2022_there', 'edgeKind', 'challenges',
          'target', 'kaniewski2013_environmental',
          'evidence', 'Molloy rejects climate determinism; Kaniewski et al. (2013) make drought the ultimate cause of the collapse.',
          'quote', 'The paper explicitly rejects both climate determinism and the dismissal of climate as irrelevant'),
        'open', NULL, NULL, '2026-10-12T09:45:00Z', NULL);

-- 4. fixed timestamps (the screenshot script fixes the browser's clock at 2026-10-19 12:00 UTC); rows
--    written at the same moment keep their order, a minute apart
UPDATE sources SET added_date = '2026-10-12T08:30:00Z', updated_at = '2026-10-12T09:00:00Z';
UPDATE articles SET updated_at = '2026-10-12T10:00:00Z';
UPDATE article_versions SET created_at = '2026-10-12T10:00:00Z', updated_at = '2026-10-12T10:00:00Z';
UPDATE events SET updated_at = '2026-10-12T09:20:00Z';
UPDATE attestations SET created_at = '2026-10-12T09:25:00Z', updated_at = '2026-10-12T09:25:00Z';
UPDATE goals SET created_at = '2026-10-12T08:00:00Z', updated_at = '2026-10-12T08:00:00Z';
UPDATE goal_links SET created_at = '2026-10-12T10:00:00Z';
UPDATE queue_items SET updated_at = created_at;
UPDATE agent_managers SET created_at = '2026-10-12T10:05:00Z', updated_at = '2026-10-12T10:05:00Z';
UPDATE manager_items SET created_at = '2026-10-12T10:06:00Z', updated_at = '2026-10-12T10:06:00Z';
UPDATE manager_events m SET at = '2026-10-12T10:05:00Z'::timestamptz + (x.n * interval '1 minute')
FROM (SELECT ctid, row_number() OVER (ORDER BY at) AS n FROM manager_events) x WHERE m.ctid = x.ctid;
UPDATE agent_autopilot SET changed_at = '2026-10-12T10:05:00Z';
COMMIT;
