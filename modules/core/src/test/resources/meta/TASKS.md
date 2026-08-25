# 🛠️ TASKS — the agent's single backlog & maintenance state

**The one place the agent looks for "what's left to do."** Replaces the scattered
`queue.md` · `queue_aegean_religion.md` · `queue_homeric_historicity.md` · `frontier_watch.md` ·
`reviews_backbone.md`. Keep it current: tick/remove done items, append new ones here — do **not**
spawn new `queue_*.md` files. Anything *the user* must buy/borrow goes in `meta/ACQUIRE.md`; anything
worth a dated record goes in `meta/LOG.md`.

---

## ▶️ Active / next
- *(nothing in progress)*

## 📋 Backlog
**Free-OA leads the agent can fetch itself (no user needed):**
- [ ] Josephson-Storm, *The Myth of Disenchantment* (2017) — find an OA copy; magic→religion→science as itself a myth (for `disputed_grand_theories`).
- [ ] "Methodological Flaws in Recent Studies of Historical and Modern Witchcraft" (1998) — find OA.

**Enrichment / compile follow-ups:**
- [ ] When the historiography-thread books land (see `meta/ACQUIRE.md`), enrich + weave into `wiki/concepts/disputed_grand_theories.md`; consider giving Graves/Gimbutas a `status: contested` *for their theses* while keeping `foundational` as reference artifacts.

**Original ingestion queues — DRAINED (2026-06-25).** The three old per-topic discovery queues
(4-program general, aegean-religion ×34, homeric-historicity ×19) are fully ingested + enriched.
No open items remain; re-run discovery only when seeding a new topic.

## 🔄 Maintenance routine (periodic)
1. **Frontier sweep** — `followups.py <doi> --since <year>` over `foundational`/`contested` notes to catch new refutations/updates; tag relationships `refutes_X`/`challenges_X`/`updates_X`; ingest the high-value OA ones.
2. **Watch for new checkpoints** — new Companions/Handbooks or `type:review` syntheses per subtopic (table below) → add to `meta/ACQUIRE.md` if book-gated, else ingest.
3. After any change, `sync_db.py`; keep wiki links resolving (see **lint-wiki** skill).

## 📍 Currency checkpoints (reference — scan forward only from each)
*The latest authoritative synthesis we hold per subtopic; "since" = the year past which to look for updates.*

| subtopic | checkpoint held | since |
|---|---|---|
| IE homeland (ling.+arch.) | Anthony & Ringe 2015 (`anthony2015_ringe`) | 2015 |
| IE phylogenetics | frontier set (`kassian2025_critique`, `phylo2026_validity`) | 2026 |
| ancient DNA / migration | `adna2018_firstdecade` · `allentoft2024_postglacial` | 2024 |
| farming/language dispersal | `farming2020_archaeoling` · `lab2020_millet_rice` | 2020 |
| Bronze Age collapse | Knapp & Manning 2016 (`knapp2016`) | 2016 *(Kaniewski 2015 review still in ACQUIRE)* |
| LBA Mediterranean (arch.) | `middlesea2021_review` | 2021 |
| Aegean prehistory / Crete | `cline2010_aegeanhandbook` · `shelmerdine2008_aegeancompanion` | 2010 |
| Mycenaean / Linear B | `duhoux_linearb1`–`3` | 2014 |
| Anatolia / Hittite | `steadman2011_anatolia` | 2011 |
| Near-East storm/weather gods | `schwemer2008_storm` | 2008 |
| Near-East genetics | `lazaridis2020_anatolia` · `agranattamir2020_levant` | 2020 |
| Greek religion (origins) | `eidinow2015_greekreligion` · `ogden2007_greekreligion` | 2015 |
| Greek↔Anatolian contact | `bianconi2021_thereandback` | 2021 |
| IE comparative mythology | `dowden2011_greekmythology` · West *IE Poetry & Myth* | 2011 |
| Homeric historicity / oral | `pache2020_homerguide` | 2020 |
| IE historical linguistics | `olander2022_ielanguage` · `klein2017_ieling1`–`3` | 2022 |
| moralizing religion / Axial | `biggods2022_review` | 2022 |
| **historiography of myth-theory** | founders held (Frazer/Müller/Murray/Harrison) + Cveček 2023; critiques in ACQUIRE | — |

**Genre note:** sciences publish formal `type:review` (harvest via OpenAlex `type:review`); humanities
checkpoints are **Companions/Handbooks/series** → book-gated, land in `meta/ACQUIRE.md`.
