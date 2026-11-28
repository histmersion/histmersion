package com.histmersion
package domain

import com.histmersion.domain.library.acquisition.LastCheck
import com.histmersion.domain.library.acquisition.Cadence
import com.histmersion.domain.library.acquisition.Held
import com.histmersion.domain.library.acquisition.Action
import com.histmersion.domain.library.acquisition.Recheckable

import com.histmersion.util.problem.Text.{line, sentence, sentences}
import com.histmersion.util.problem.{Problem, Problems}
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.jobs.JobState

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, ZoneOffset}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition list's
  * re-check"). An item on the acquisition list — the person's buy/borrow list — was put there because no legal copy was
  * found THEN; fetchers and keys added since may have one. A model-free pass of the managers' tick looks at every open
  * item that names a DOI: one the corpus holds with its original is closed; one checked within `every-days` under the
  * same fetcher generation is left alone; the others (a few a pass, a cap a day, within the shared legal ingest
  * allowance) are tried through the LEGAL chain only. The pure rules; the server reads the facts and writes the result.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryAcquisition)
object AcquireRecheck:
  type Allowance = Allowance.T
  object Allowance extends Newtype[Int, Pure]
  type CheckedToday = CheckedToday.T
  object CheckedToday extends Newtype[Int, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
  type GenerationPart = GenerationPart.T
  object GenerationPart extends Newtype[String, Pure]
  type ProgressLine = ProgressLine.T
  object ProgressLine extends Newtype[String, Pure]

  /** `acquire.recheck.*` as the rules read them: on or off, how many days an item's check holds, at most how many items
    * a day and a pass, the minutes between passes, the share (percent) of the shared daily legal ingest allowance the
    * re-check may take, and the hours after which a check that got no answer is due again.
    */
  export LastCheck.Generation as IsDueGeneration
  export Cadence.{EveryDays as IsDueEveryDays, RetryHours as IsDueRetryHours}
  export LastCheck.Generation as NextDueGeneration
  export Cadence.{EveryDays as NextDueEveryDays, RetryHours as NextDueRetryHours}

  /** When an item is next due: never checked, or checked under another fetcher generation (a fetcher or a key was added
    * since) = now (None); a check that got no answer `retryHours` after it; else `everyDays` after its check.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dueAt(
      last: Option[LastCheck],
      generation: LastCheck.Generation,
      everyDays: Cadence.EveryDays,
      retryHours: Cadence.RetryHours
  ): Option[Instant] =
    last
      .filter(_.generation == generation)
      .map(l =>
        if l.failed.value then l.at.plusSeconds(math.max(1, retryHours.value).toLong * 3600L)
        else l.at.plusSeconds(math.max(1, everyDays.value).toLong * 86400L)
      )

  def isDue(
      last: Option[LastCheck],
      generation: IsDueGeneration,
      everyDays: IsDueEveryDays,
      retryHours: IsDueRetryHours,
      now: Instant
  ): Boolean =
    dueAt(last, generation, everyDays, retryHours).forall(!_.isAfter(now))

  /** The first DOI an item names decides (an item is one work). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def action(dois: List[Doi], held: Doi => Option[Held]): Action =
    dois.headOption match
      case None      => Action.NoDoi
      case Some(doi) =>
        held(doi) match
          case Some(h) if h.hasOriginal.value => Action.Close(h.citekey)
          case other                          => Action.Fetch(doi, other.map(_.citekey))

  /** A failed legal ingest: no legal copy, or something else in the way. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def failure(message: Problems): domain.library.acquisition.Upshot =
    if IngestBacklog.isNoLegalCopy(message) then domain.library.acquisition.Upshot.NoCopy
    else domain.library.acquisition.Upshot.Failed

  /** What each fetcher answered when a legal ingest found no copy ([[Problem.NotFound.Copy]]'s answers). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def answers(message: Problems): Vector[Problem.NotFound.Answer] =
    message.toVector.collectFirst { case Problem.NotFound.Copy(_, said) => said }.getOrElse(Vector.empty)

  /** Is a pass due: never run (since the start), or `passMinutes` passed since the last. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def passDue(last: Option[Instant], passMinutes: Cadence.PassMinutes, now: Instant): Boolean =
    last.forall(l => !l.plusSeconds(math.max(1, passMinutes.value).toLong * 60L).isAfter(now))

  /** The fetcher generation: one short digest of what the legal chain is — its fetchers in order, and each one's plugin
    * and settings (secrets as references) — so a fetcher or a key added changes it.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def generation(parts: List[GenerationPart]): LastCheck.Generation =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    LastCheck.Generation(
      md.digest(parts.mkString("\n").getBytes(StandardCharsets.UTF_8)).take(8).map(b => f"${b & 0xff}%02x").mkString
    )

  /** The earliest time one of the checked items falls due again (None: none waits). */
  def nextDue(
      checks: List[LastCheck],
      generation: NextDueGeneration,
      everyDays: NextDueEveryDays,
      retryHours: NextDueRetryHours
  ): Option[Instant] =
    checks.flatMap(c => dueAt(Some(c), generation, everyDays, retryHours)).minOption

  /** The order items are tried in: the never-checked first, then the oldest check, then the item id. */
  def order(candidates: List[Recheckable]): List[Recheckable] =
    candidates.sortBy(c => (c.last.isDefined, c.last.fold(0L)(_.at.toEpochMilli), c.item))

  /** Was this item checked by a job that started at `since` (or since then by anything else)? */
  def checkedSince(last: Option[LastCheck], since: Instant): Boolean = last.exists(l => !l.at.isBefore(since))
