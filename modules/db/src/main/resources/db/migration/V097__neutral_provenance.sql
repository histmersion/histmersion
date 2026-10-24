-- V097 retired a provenance value in favour of `external-file`. Every database it ran on was rewritten
-- then, and a database created later never holds the value, so it now does nothing.
SELECT 1;
