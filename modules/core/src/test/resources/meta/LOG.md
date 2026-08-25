# 📓 LOG — project worklog

**The one place for "what was done and when."** Reverse-chronological. One entry per meaningful work
session: what changed, why, key numbers. Replaces the per-file "Pass 1 / Pass 2 / updated:" records that
were scattered across `frontier_watch.md`, `reviews_backbone.md`, etc. Append at the top.

*(Git history covers code changes; this log covers the corpus/wiki — fetches, enrichment, compilation,
status changes — which live in Nextcloud, not git.)*

---

## 2026-06-25 — Bookkeeping consolidation + conventions
- Merged the scattered working files into three predictable places in `meta/`: **ACQUIRE.md** (user
  acquisition queue), **TASKS.md** (agent backlog + checkpoints), **LOG.md** (this). Deleted the old
  `books_to_acquire / sources_to_provide / queue*.md / frontier_watch / reviews_backbone` from `wiki/index/`.
- Convention recorded in `CLAUDE.md` + a **bookkeeping** skill: `wiki/` = compiled content only;
  `meta/` = working state; don't scatter new queue/tracker files.

## 2026-06-25 — Historiography thread + Calibre safety net
- New topic **`disputed_grand_theories`**: concept article tracking Frazer/Graves/Eliade/Müller/Murray/
  Gimbutas — their cultural afterlife (Wicca, the Goddess movement, the monomyth) and refutation status.
- Fetched the public-domain founders → Calibre: Frazer *Golden Bough*, Müller *Comparative Mythology*,
  Murray *Witch-Cult*, Harrison *Themis*; ingested + enriched. Seeded Cveček 2023 (matriarchy critique).
- Added the **Calibre-gap reminder hook** (`tools/hooks/calibre_gap_reminder.py` + `.claude/settings.json`,
  PostToolUse + Stop) and the **sync-calibre** skill so fetched sources never miss the canonical store.
- Pushed all 46 previously-un-Calibred OA articles into Calibre via `to_calibre.py`; `dedup_raw` reclaimed
  **185.9 MB** of raw/ copies (sha256-verified). Corpus: **209 sources / 964 findings**.

## 2026-06-25 — Compiled the wiki
- Built the synthesized article layer from the corpus: master `wiki/index.md` MOC + ~31 articles
  (16 concepts, 6 myths, 6 cultures, 5 regions, 1 deep-time timeline) and refreshed the 2 synthesis
  reports to the full corpus. 1,731 wikilinks, 0 dangling. Fixed the `heggarty2023` year glitch.
- Fixed the recurring DuckDB corruption permanently (GEOMETRY blob → `lat`/`lon` DOUBLE columns).

## 2026-06-24/25 — Frontier passes + reviews-first strategy
- **Pass 1:** swept 47 anchors → ~1,500 post-2020 citing papers; ingested 7 frontier papers (Kassian 2025
  refutes Heggarty; iecor; PLoS phylo-validity; contact/borrowing; millet-vs-rice; HG-admixture rebound;
  archres rethinking) and flagged refutes/challenges/updates relationships.
- **Pass 2 + reviews-first:** ingested next-tier aDNA syntheses, IE-linguistics method, collapse & religion
  reviews; built the checkpoint backbone (now in `meta/TASKS.md`) and acquired the Companion/Handbook layer.
- Closed the two decades-stale checkpoints (Aegean prehistory 1998→2010; Greek religion 1985→2015).

## 2026-06-22/24 — Corpus build (the original 4-topic program)
- Discovery → legal-OA fetch → token-free convert → enrich → catalog, across the four interlinked topics
  plus aegean-religion and homeric-historicity. Acquired 24 foundational books + Homeric-historicity books
  via Calibre; recovered the closed journal articles (sections C/D) via OA routing + manual drop-ins.
- Built the Calibre-is-canonical storage model: backfill metadata, add fetched PDFs, sha256 dedup.
