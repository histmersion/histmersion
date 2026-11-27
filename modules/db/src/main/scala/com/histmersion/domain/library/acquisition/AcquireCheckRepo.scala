package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.AcquireRecheck
import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.problem.Problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.augustnagro.magnum.*

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Sql, "reads and writes the acquire check tables and their row codecs")
private[acquisition] object AcquireCheckRepo:

  type SaidWire = SaidWire.T
  object SaidWire extends Newtype[String, Pure]
  type ScriptsWire = ScriptsWire.T
  object ScriptsWire extends Newtype[String, Pure]

  enum StoredSaid(val wire: SaidWire, val said: Problem.NotFound.Said) derives CanEqual:
    case NoIdentifier
        extends StoredSaid(SaidWire("no-identifier"), Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NoIdentifier))
    case NotHeld extends StoredSaid(SaidWire("not-held"), Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld))
    case Unanswered
        extends StoredSaid(SaidWire("unanswered"), Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unanswered))
    case RateLimited
        extends StoredSaid(SaidWire("rate-limited"), Problem.NotFound.Said.Miss(Problem.NotFound.Absence.RateLimited))
    case Unconfigured
        extends StoredSaid(SaidWire("unconfigured"), Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unconfigured))
    case BotChallenge
        extends StoredSaid(
          SaidWire("bot-challenge"),
          Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge)
        )
    case LendingOnly
        extends StoredSaid(
          SaidWire("lending-only"),
          Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.LendingOnly)
        )
    case NoEntitlement
        extends StoredSaid(
          SaidWire("no-entitlement"),
          Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.NoEntitlement)
        )
    case LicenceForbids
        extends StoredSaid(
          SaidWire("licence-forbids"),
          Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.LicenceForbids)
        )

  object StoredSaid:
    def of(said: Problem.NotFound.Said): StoredSaid = values.find(_.said == said).getOrElse(NotHeld)
    def parse(w: SaidWire): Option[StoredSaid] = values.find(_.wire == w)

  enum StoredScripts(val wire: ScriptsWire, val scripts: AcquireAttempt.Oa.Scripts) derives CanEqual:
    case Allowed extends StoredScripts(ScriptsWire("allowed"), AcquireAttempt.Oa.Scripts.Allowed)
    case Refused extends StoredScripts(ScriptsWire("refused"), AcquireAttempt.Oa.Scripts.Refused)

  object StoredScripts:
    def of(s: AcquireAttempt.Oa.Scripts): StoredScripts = values.find(_.scripts == s).getOrElse(Allowed)
    def parse(w: ScriptsWire): Option[StoredScripts] = values.find(_.wire == w)

  private type Selected = (
      AcquireAttemptRow.Id,
      String,
      AcquireAttemptRow.Trigger.Wire,
      AcquireRecheck.LastCheck.Generation,
      AcquireRecheck.Outcome.Wire,
      Option[Citekey],
      Option[AcquireAttempt.Outcome.Source],
      List[Problem.NotFound.Fetcher],
      List[SaidWire],
      Option[NonEmptyVector[Problem]],
      Option[NonEmptyVector[Problem]],
      Option[ScriptsWire],
      Option[AcquireAttempt.Oa.OaStatus],
      Option[AcquireAttempt.Oa.OaUrl],
      Option[AcquireAttempt.Oa.OaLicence],
      Option[AcquireAttempt.Outcome.LegacyNote],
      Option[AcquireAttempt.Tries]
  )

  private val Select =
    sql"""SELECT item_id, ${Timestamps.iso(sql"at")}, trigger, generation, outcome, citekey, source,
                 ARRAY(SELECT f.v FROM jsonb_array_elements_text(jsonb_path_query_array(answers, '$$[*].fetcher'))
                         WITH ORDINALITY AS f(v, i) ORDER BY f.i),
                 ARRAY(SELECT s.v FROM jsonb_array_elements_text(jsonb_path_query_array(answers, '$$[*].said'))
                         WITH ORDINALITY AS s(v, i) ORDER BY s.i),
                 nullif(jsonb_path_query_array(answers, '$$[*].why'), '[]'::jsonb),
                 failure, oa ->> 'scripts', oa ->> 'status', oa ->> 'url', oa ->> 'licence', legacy_reason, legacy_checks
            FROM acquire_attempts"""

  private def grouped(rows: Vector[Selected]): Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
    rows
      .map { row =>
        val (
          item,
          atText,
          trigger,
          generation,
          outcome,
          citekey,
          source,
          fetchers,
          saids,
          whys,
          failed,
          scripts,
          status,
          url,
          licence,
          reason,
          checks
        ) = row
        val at = Instant.parse(atText)
        val answers = fetchers
          .zip(saids)
          .zip(whys.fold(Vector.empty[Problem])(NonEmptyVector.toVector))
          .map { case ((fetcher, said), why) =>
            StoredSaid.parse(said).map(s => Problem.NotFound.Answer(fetcher, s.said, why))
          }
        val facts =
          scripts.map(w => StoredScripts.parse(w).map(s => AcquireAttempt.Oa(status, url, licence, s.scripts)))
        val found: Option[AcquireAttempt.Outcome] =
          (AcquireRecheck.Outcome.parse(outcome), citekey, source, failed) match
            case (Some(AcquireRecheck.Outcome.Closed), Some(ck), _, _)      => Some(AcquireAttempt.Outcome.Closed(ck))
            case (Some(AcquireRecheck.Outcome.Fetched), _, Some(s), _)      => Some(AcquireAttempt.Outcome.Fetched(s))
            case (Some(AcquireRecheck.Outcome.NoCopy), _, _, _)             => Some(AcquireAttempt.Outcome.NoCopy)
            case (Some(AcquireRecheck.Outcome.Failed), _, _, Some(why))     => Some(AcquireAttempt.Outcome.Failed(why))
            case (None, _, _, _) if outcome == AcquireAttemptRow.LegacyWire =>
              Some(
                AcquireAttempt.Outcome.Legacy(
                  reason.getOrElse(AcquireAttemptRow.NoNote),
                  checks.getOrElse(AcquireAttempt.Tries.One)
                )
              )
            case _ => None
        val read =
          for
            kind <- AcquireAttemptRow.Trigger.parse(trigger)
            result <- found
            said <- Option.when(answers.size == fetchers.size && answers.forall(_.isDefined))(answers.flatten.toVector)
            oa <- facts.fold[Option[Option[AcquireAttempt.Oa]]](Some(None))(_.map(Some(_)))
          yield AcquireAttempt(at, kind.domain, generation, result, said, oa)
        item -> read.getOrElse(AcquireAttemptRow.unreadable(at))
      }
      .groupBy(_._1)
      .view
      .mapValues(_.toList.map(_._2))
      .toMap

  def of(items: List[AcquireAttemptRow.Id])(using DbCon): Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
    if items.isEmpty then Map.empty
    else
      grouped(
        sql"""$Select WHERE item_id::text = ANY(${items.distinct.map(_.toString)}::text[])
              ORDER BY item_id, at DESC, id DESC"""
          .query[Selected]
          .run()
      )

  def open()(using DbCon): Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
    grouped(
      sql"""$Select WHERE item_id IN (SELECT id FROM queue_items WHERE list = 'acquire' AND status = 'open')
            ORDER BY item_id, at DESC, id DESC"""
        .query[Selected]
        .run()
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def record(item: AcquireAttemptRow.Id, attempt: AcquireAttempt, kept: AcquireAttempt.Kept)(using DbCon): Unit =
    val trigger = AcquireAttemptRow.Trigger.of(attempt.trigger).wire
    val fetchers = attempt.answers.map(_.fetcher).toList
    val saids = attempt.answers.map(a => StoredSaid.of(a.said).wire).toList
    val whys = NonEmptyVector.from(attempt.answers.map(_.why))
    val scripts = attempt.oa.map(o => StoredScripts.of(o.scripts).wire)
    val status = attempt.oa.flatMap(_.status)
    val url = attempt.oa.flatMap(_.url)
    val licence = attempt.oa.flatMap(_.licence)
    val at = attempt.at.toString
    val (outcome, citekey, source, failure, reason, checks) = attempt.outcome match
      case AcquireAttempt.Outcome.Closed(ck)  => (AcquireRecheck.Outcome.Closed.wire, Some(ck), None, None, None, None)
      case AcquireAttempt.Outcome.Fetched(s)  => (AcquireRecheck.Outcome.Fetched.wire, None, Some(s), None, None, None)
      case AcquireAttempt.Outcome.NoCopy      => (AcquireRecheck.Outcome.NoCopy.wire, None, None, None, None, None)
      case AcquireAttempt.Outcome.Failed(why) =>
        (AcquireRecheck.Outcome.Failed.wire, None, None, Some(why.toNonEmpty), None, None)
      case AcquireAttempt.Outcome.Legacy(r, n) => (AcquireAttemptRow.LegacyWire, None, None, None, Some(r), Some(n))
    val _ =
      sql"""DELETE FROM acquire_attempts
             WHERE id IN (SELECT id FROM acquire_attempts WHERE item_id = $item
                           ORDER BY at DESC, id DESC OFFSET GREATEST(0, $kept::int - 1))
             RETURNING id""".query[AcquireAttemptRow.Id].run()
    val _ =
      sql"""INSERT INTO acquire_attempts (item_id, at, trigger, generation, outcome, citekey, source, answers, failure,
                                          oa, legacy_reason, legacy_checks)
            VALUES ($item, $at::timestamptz, $trigger, ${attempt.generation}, $outcome, $citekey, $source,
                    coalesce(
                      (SELECT jsonb_agg(
                                jsonb_build_object('fetcher', a.f, 'said', a.s, 'why', $whys::jsonb -> (a.n - 1)::int)
                                ORDER BY a.n
                              )
                         FROM unnest($fetchers::text[], $saids::text[]) WITH ORDINALITY AS a(f, s, n)),
                      '[]'::jsonb),
                    $failure::jsonb,
                    CASE WHEN $scripts::text IS NULL THEN NULL
                         ELSE jsonb_build_object('scripts', $scripts::text, 'status', $status::text, 'url', $url::text,
                                                 'licence', $licence::text) END,
                    $reason, $checks)
            RETURNING item_id""".query[AcquireAttemptRow.Id].run()

  def countSince(since: Instant)(using DbCon): Map[AcquireRecheck.Outcome.Wire, RowCount] =
    val from = since.toString
    sql"""SELECT outcome, count(*)::int FROM acquire_attempts
           WHERE at >= $from::timestamptz AND outcome <> 'legacy' GROUP BY outcome"""
      .query[(AcquireRecheck.Outcome.Wire, RowCount)]
      .run()
      .toMap

  def heldByDoi(dois: List[Doi])(using DbCon): Map[Doi, AcquireRecheck.Held] =
    if dois.isEmpty then Map.empty
    else
      sql"""SELECT DISTINCT ON (lower(doi)) lower(doi), citekey,
                             (coalesce(ocr_markdown, '') <> '' OR sha256 IS NOT NULL)
                      FROM sources
                     WHERE lower(doi) = ANY(ARRAY(SELECT lower(trim(d)) FROM unnest(${dois.distinct}::text[]) AS d))
                     ORDER BY lower(doi), citekey"""
        .query[(Doi, Citekey, AcquireRecheck.Held.HasOriginal)]
        .run()
        .map((d, ck, has) => d -> AcquireRecheck.Held(ck, has))
        .toMap
