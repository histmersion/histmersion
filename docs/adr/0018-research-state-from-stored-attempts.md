# 0018 — The research state is computed from stored, typed acquisition attempts

Status: accepted (owner decisions 2026-11-20, queue row 22). Design: `notes/design-research-state.md` (coordinator's notes).

## Context

Research › State shows, for every open item, who acts next: the person or the agents, and why. For an acquisition it
must tell "a provider will fetch it" from "no free legal copy found: buy or borrow", "download it in the browser" and
"borrow it". Before this record the reasons were sentences (`acquire_checks.reason`, one row per item, each check
overwriting the last), and a fetcher's refusal was one untyped case (`Fetch.Refused(why)`), so the page could only have
guessed by reading text.

## Decision

1. **Fetchers answer typed (SPI 3).** `Fetch.Miss(absence, why)` with `Absence` = `NoIdentifier | NotHeld | Unanswered |
   RateLimited | Unconfigured`; `Fetch.Refused(refusal, why)` with `Refusal` = `BotChallenge | LendingOnly |
   NoEntitlement | LicenceForbids`. `Fetch.failed(problem)` maps a failed request to the answer it stands for, in one
   place. The host carries the answer as `Problem.NotFound.Said` in `Problem.NotFound.Answer`. `plugin-api-api.txt`
   pins the SPI surface under its version (`SpiSurfaceSuite`, G99). A problem entry stored before the change, of the old
   `Copy` shape, no longer decodes as its case and reads as its sentence (the existing unknown-entry path); nothing
   stores those entries for long, and nothing branches on them.
2. **Every attempt is kept.** `acquire_attempts` (migration V112) replaces `acquire_checks`: one row per attempt, the
   newest `acquire.recheck.attempts-kept` (20) per item, each with its trigger, fetcher generation, outcome, every
   fetcher's typed answer (`answers`), the failure (`failure`) and the open-access facts the metadata gave (`oa`). The
   JSON columns have a stored shape of their own, defined in SQL by `AcquireCheckRepo` (G91): `answers` is an array of
   `{fetcher, said, why}` with `said` a stored word (`not-held`, `bot-challenge`, …) and `why` a problem entry,
   `failure` the problems column's entry array, `oa` `{scripts, status, url, licence}`. The repository builds and reads
   them from typed SQL values, so no wire codec reaches the store; a row that does not read is shown as a legacy
   attempt and is never rewritten.
3. **The old checks become legacy attempts.** Each `acquire_checks` row becomes one attempt with outcome `legacy`, its
   reason text kept verbatim in `legacy_reason` and its count in `legacy_checks`, under the generation `legacy`. Since an
   attempt under another generation makes an item due (`AcquireRecheck.dueAt`), the scheduled re-check checks every
   item once more within its daily caps and the shared allowance. That is the owner's "re-check the list once", with no
   separate job.
4. **The verdict is a pure function, not a stored column.** `AcquireVerdict.of(identifier, attempts, generation,
   settings, now)` (core) decides `Unchecked | Converting | Retrying` (the agents) or `Stuck | ManualDownload | Borrow |
   NoFreeCopy | NoIdentifier` (the person). It depends on the clock, the fetcher generation and the give-up threshold
   (`acquire.recheck.give-up-checks` 5 / `give-up-days` 14), so storing it would make it stale. Only the legal chain's
   attempts are recorded, so an unauthorised fetcher never makes an item the agents'.
5. **Tasks carry their actor.** `queue_items.actor` (`person | agents`) is required for every task. The migration sets
   `agents` for the reviewer's own `agent review` tasks and `person` for every other task (a one-time reading of the
   stored topic). Afterwards a task's actor is set when it is created and changed only explicitly.
6. **No sentence is read back.** The text readers of the re-check (`AcquireRecheck.replies`, `reason`'s parsing) are
   deleted. The page's wording comes from the types.

## Consequences

Plugins must be rebuilt against SPI 3. A provider that keeps failing becomes the person's after the threshold, with the
last problem shown. An unreadable stored attempt is shown as legacy text and makes the item due, never a default.
