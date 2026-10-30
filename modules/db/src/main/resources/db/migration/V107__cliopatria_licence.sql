-- V107: Cliopatria's polity outlines are CC BY 4.0 (the repository's LICENSE.md), not CC BY-NC-SA as they were
-- labelled when imported; the place cards read the licence from each row's `source`.
UPDATE places SET source = replace(source, 'CC BY-NC-SA', 'CC BY 4.0')
  WHERE source LIKE 'Cliopatria%' AND source LIKE '%CC BY-NC-SA%';
