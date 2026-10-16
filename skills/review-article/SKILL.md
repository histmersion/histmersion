---
name: review-article
description: Adversarial review of one or more compiled Histmersion articles by a three-agent panel — a rules reviewer (writing-guide compliance + fact spot-checks), a cold general reader (comprehension), and a specialist skeptic (depth, contradictions, missing sources) — optionally followed by a fixer that patches the article and re-runs the panel until no MAJOR finding remains. Use after any article is created, rewritten or extended, or to audit the compiled wiki. This is the gate that makes an article "done".
allowed-tools: Bash(histmersion:*), {{tool:*}}, Agent, Read, Write
---

# Review an article — three adversaries, one fixer

`/review-article <slug> [<slug>…] [--fix] [--rounds N]` · `/review-article --kind concept --fix` ·
`/review-article --all`

The writer's self-check is what the writer believes. This skill is what a reader finds. An article
compiled from Histmersion is **not done** until this review returns no MAJOR finding (the `article-gate`
hook reminds you after every `article put/patch`). The three reviewers are chosen to disagree:

| agent | pulls toward | has access to |
|---|---|---|
| `article-rules-reviewer` | the letter of `docs/writing-guide.md`; facts as recorded | the database (`histmersion`) |
| `article-cold-reader` (a smaller model, on purpose) | a general reader's comprehension — every term defined, every claim restatable, the *why* present | the article text only |
| `article-skeptic` | a specialist's depth — distinctions, critiques, dates, the live debate | the database |

The tension is deliberate: the cold reader would simplify, the skeptic would complicate. The
**resolution rule** (enforced by `article-fixer`) is *define, never delete*: depth stays, and it
gets explained. An article passes only when both are satisfied.

## 0. Or hand the whole panel to the server

When the server runs embedded agents (`histmersion agent models` says **enabled** and the reviewer,
cold-reader, skeptic and fixer roles are ready), one call does steps 1–5 there, checkpointed and
budgeted, with the same four agent prompts: `histmersion agent run review-article <slug> --fix --follow`
(or the MCP tool `start_run` with `pipeline: "review-article"`, `fix: true`). Report its verdict from
`histmersion agent show <id>`; statuses and edges its skeptic proposes wait in `histmersion agent
approvals`. Otherwise — agents off, a role not configured — run the panel yourself, below.

## 1. Export the article and run the mechanical checks

```bash
histmersion article get <slug>                                     # confirm it exists (updated_at: query the articles table)
histmersion article export $SCRATCH/review                         # writes $SCRATCH/review/<kind>/<slug>.md
histmersion lint --rule article-filler --rule article-uncited-paragraph | grep "\*\*<slug>\*\*"
histmersion query "select slug from articles where slug='<slug>' and position('\\[\\[' in body)>0"   # escaped links → MAJOR
histmersion article coverage <slug>                                # the recorded filter against the corpus
```

`$SCRATCH` is the session scratchpad directory. Mechanical hits are findings with id `M<n>`. The
coverage is too: every source under "neither cited nor given a reason" is one MAJOR finding, a stale
reason a minor one. When the article records a filter its Not used list and `_Method_` note are
GENERATED (the export shows them between markers): a hand-written `## Not used` or `_Method_` line in
the body is a MAJOR finding. When it records none (`article coverage` answers 404), say so in the
report — set it with `histmersion article patch <slug> --filter-…` if the article's topic is clear.

## 2. Run the panel — three agents, in parallel, in one message

Spawn all three with the **Agent** tool in the same message (they are independent). Each brief
contains: the `slug`, the absolute path of the exported markdown, the absolute path of
`docs/writing-guide.md`, and "return only the JSON block your definition specifies".

- `subagent_type: article-rules-reviewer`
- `subagent_type: article-cold-reader`
- `subagent_type: article-skeptic`

Do not review the article yourself while they run; wait for the three notifications.

## 3. Merge

Parse the three JSON blocks. The article's verdict is the **worst** of the four (mechanical + three
reviewers). Build one findings list, deduplicated by `where` + `problem`, tagged with its
reviewer (`R`, `C`, `S`, `M`). Where a cold-reader finding and a skeptic finding touch the same
passage, keep both — the fixer must satisfy both.

Report to the user as a table: slug · verdict · majors · minors · explain-back empty slots ·
`db_issues` from the skeptic. Print the major findings verbatim (quote + fix).

**Persist the merged findings in Histmersion before anything else happens** — never only in the
scratchpad (it lives in `/private/tmp`, which macOS clears; a whole round of reviews was lost that way
on 2026-10-02):

```bash
histmersion agent-state set "review/<slug>/round<N>" "$(cat merged.json)"   # verdict + findings + db_issues
histmersion agent-state get "review/<slug>/round<N>"                         # resume from here after a break
```

Resuming a paused panel starts from `histmersion agent-state list | grep review/` — not from files.

## 4. Fix (only with `--fix`)

Spawn `subagent_type: article-fixer` with the slug, the markdown path, the guide path, the merged
findings JSON and the `db_issues`. It verifies every factual finding against the database before
applying it (starting from each finding's `evidence` records), rejects what it cannot confirm (with
evidence), edits the exported file locally — the passages the findings name, never a rewrite of the
whole body — dry-runs the result with `histmersion article check <slug> --body-file …`, patches the
article with `histmersion article patch <slug> --body-file … --if-updated-at …`, sets the reasons of
the sources it leaves out with `--reason <citekey>=<why>`, fixes `db_issues` with
`histmersion patch <citekey> …`, and returns its JSON.

Then **verify, do not re-review** (docs/research-review-convergence.md: a second full panel finds a
fresh set of MAJORs every time, half of them in text the fixer wrote). Step 1 again, then spawn only
the reviewers that own a finding the fixer lists as applied, each with: the merged findings of its
own that the fixer claims fixed, the **edited passages** (`git diff --word-diff` of the export before
and after, each changed paragraph with one paragraph of margin), and the article. Each answers per
finding `fixed` true/false with a quote of the current text, and reports regressions in the edited
passages only; outside them only a factual error with evidence counts as MAJOR. Carry the findings
forward under their ids (`agent-state` `review/<slug>/ledger`): fixed, rejected (with the record's
sentence), still open, plus a lesson for each rejection or failed fix, which the fixer reads next
time. Fix again while a MAJOR is open and `--rounds` (default 3) allows; when none is open, run the
full panel once more, with the ledger, to confirm. Rejected findings that a reviewer raises again
are escalated to the user, not silently dropped.

After the last round, majors that are **contained** (one sentence, and the reviewer quoted the
record) may be applied by the orchestrator directly — verify each with your own `histmersion passages <ck> "<term>"`
(read every passage and the count of the rest; `head` on a grep hid two true matches once), patch with
`--if-updated-at`, re-lint. Anything larger is logged, not patched. Watch the fixer's own additions:
in practice most late-round majors were introduced by an earlier fix (a number from memory, an
attribution to the wrong paper of a pair, bookkeeping words in prose).

## 5. Record

```bash
histmersion queue log add "review-article <slug>: round N → <verdict> (R:x C:y S:z), fixer applied a / rejected b"
histmersion queue tasks add "review-article <slug>: 2 open MAJORs — R4 (…): …; S2 (…): …"   # ONE item per article, only while a MAJOR remains
```

If the article already has an open item (`histmersion queue tasks` — one starting `review-article [[<slug>]]:` or
`review-article <slug>:`), rewrite it (`histmersion queue edit <id> "<text>"`) instead of adding a
second; a server run finds and rewrites it the same way.

Multiple slugs: run steps 1–5 per slug; the panels of different slugs may run concurrently (three
agents per slug — keep it to ~4 slugs in flight). `--all` / `--kind` enumerate with
`histmersion article list --kind …`.

## Done means

Every slug reviewed has a logged verdict; with `--fix`, no MAJOR remains or the user has been told
which one does and why the fixer could not resolve it. Nothing was reviewed from memory: every
factual finding names the `histmersion` call that supports it.
