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
count finding is **MINOR** at any count (2026-10-09: the count still fired on every article, and as a MAJOR it made
PASS impossible): "N terms undefined (count only; the individual findings carry the severity)". The reader's
individually named terms keep the severity it gives them. In the ledger the count is one finding whatever its number
(`TermCount.is`): a later pass's count replaces the open entry under its id — so a MAJOR count an earlier run stored
becomes the minor one on the next full panel — and a full panel that does not raise it (8 terms or fewer) closes it.

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

### 6.6 Verified spot-checks (#1840)

The rules reviewer's database checks (5–6: decorative citations, ALCE recall) were trusted: it reported
`accuracyChecked`, a number. From 2026-10-10 the sonnet reviewer stopped making them whenever the brief carried a
review ledger — full rules passes over 2026-10-09…16, counting tool calls over all attempts of a step:

| claude-code:sonnet rules pass | steps | with no tool call | tool calls / step |
|---|---|---|---|
| pass 1, no ledger | 52 | 0 | 9.8 |
| pass 1, ledger | 29 | 24 | 0.4 |
| later full pass, ledger | 21 | 17 | 0.4 |

Opus (skeptic) fell from 19.4 to 5.0 calls with a ledger but never to none; Codex (gpt-6.1-sol rules, gpt-6-astra
skeptic) was unaffected. The ledger's instruction ("give a check for each OPEN finding of yours") read as the
whole task. Two changes:

- **The answer reports its checks, and code holds them to the step's tool calls.** `RulesAnswer.spotChecks`
  replaces `accuracyChecked`: `{citekey, claim, verdict, recordQuote}` per checked claim. On a full pass code
  accepts one only when the claim is in the article, the citekey is cited, a `get_source`/`passages` call of the
  step names it (the calls as the runtime reported them — sttp-ai's loop, Claude Code's stream-json, Codex's
  `mcp_tool_call` items, opencode's `tool_use` parts — never as the model describes them; a retry's are the
  earlier attempts' too, since it amends without looking up again) and the record quote is in that record.
  `agents.review.spot-checks` (3) of them on different sources are required, fewer when fewer are cited; an
  answer with too few is sent back, and on the last attempt kept with a run warning. The verified ones are kept
  on the step's `Report`. Verification passes are not held to it, and the cold reader has no tools.
- **The ledger says it adds to the review.** Its preamble for the rules reviewer and the skeptic now says the
  full review, database look-ups included, still runs; the agent body says the same.

What looked like a Codex regression after the lean base prompt (2026-10-16, rules steps "averaging ~20k input")
was retries: an attempt rejected for its verdict or a check is retried with its own answer and told not to
repeat its lookups, so the retry makes no call. First attempts after 18:38 still made 23.6 calls (rules) and
42.7 (skeptic) each.

### 6.7 A coverage MAJOR the fixer cannot answer

After 2026-10-16 18:38 the Codex fixer (gpt-6-astra) failed 4 of 23 first attempts with "major finding R8/R16 is
neither applied nor rejected" (1 of 18 that afternoon, R23). Every one was the rules reviewer's `coverage` MAJOR on
an article that records no filter ("the reconstructed search returns 94 sources; most are neither cited nor
excluded"). No tool result was cut (the answers listed every other id, with 6–25 lookups); the finding had no
answer: its fix is recording the filter, which no edit and no fixer tool does; a rejection needs a record quote
that contradicts it, and only a minor could be declined. The fixer left it out, and on the retry listed it as
"applied" with a note saying it was not — a false claim the re-check then had to catch.

Such a finding may now go in `wontFix`: declined, it stays OPEN in the ledger (only a minor is closed as won't
fix), so it reaches the article's backlog item for a person. Left out, it is sent back saying so. With a recorded
filter the rule is unchanged (the reasons answer it).

### 6.8 The critique checklist: missing-critique MAJORs that converge (2026-10-17)

**Evidence** (the live ledgers, 2026-10-17, read-only):

- 347 skeptic `missing-critique` MAJORs: 315 fixed, 27 open, 0 rejected. They are real and fixable — but every
  new full panel finds another, so the review manager stopped articles on "omission churn" (§4(a) of
  docs/research-process-managers.md). 213 were raised in pass 1, 124 in the final confirmation panel.
- 88 % (306 of 347) involve a source joined to an already-cited source by a typed disagreement edge (`edges`:
  challenges, refutes, updates, superseded_by). Per article a median of 28 such pairs (max 86), a median of 15
  with the other side uncited.

So the space the skeptic samples is mostly closed and countable: the edges. The design makes it a list code keeps,
the skeptic rules on, and the manager counts down.

**The checklist** (`core.Critiques`, pure). For an article: every challenges/refutes/updates/superseded_by edge
with at least one end cited by the body (`[[citekey]]` outside a hand-written Not used section, aliases and short
forms resolved as the page resolves them). The edges between the same two sources in one direction are one pair
(`src>dst`, the critic first; its kinds listed). Each pair is

- `addressed` — both citekeys are cited in the same paragraph (the user's test; a list item and a table row are
  paragraphs of their own, headings and code are not);
- `reasoned` — a stored one-line reason says the critique does not bear on what the article cites the source
  for;
- `open` — neither.

`Handlers.critiques(slug, body)` builds it, with or without a filter; `openCritiques(slug)` counts the open ones.

**The reasons** (V091 `article_critique_reasons(slug, src, dst, reason, set_by, set_at)`, PK `(slug, src, dst)`):
one per article and pair, whatever the edge kinds. `slug` references `articles` and `src`/`dst` reference
`sources`, all `ON DELETE CASCADE ON UPDATE CASCADE`: an article rename or delete, a citekey rename or a source
delete carries or drops them. They are written through `patch_article`'s one path — `critiqueReasons
[{src, dst, reason}]` in the REST/MCP patch, `histmersion article patch <slug> --critique-reason <src>><dst>=<why>`
— checked first (both citekeys resolve, an old one too; a disagreement edge joins them, either way round, and the
reason is kept under the edge's direction; at most 500 characters; an empty reason removes), so a refused reason
writes nothing. They sit beside the article rather than in its row: a patch of reasons alone writes no version and
leaves `updatedAt` as it is — a review pass stores the skeptic's reasons while the fixer holds that token. A
migration cost of one empty table and two indexes. Bundles and the Markdown export do not carry them (like the
review ledger); `histmersion backup` does.

**Where it shows.** `article coverage` (REST, MCP `article_coverage`, `get_article`'s coverage part) gains a
*Disagreements checked* section: the counts, every open pair, every reasoned one with its reason and setter.
The generated `_Method_` note gains `disagreements checked: 28 pairs — 19 addressed, 7 reasoned, 2 open` when the
article records a filter (as the rest of the note) and has a pair. The `review-article` log line ends with the
same counts.

**The skeptic rules on the open pairs.** A full pass's skeptic gets `<critique-checklist>`: the counts, the open
pairs in full — each `src>dst` with both citekeys, the edge kinds, titles, years and whether each side is cited —
ranked (an uncited critic of a cited source first, then two cited sources never discussed together, then a cited
critic of an uncited source; refutes and superseded_by before challenges before updates; the more paragraphs
cite the source, the earlier), at most `agents.review.critique-pairs` (25) of them, the rest by name only, for a
later pass; then the reasoned pairs with their reasons (at most `critique-reasoned`, 40). It must rule on each
shown open pair: a `missing-critique` MAJOR naming it (`pair: "src>dst"`, a new optional field of every finding
answer) when the critique bears on a claim the article makes, or a `notMaterial` entry `{pair, reason}`. An
answer that leaves a shown pair unruled is sent back (a warning on the last attempt). The rules reviewer gets
the reasoned pairs (`<critique-reasons>`), so a wrong reason is a normal finding of its own.

**A not-material ruling is stored by code, not by the fixer.** The ruling is the skeptic's — the reviewer that
would otherwise raise the finding — and storing it at once closes the pair in the same pass, with no ledger entry
to open and close. It is written at `ledger-r` through the run's gate (`set_by: skeptic (agent run …)`; a dry
run carries it in memory and its later snapshots count it). The reason stays visible: in the coverage, in the
next skeptic's checklist, and in the rules reviewer's brief, where it can be challenged.

**A missing-critique MAJOR must name an open pair** (`ReviewArticle.checkCritiques`, per pass):

| the finding names | pass 1 (the fixer still works) | re-check | confirmation |
|---|---|---|---|
| an open pair | MAJOR (in the checklist's direction) | MAJOR, then scoped to the edited passages as before | MAJOR (open for the next run) |
| an addressed or reasoned pair | MINOR, with the note why | MINOR | MINOR |
| no checklist pair, with a verified edge proposal for it in the same answer | MAJOR (the edge goes to Approvals; the fixer answers in text) | — (a re-check proposes nothing) | MINOR + run warning, carried to the next run |
| no checklist pair, no proposal | sent back (MINOR + warning on the last attempt) | MINOR + warning | sent back (MINOR on the last attempt) |

The "edge now exists" of the design is the approval: with `agents.approvals.edges = manual` (the default) a
proposed edge waits for a person, and its pair joins the checklist once approved — the next run's skeptic sees
it. With `auto` it is applied at once. A minor missing-critique finding is not held to a pair.

**The fixer may set a critique aside.** For a missing-critique finding it may answer `setAside: [{id, pair?,
reason}]` instead of an edit: the pair is the finding's (or given, for a finding raised before the checklist),
must be a pair of the checklist (a recorded edge), and the reason one sentence. Code stores the reason with the
patch (`set_by: fixer (agent run …)`) and closes the finding with a new ledger status, **`set-aside`** — not
`rejected`, which says the finding was wrong and carries a record's sentence that contradicts it; here the
critique exists and does not bear. The closure keeps the pair and the reason, and every later prompt's one-liner
reads `S4 … → set aside as not material (smith1987_dying>frazer1922_golden) — <reason>`. Raised again as a MINOR
(the pair is reasoned now) it stays set aside; raised again MAJOR it re-opens with "set aside before: <reason>".

**The manager.** `RunMark` gains `pairsBefore`/`pairsAfter` (the open pairs when the run was dispatched and when
it was evaluated, counted live; `ItemState.pairsBefore` holds the first), and `ReviewFacts` says whether every
open MAJOR is a missing-critique (`onlyMissingCritiques`, any pass, any run). Omission churn is now: every open
MAJOR a missing-critique **and** the open pairs did not go down over `no-progress-runs` runs (from the window's
first `pairsBefore` to its last `pairsAfter`). While the pairs go down neither churn nor the open-MAJOR rule stops
the item — one missing-critique MAJOR per open pair keeps that count flat by design — and `max-runs-per-article`
still does; with a rules or reader MAJOR open the open-MAJOR rule applies as before. Counts a run evaluated
before the checklist never recorded are no evidence of a stall. Converged is unchanged: no open MAJOR.

**Prompt size** (`PromptBudget`, fixed overhead a turn, against e4abc796): skeptic 24,920 → 26,513 characters
(+1,593, ~590 tokens: the fragment's rulings, `notMaterial` and the finding's `pair` in its answer schema), fixer
27,655 → 28,910 (+1,255, ~460: `setAside`), rules reviewer 25,815 → 26,169 (+354, ~130: the `pair` field and the
guide's same-paragraph line); cold reader unchanged. The checklist block itself is data in the skeptic's user
message — about 200 characters per open pair shown, so at most ~5,000 characters (25 pairs) plus one line per
reasoned pair; the rules reviewer's `<critique-reasons>` one line per reasoned pair.

**Deploying.** The review pipeline's version is now 5: a `review-article` run started on version 4 and paused
cannot resume (the engine refuses a changed pipeline; start it again). Let running reviews finish before
deploying. V091 applies on start (one empty table).

### 6.9 Convergence: a closed checklist up front, a final pass that verifies, guards in code (2026-10-19)

**Evidence** (the live database, 2026-10-19, read-only): 85 articles; 76 last reviewed MINOR, 8 MAJOR with 32 open
MAJOR ledger entries (gods_and_migrations 10, achilles_origins 7, mycenaean 6, etruscan_religion 3,
levant_near_east 2, aegean 2, persephone_hades_origins 1, creation_cosmogony 1). By kind: skeptic missing-critique
22, cold-reader `lost` 4, rules coverage 2, other 4. 24 of the 32 were raised first by the confirming panel (pass 3
of a `--rounds 2` run); 12 carry `notFixed` notes; 4 were "fixed in pass 2, raised again in pass 3".

**Root causes, from the runs' own steps** (gods_and_migrations, run 959312a8, read from `agent_steps`):

| failure | what the steps show | cause |
|---|---|---|
| late new critiques | snapshot-1: 53 open pairs; skeptic-1 was shown 25 and ruled on them (17 MAJOR, 8 not material). skeptic-3 (the confirmation) ruled on the rest: 10 more MAJORs. Two of them (`bjrn2024>demoule2023`, `olander2022>demoule2023`) were not on the checklist at pass 1: the fixer had cited `demoule2023` to answer five other pairs | the cap on the pairs shown (`critique-pairs` 25), half of them on works the article does not cite; the confirmation owed rulings like pass 1 (§6.8: "MAJOR, open for the next run"); a citation the fixer adds brings its own critiques, and nothing checked them |
| "fixed in pass 2, raised again in pass 3" | `chang2015>gray2003_language`, `contact2022_langtrees>bouckaert2012`, `phylo2026_validity>gray2003_language` are **open in snapshot-1, -2 and -3**; skeptic-check-2 called each "fixed" on a quote describing the critique | corrected: not a later fix undoing an earlier one, but a re-check accepting a description of the critique while code's own checklist showed the pair open |
| not fixed, again and again | achilles_origins S4/S5/S6: "The paragraph still cites only [[kelly2021_gods]]; [[bachvarova2016_hittite]] is not cited in it" — the paragraph names Bachvarova; one `[[citekey]]` is missing | a claim of "applied" was never checked against the checklist; the one mechanical repair was left to a model |
| echoes | achilles C12 (cold reader): "omits Ballesteros (2024) … the open ledger item S3 remains unaddressed"; R8 (rules) the same pair as S3; mycenaean S21 "the reciprocal checklist pair" of S20 | the cold reader read every reviewer's ledger entries; nothing kept one finding per pair |
| severity | most missing-critique MAJORs quote a claim the article already hedges or labels ("Chang et al. (2015, contested) …", "Kaniewski … (contested — challenged by Middleton, 2020)") | MAJOR whenever a critique was missing, whatever the article said |

**Design (as built).**

1. **The checklist up front, bearing pairs only** (`core.Critiques`). A pair's *criticised* side is the target of
   challenges/refutes/updates and the SOURCE of superseded_by (stored on the older work); a pair *bears* when it is
   open and the article cites the criticised work. The skeptic's brief shows every bearing pair (`toRule`: once
   per two sources, reciprocal pairs collapsed; `agents.review.critique-pairs` now 40) and names the rest as
   background, owing no ruling. `Handlers.openCritiques` (the review manager's pair count) counts bearing pairs.
   The fixer gets the work as `<critique-work>`: for each missing-critique finding the paragraph its critique goes
   in (the one its quote is in, when that cites the criticised work), flagged when it was found not fixed twice
   or more. The compile writer's evidence model says the same rule (both sources of an edge in one paragraph).
2. **The final pass verifies.** The confirming panel's user message says so; it owes no rulings; and code
   (`ReviewLedger.deferFinal`) marks every MAJOR it raises *new* (an entry the ledger did not have, or an open
   minor raised to MAJOR) `deferred = <run>` unless `ReviewArticle.finalRegression` says the run caused it: a
   pair answered in the run's first snapshot and open now, or a factual error (`FactualKinds`, or `synth`) quoted
   from text the run changed. A reopened, re-raised or escalated finding is not new. Deferred entries stay open
   and MAJOR in the ledger and the backlog item ("[new in the final pass: deferred to the next run]"); the run's
   `merge-F` lists them in `deferred` and its verdict counts them as minor; the log line says how many. The next
   run's pass 1 hands them to the fixer like any open MAJOR.
3. **Co-citation in code** (`core.CritiquePairing`, lint `article-critique-pairing`). A paragraph that cites one
   side of a disagreement edge and names the other side's author (surnames from the authors, matched to the
   citekey's leading name: "Drews Robert" → Drews, Bjørn → `bjrn2024_…`) without citing it is a hit. It is
   MECHANICAL when the name points at one work the article already cites elsewhere: that work's year beside a
   mention or no other year, no other candidate or cited work sharing the name. `lint --rule
   article-critique-pairing --fix` appends the missing citekey to the paragraph's last citation (one patch per
   article, guarded by its token); the review pipeline applies the same repair to every fixer revision, and the
   findings whose pair it answers are `repaired` — claimed for their reviewer's re-check. A non-mechanical hit is
   a line of `<critique-work>`. Code never brings in a source the article cites nowhere (a new source brings its
   own critiques, and nobody read its record).
4. **The regression guard** (`ReviewLedger.guard`). Before a revision is written: a finding the ledger closed as
   fixed must stay fixed — its pair still answered, its problem text not written back, the citations its fix
   rests on still cited. An answer that reopens one is sent back ("your edits reopen a closed finding — S1
   (closed): …"); on the last attempt the edits that reopen it are dropped, one at a time from the last. A
   re-check (or a full panel's check) that calls a missing critique fixed while its pair still bears is recorded
   NOT fixed (`holdPairChecks`), with that as the note.
5. **Severity calibration** (`ReviewArticle.checkCritiques`, rules reviewer and skeptic alike). A missing-critique
   MAJOR stands only when its pair is an open pair of the checklist (the edge is recorded), the article cites
   the criticised work, and the finding's quote is the article's sentence stating the claim, with no hedge or
   contest word in the claim's own clause and no status label on the criticised work (`qualifiedClaim`: "Gray
   and Atkinson (2003) dated … [[gray2003_language]], and Bouckaert et al. (2012, contested)" — the label is
   Bouckaert's, so Gray and Atkinson's claim is unqualified). Anything else is recorded minor, with why: no
   recorded edge (a proposed one waits for approval and joins the next run's checklist — no longer MAJOR in pass
   1), a work the article does not cite, a qualified claim, a quote not in the body (sent back on a full panel's
   skeptic). One missing-critique finding per pair and pass (`onePerPair`): the skeptic's is kept, a reciprocal
   or another reviewer's echo dropped with a warning; the cold reader reads only its own ledger findings.
6. **The fixer cannot skip silently.** A missing critique listed as applied whose pair is still unanswered after
   the revision (and the repair) is sent back; on the last attempt it is not claimed. A citation the revision
   brings in whose critique no paragraph discusses is sent back (cite the critic, or `setAside` the pair with no
   `id`). Every open finding the fixer neither applied, rejected, declined nor set aside goes into `Fix.notFixed`
   with why; the ledger records it (`notFixed`, `notFixedCount`), and an entry found not fixed twice is flagged in
   the next fixer's `<critique-work>`.

**The effect on the eight MAJOR articles**, replaying the deterministic parts on their current bodies and ledgers
(a temporary suite over a read-only export; no model): of the 32 open MAJORs, 13 are new in a final pass and
would be deferred; 11 are recorded minor by the calibration (9 on a claim the article already qualifies, 1 on a
work it does not cite, 1 reciprocal of a deferred one); 3 (achilles S4, S5, S6) are answered by the pairing
repair; 5 remain: achilles R8 and C12 (the Ballesteros critique the article never names: R8 is dropped as the
skeptic's echo by the next pass, the fixer is told where it goes), gods_and_migrations S24 and S32 (the open
`chang2015>gray2003_language` and `phylo2026_validity>gray2003_language`, Gray and Atkinson stated unqualified),
persephone_hades_origins C25 (a reader lost, reopened). Five of the eight (mycenaean, etruscan_religion,
levant_near_east, aegean, creation_cosmogony) would have ended below MAJOR, each with its deferred findings in its
backlog item. The repair would add 20 citations across the eight bodies (11 in achilles_origins, most in its
etymology table).

**The review manager.** Unchanged code. Deferred entries are open MAJORs in the ledger, so `ReviewFacts.openMajors`
counts them and the manager runs the article again (the next run fixes them); `openCritiques` now counts bearing
pairs, so omission churn compares the pairs a run owes something.

**Prompt size** (`PromptBudget`, fixed overhead a turn): skeptic 26,513 → 27,951 (+1,438: the severity rule, the
final pass), fixer 28,910 → 30,120 (+1,210: code's co-citation check and repair, brought citations, closed
findings), rules reviewer 26,169 → 26,620 (+451), cold reader 7,587 (its ledger is its own); each includes the
guide's new pairing line (+101). `<critique-work>` is data, one line per finding.

**Deploying.** The review pipeline's version is now 6 (`Merged.deferred`, `Fix.repaired/repairs/notFixed/
pairsAside`, `Entry.notFixedCount/deferred` — all defaulted, so stored ledgers decode): a run started on version
5 and paused cannot resume; start it again. No migration.

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
