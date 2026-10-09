# Review convergence: why `review-article --fix` never ends below MAJOR (research, 2026-10-09)

On 2026-10-08 the article set was reviewed with `review-article <slug> --fix --rounds 2`. Every run that finished
ended on MAJOR, typically "round 2 → MAJOR (R:6 C:20 S:3), fixer applied 35". This note measures why, from the runs'
own checkpoints, and records the design built from it (§6).

**Method.**
- Data: the 27 `review-article` runs created on 2026-10-08 that reached `done` (17 earlier runs had failed, 6 were
  still queued or running when the data was read). All 27 ran `fix: true, rounds: 2`, so each has exactly one
  full panel, one fixer, one full panel.
- Read-only, from the live database with `histmersion query --json` (`jobs`, `agent_steps`): the `merge-1` and
  `merge-2` findings (exploded with `jsonb_array_elements`), the `snapshot-1` and `snapshot-2` bodies (the text
  each panel read; `snapshot-2` is exactly what `patch-1` wrote, `previousVersion` in `article_versions`), the
  `fixer-1` edits, and the token columns of every LLM attempt. `query` cuts a cell at 2,000 characters and a
  result at 500 rows, so bodies and edit texts were read in 1,800-character slices and every query was paged.
- No model was called. The cold-reader sample (§4) was judged by reading it.

**Matching rule** (round-1 finding ↔ round-2 finding). Two findings are the same when the **same reviewer** raised
both and either
- their quoted passages overlap (one contains the other, or difflib ratio ≥ 0.6, both ≥ 12 characters), or
- their `where` agrees (ratio ≥ 0.6) and their problems share ≥ 30 % of their content words, or
- their problems alone share ≥ 45 % of their content words.

A stricter variant (quote ratio ≥ 0.8; or `where` ≥ 0.7 and problem overlap ≥ 0.4) moves the totals by at most
four, so the conclusions below do not hang on the thresholds. "Resolved" = a round-1 MAJOR that no round-2
finding of the same reviewer matches. "New" = a round-2 MAJOR that matches no round-1 finding (of any severity).

**Fixer-inserted text.** A round-2 MAJOR points at text the fixer wrote when its normalised quote is not in the
round-1 body and either sits inside the text of an applied edit or shares fewer than half of its six-word shingles
with the round-1 body. A quote shorter than 12 characters (mostly the code-added "N terms are undefined" finding,
which has none) is undeterminable.

---

## 1. Headline

| | value |
|---|---|
| runs analysed | 27 articles, 2 rounds each |
| round-1 findings → round-2 findings | 977 → 840 |
| round-1 MAJORs → round-2 MAJORs | 306 → 211 |
| round-1 MAJORs **resolved** (not re-raised in round 2) | **261 of 306 (85 %)** |
| round-1 MAJORs re-raised | 45 (27 of them the identical code-added cold-reader count, §4) |
| round-2 MAJORs that are **new** | **171 of 211 (81 %)**; 164 when matched across reviewers |
| round-2 MAJORs on **text the fixer inserted** | **84 of 211 (40 %)**; 84 of the 176 that can be located (48 %) |
| articles ending below MAJOR | **0 of 27** |
| fixer: findings applied / rejected | 966 / 3, in 993 edits, all applied |
| article length | 124,847 → 146,260 words (+17 %) |

The fixer works: 85 % of the round-1 MAJORs are gone in round 2. The loop does not converge because round 2 is a
second, independent full review. It finds a fresh set of MAJORs (81 % of them new), and nearly half of the ones
that can be located are in sentences the fixer itself wrote. With `rounds 2` there is no round to fix those, and
with more rounds the same happens again: each full panel samples a new slice of an open-ended space.

## 2. Findings by reviewer, severity and round

| reviewer | round 1 major | round 1 minor | round 2 major | round 2 minor | round-1 majors re-raised | round-2 majors new |
|---|---|---|---|---|---|---|
| rules | 108 | 130 | 63 | 133 | 8 | 58 |
| cold reader | 99 | 438 | 99 | 402 | 34 | 65 |
| skeptic | 91 | 103 | 48 | 94 | 2 | 48 |
| mechanical | 8 | 0 | 1 | 0 | 1 | 0 |
| **total** | **306** | **671** | **211** | **629** | **45** | **171** |

MAJOR kinds of the two evidence-based reviewers:

| kind | rules r1 → r2 | | kind | skeptic r1 → r2 |
|---|---|---|---|---|
| coverage | 34 → 20 | | missing-critique | 28 → 25 |
| section | 21 → 9 | | contradictory | 22 → 5 |
| synth | 15 → **22** | | missing-source | 19 → 9 |
| recall | 15 → 7 | | extrapolatory | 9 → 1 |
| status | 11 → 1 | | settled-but-contested | 6 → 1 |
| weight | 7 → 1 | | disagreement, synth, lost-distinction, date | 7 → 7 |
| decorative | 5 → 3 | | decorative | 0 → 1 |

- **Factual errors fall fast.** Contradictory 22 → 5, extrapolatory 9 → 1, status 11 → 1, recall 15 → 7.
- **Synth rises (15 → 22).** It is the one kind that grows, and it is what a fixer produces when it answers "explain
  why the two papers differ" or "add the critique" by writing a sentence that links two sources. Example
  (ancient_dna_revolution, rules, round 2): the fixer inserted *"This differs from Allentoft 2024's 65 % Middle Don
  + 35 % Caucasus hunter-gatherer split because the two papers model Yamnaya against different reference
  populations…"*; the reviewer: *"a bridge claim… Lazaridis 2025 never mentions Allentoft 2024 except in its
  reference list"*.
- **Missing-critique stays (28 → 25), all new.** The corpus holds more critiques than a round can cite. A full
  panel always finds another one, so this kind alone keeps round 2 MAJOR.
- **Coverage on articles with no recorded filter** (11 MAJORs in round 1, 7 in round 2): "the article records no
  filter… more than 30 % unplaced". The fixer cannot record a filter, so this never closes inside a run.

## 3. Round-2 MAJORs on text the fixer wrote

84 round-2 MAJORs quote fixer-inserted text, 81 of them new. Typical:

| article | reviewer · kind | the inserted text | the problem |
|---|---|---|---|
| dionysus_origins | rules · synth | "On the Companion's reading, the tablets bear on when the cult began, not on why the Greeks told his story as an arrival" | no passage of the cited record separates the two |
| poseidon_origins | rules · coverage | a Not-used reason: "His Poseidon passages concern the god's later Athenian cult…" | Nilsson's record does discuss Poseidon's origin; the reason is wrong |
| bronze_age_collapse | skeptic · decorative | "Knapp & Manning (2016) re-modelled the core's age–depth curve … [[langgut2013_climate]]" | a 2016 result cited to the 2013 paper |
| demic_diffusion | rules · coverage | a reason: "edited volume whose chapter-level findings are not separately recorded" | the record has 8 chapter-level findings |
| poseidon_origins | rules · synth | "The two numbers probably name one Pylos tablet." | the identity is the fixer's inference |

Per article the share ranges from 0 (deep_time, athena_origins, mycenaean_religion) to 10 of 13 (levant_near_east).
The three short articles the fixer more than doubled (europe 1,340 → 3,070 words, levant_near_east 1,228 → 2,859,
pontic_caspian_steppe 1,240 → 2,970) have the most: 6, 10 and 7.

**Consequence.** A verification round must look hardest at the edited passages, and the fixer must hear what
went wrong last time ("a reason must be checked against the record", "no bridge sentence between two sources").

## 4. The cold reader

- 1,038 findings over both rounds; **198 MAJOR (19 %)**: 99 in each round.
- Kinds over all findings: term 253, opaque 204, number 138, certainty 135, padding 92, assertion 82, unanswered
  72, lost 57, empty-slot 4.
- Kinds of the MAJORs: term 125, lost 47, certainty 12, assertion 5, opaque 4, empty-slot 4, number 1.
- **54 of the 198 are added by code, not the model**: "N terms are undefined (more than 8)", from the answer's
  `termsUndefined` count. That finding was raised on **27 of 27 articles in round 1 and again on 27 of 27 in round
  2**. Taken alone it makes PASS unreachable for every article in the set.

**Sample.** 30 cold-reader MAJORs drawn at random (seed 7) and read as an editor would:

| judgement | n | what they are |
|---|---|---|
| a careful editor agrees it is MAJOR | 12 | lost in a stack of period labels or tablet codes (minoan_religion, poseidon_origins, bronze_age_collapse, corded_ware_bell_beaker); main message not in the first paragraph (poseidon_origins, trojan_war); a summary sentence stated as settled that the body itself qualifies (storm_god); "Kn02" called a Pylos tablet — Kn is the Knossos prefix (hera_origins); "the corpus's two great demic turnovers" (europe) |
| plausible | 1 | empty "answer" slot (homeric_historicity) |
| real, but minor | 5 | one undefined label: "Mycenaean", "Minoan", "ancient DNA", "Semitic", "HG" |
| disagrees | 12 | 9 are the code-added count, whose term lists include Ventris, Chadwick, Homer, Hesiod, Demeter, Pylos, Thebes, Nestor; one says FST is undefined in the sentence that defines it; two are empty "question"/"answer" slots on region articles, which survey a place and pose no single question |

So about 40 % of the cold reader's MAJORs are MAJOR to an editor, a sixth are real but minor, and 40 % should not
block. The count finding is the single largest bad class: the reader counts proper names of people, places, gods
and texts as undefined terms.

## 5. Tokens

Input tokens per article (cache reads included; Claude Code plan models, so 0 USD recorded):

| step | round 1 | round 2 | of which cache reads |
|---|---|---|---|
| rules reviewer (Sonnet) | 182k | 178k | 82 % |
| cold reader (Haiku) | 18k | 19k | 14 % |
| skeptic (Opus) | 409k | 419k | 90 % |
| fixer (Opus) | 1,128k | — | 92 % |
| **per article** | | | **2.35M** |

The fixer is half the bill; round 2's full panel is a quarter of it (616k) and is the part that produced the
non-converging findings.

---

## 6. Design (as built)

The data changes two things against the brief's outline, each marked **adapted**.

### 6.1 The findings ledger

One per article, in `review_ledgers` (migration V073, `slug` → `articles` with `ON DELETE/UPDATE CASCADE`, so a
rename carries it and a delete drops it). The value is the pure `ReviewLedger.Ledger` as JSON:

- **entries**: every finding ever raised on the article, under a ledger-wide id (`R7`, `C12`, `S3`, `M2` — the
  reviewer's letter and a number never reused), with reviewer, severity, kind, `where`, quote, problem, fix,
  evidence, the run and round that raised it, and a **status**:
  - `open` — `claimed` once the fixer lists it as applied (awaiting its re-check);
  - `fixed` — closed by the reviewer that raised it (or by code, for a mechanical finding that no longer
    recurs), with the round, the run and the reviewer's quote of the fixed text;
  - `rejected` — the fixer's rejection, with the citekey and the record's sentence code found;
  - `wont-fix` — a MINOR the fixer declined with a reason (a MAJOR can only be applied or rejected).
  A fixed finding raised again is re-opened (`reopened` counts it); a rejected one raised again is re-opened and
  flagged escalated, as before.
- **lessons**: at most 15 short rules, newest kept: from each verified rejection (`[[ck]]: <the fixer's reason>`),
  and from the re-checks (a reviewer's `lessons`: what a claimed fix got wrong, or a regression's rule).
- **task**: the one backlog item of the article (§6.4).

Loaded by the run's first step (`ledger`, checkpointed: a resumed run sees the same ledger), written after every
pass, after every fix and at the end, through the run's write gate (a dry run never persists it, but carries it in memory).

**In the prompts** (`ReviewLedger.promptText`, data inside `<review-ledger>`): open findings in full (with a
claimed fix or a "not fixed" note), closed findings as one line each (the newest 60), and the lessons. The full
panel gets the whole ledger; a re-check gets the findings it verifies in full and its reviewer's other findings as
one-liners; the fixer gets every open finding in full, then the closed one-liners and the lessons.

### 6.2 Round accounting

`--rounds N` (with `fix`: at least 2; default `agents.review.max-rounds`; at most `rounds-cap`) is the number of
**review passes before the confirmation**: pass 1 is a full panel, passes 2…N are targeted re-checks, and the fixer
works between consecutive passes (at most N − 1 fixer calls). Steps, per run:

```
ledger                                               load the article's ledger
snapshot-1 mechanical-1                              pass 1: a full panel
  rules-1 cold-reader-1 skeptic-1                      (in parallel, each with the ledger)
  ledger-1 merge-1 proposals-1
fixer-1 patch-1                                      while a MAJOR is open and passes remain
snapshot-2 mechanical-2                              pass 2: targeted
  [rules-check-2] [cold-reader-check-2] [skeptic-check-2]   (only the owners of claimed findings)
  ledger-2 merge-2
fixer-2 patch-2 … snapshot-N … merge-N               …
snapshot-F mechanical-F rules-F cold-reader-F        the confirmation, F = N + 1
  skeptic-F ledger-F merge-F proposals-F
record                                               backlog item, ledger, log line
```

- A **targeted pass** runs only the re-checks it needs: one call per reviewer that owns a claimed finding (a
  reviewer with nothing to check is not called). Mechanical findings are re-checked by code: one that no longer
  recurs is fixed, one that recurs stays open, a new one (an uncited paragraph the fixer wrote) is opened.
  `mechanical-r` also runs `check_edits`'s formatting check on the stored body; errors the edit introduced are
  minor `M` findings.
- **Confirmation (adapted).** A full panel at the end, with the ledger, runs when the last pass was a targeted
  one and it left **no open MAJOR** (`agents.review.final-panel = clear`, the default; `always` runs it regardless; `never`
  skips it). The data says why: a full panel on an article that still has open MAJORs re-finds those and adds new
  ones (81 % of round-2 MAJORs were new), which changes nothing about the outcome and costs ~600k tokens. Its new
  findings are recorded as open; the fixer does not run after it.
- **A concurrent writer.** When `patch-r` meets a 412 the fix is dropped, nothing is claimed, and the next pass is
  a full panel on the other writer's text (with no confirmation after it: there is no fix to confirm).
- **A full panel checks the ledger too.** Each reviewer of a full panel (pass 1 of a later run, or the
  confirmation) answers a `check` for every open finding of its own; an answer without them is sent back, like
  any other failed validation.
- The verdict of a pass (its `merge-r`) is that of the **open ledger findings**, so `agent compare` and the log
  line show where the article stands, not what one pass happened to see.

### 6.3 The verification round

A re-check (`rules-check-r`, `cold-reader-check-r`, `skeptic-check-r`; the reviewer's own agent definition, a
`RecheckAnswer`) is given the article, its earlier findings that the fixer claims fixed (in full, with what the
fixer did), the **edited passages** (`ReviewLedger.editedPassages`: a paragraph-level diff against the previous
pass's body, each changed paragraph with one paragraph of margin, removed paragraphs shown as removed), and the
ledger's one-liners and lessons. It answers:

- `checks` — for each finding listed: `fixed` true/false and a **quote** of the current text that shows it. Code
  requires a check per listed finding (a missing one leaves it open), finds the quote in the body (fuzzy, the
  evidence matcher), and accepts an empty quote only when there is nothing to quote (the finding quoted nothing, or its passage
  is gone);
- `findings` — regressions in the edited passages. **A new MAJOR outside them is kept only for a factual error
  with evidence** (kinds `contradictory`, `extrapolatory`, `decorative`, `recall`, `date`, `status`,
  `settled-but-contested`, `number`, with at least one record quote code verified). Anything else outside them is
  recorded as MINOR with a run warning, so it is not lost and does not block;
- `lessons` — short rules for the fixer.

The turns of a re-check: `agents.review.recheck-turns` plus one per finding to check, never more than the agent's
`max-turns`.

**Adapted: the cold reader's term count.** The reader's prompt now says that proper names of people, places,
gods and texts that a sentence situates are not undefined terms (the sample's largest bad class). The code-added
count finding stays MAJOR above eight terms, and it is a ledger finding like the others: the next pass's reader
re-checks it.

### 6.4 The backlog (#934)

One `tasks` item per article (topic `agent review`), its id kept in the ledger:
`review-article [[slug]]: 3 open MAJORs — R4 (Disagreements): …; S2 (…): …; C7 (…): … (agent run 1a2b3c4d)`.
A later run rewrites the same item's text while MAJORs remain, and marks it done ("no MAJOR open after agent run
…") when none does. A rejected finding raised again says so in its entry. The per-finding items earlier runs left
for the article (`review-article [[slug]]: MAJOR open — …`) are closed as superseded by the article's item.

### 6.5 Dry runs, `agent compare`, deploying

- The pipeline's version is now 4: a `review-article` run started on version 3 and paused cannot resume
  (the engine refuses a changed pipeline); start it again. Let running reviews finish before deploying.
- A dry run (#992) loads the ledger, carries it through every pass in memory, and writes nothing: the ledger
  write, the patch, the backlog item and the log line are recorded on their steps as "would write".
- `agent compare` reads `rules-1`, `cold-reader-1`, `skeptic-1`, `merge-1` and the last `merge-<n>`, which keep
  their names and types (`merge-r` is now the ledger's open findings after pass r); the confirmation's steps are
  numbered like a pass (`merge-F`), so the last verdict is the run's verdict. The re-check steps
  (`rules-check-r`, …) appear in the step table like any other.

## 7. Expected saving per article

From §5, the old flow: full panel 609k + fixer 1,128k + full panel 616k = **2.35M**.

New flow with `--rounds 2`:

| step | estimate | reasoning |
|---|---|---|
| full panel (pass 1) | 609k | unchanged; the ledger adds a few hundred lines at most |
| fixer-1 | 1,128k | unchanged work; the lessons are a few lines |
| targeted re-check (pass 2) | ~150k | rules and skeptic each check their ~5–10 claimed MAJORs and ~10 minors in the edited passages, with at most `recheck-turns` + one per finding (≤ the agent's max-turns) instead of 20 open-ended lookups; the skeptic's round-2 cost was 90 % cache reads of its own growing tool loop, which is what the cap bounds. Cold reader unchanged (~18k) |
| confirmation | 0 or ~610k | only when pass 2 closed every MAJOR |

- An article that still has an open MAJOR after pass 2 (all 27 of 2026-10-08 would have, in the old flow): **~1.89M,
  about 20 % (≈ 470k) less**, and its backlog item names exactly what is open.
- An article that clears: ~2.5M, about 6 % more than the old flow — and it ends PASS/MINOR, which the old flow
  never reached.
- With `--rounds 3`, each extra fixer pass sees only the not-fixed and regression findings (a handful), so its
  turns, and therefore its cache-read-dominated cost, are a fraction of the first fixer's (estimate 200–400k), and
  each extra re-check ~150k, against ~1.7M for a fixer plus a full panel in the old flow.

The bigger saving is across runs: a later run on the same article starts from its ledger, so the full panel
verifies known findings and does not re-discover them, and rejected findings are not raised again for the fixer to
reject again.
