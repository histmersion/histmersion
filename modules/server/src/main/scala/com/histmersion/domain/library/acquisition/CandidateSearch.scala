package com.histmersion
package domain.library.acquisition

import com.histmersion.api.Problem
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.sources.SourceService

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind

import hearth.kindlings.optics.syntax.*

import java.time.{LocalDate, ZoneOffset}
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.library.{Acquire, Candidates, CandidateSweep, CandidateWatch, WorkRef}
import com.histmersion.api.Api
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.search.Search
import io.github.iltotore.iron.constraint.any.Pure

/** The deterministic new-source search of one goal (#3761, docs/research-goal-advancement.md): its WATCHES — every
  * saved query (its linked `query` links, else its title) on every index of `acquire.candidates.indexes`, and for up to
  * `sources` of its sources (linked, and cited by its linked articles) the works citing it and the newer works of its
  * first and last authors (OpenAlex) — each read from its watermark; the works found deduplicated by DOI across watches
  * and against the corpus, the acquisition list and the goal's earlier candidates; the new ones stored as `new`. No
  * model runs here: the sweep only calls the indexes, so a schedule can run it as often as it likes for the price of
  * the requests, and starts a model only when it found something new.
  */
final class CandidateSearch(
    candidates: CandidateService,
    sources: SourceService,
    goals: GoalService,
    articles: ArticleCitations,
    queues: QueueService,
    cfg: => CandidatesConfig,
    indexes: => List[ScholarlyIndex],
    alerts: => CitationAlerts,
    today: () => LocalDate
):

  /** The goal's watches as of now: what is read, from where (the watermark is the stored one). */
  private def plan(goal: Goal): List[CandidateWatch] =
    val c = cfg
    val links = goals.links(com.histmersion.domain.automation.Goal.Slug.assume(goal.slug.value))
    def refs(kind: LinkKind) = links.filter(_.kind == kind).map(_.ref.value)
    val watchGoal = CandidateWatch.Goal(goal.slug.value)
    val queries = Some(refs(LinkKind.Query)).filter(_.nonEmpty).getOrElse(List(goal.title.value))
    val q = for query <- queries; i <- indexes
    yield CandidateWatch(
      watchGoal,
      Candidates.WatchKind.Query.wire,
      CandidateWatch.Subject(query),
      CandidateWatch.Index(i.name.value)
    )
    val cited = (refs(LinkKind.Source).flatMap(Citekey.option(_)) ++
      refs(LinkKind.Article).flatMap(Slug.option(_)).flatMap(articles.cited)).distinct
      .take(math.max(0, c.sources.value))
    val withDoi = cited.flatMap(ck =>
      sources.load(ck).flatMap(_.doi).flatMap(doi => Ident.doi(Ident.Written(doi.value))).map(ck -> _)
    )
    val citing = withDoi.map((ck, doi) =>
      CandidateWatch(
        watchGoal,
        Candidates.WatchKind.Citing.wire,
        CandidateWatch.Subject(doi.value),
        CandidateWatch.Index("openalex")
      )
    )
    q ++ citing

  /** Sweep `goal` now; a dry run reads, but stores neither candidates nor watermarks. */
  def sweep(
      goal: com.histmersion.domain.automation.Goal.Slug,
      dryRun: CandidateSearch.SweepDryRun
  ): Either[Api.Error, CandidateSweep] =
    goals
      .get(goal)
      .toRight(Api.Error(Problem(s"no goal '$goal'"), Some(Api.Error.Code(Write.CodeNotFound))))
      .map { g =>
        val c = cfg
        val now = today()
        val watchGoal = CandidateWatch.Goal(g.slug.value)
        val stored = candidates.watches(goal).map(w => (w.kind, w.subject, w.index) -> w).toMap
        def known(w: CandidateWatch) =
          stored.get((w.kind, w.subject, w.index)).fold(w)(s => w.modify(_.watermark).setTo(s.watermark))
        val base = plan(g).map(known)
        val byIndex = indexes.map(i => i.name -> i).toMap
        // the authors of the cited sources: their own watches (found through OpenAlex, then kept)
        val authorWatches = base
          .filter(_.kind == Candidates.WatchKind.Citing.wire)
          .flatMap { w =>
            Doi.option(w.subject.value).toList.flatMap { doi =>
              alerts
                .authors(doi)
                .toOption
                .toList
                .flatMap(as => (as.headOption ++ as.lastOption).toList.distinct.take(c.authorsPerSource.value))
                .map(a =>
                  known(
                    CandidateWatch(
                      watchGoal,
                      Candidates.WatchKind.Author.wire,
                      CandidateWatch.Subject(a.value),
                      CandidateWatch.Index("openalex")
                    )
                  )
                )
            }
          }
          .distinctBy(w => w.subject)
        // an index that may not be asked now (CORE's budget spent, CORE asked to wait) is left out of the rest
        // of this sweep: its watches keep their watermarks and are read next time
        val leftOut =
          scala.collection.mutable.LinkedHashMap.empty[ScholarlyIndex.Name, (String, Int)] // this sweep's tally
        def query(i: ScholarlyIndex, w: CandidateWatch, since: LocalDate): Either[String, List[WorkRef]] =
          leftOut.get(i.name).orElse(i.unavailable.map(p => p.value -> 0)) match
            case Some((why, n)) =>
              leftOut.update(i.name, (why, n + 1))
              Left(s"not asked this sweep — $why")
            case None =>
              val got = i.search(Search.Query(w.subject.value), since, ScholarlyIndex.Limit(c.perWatch.value))
              got.left.foreach(_ => i.unavailable.foreach(why => leftOut.update(i.name, (why.value, 0))))
              got.left.map(_.value)
        val read = (base ++ authorWatches).map { w =>
          val since = Candidates.since(
            w.watermark.map(m => Candidates.Watermark(m.value)),
            now,
            Candidates.Lookback(c.lookbackDays.value)
          )
          val got: Either[String, List[WorkRef]] = Candidates.WatchKind.parse(w.kind) match
            case Some(Candidates.WatchKind.Query) =>
              byIndex
                .get(ScholarlyIndex.Name(w.index.value))
                .toRight(s"index '${w.index}' is not configured")
                .flatMap(query(_, w, since))
            case Some(Candidates.WatchKind.Citing) =>
              Doi
                .either(w.subject.value)
                .flatMap(doi =>
                  alerts.citing(doi, since, CitationAlerts.CitingLimit(c.perWatch.value)).left.map(_.value)
                )
            case Some(Candidates.WatchKind.Author) =>
              alerts
                .byAuthor(
                  CitationAlerts.ByAuthorAuthorId(w.subject.value),
                  since,
                  CitationAlerts.ByAuthorLimit(c.perWatch.value)
                )
                .left
                .map(_.value)
            case None => Left(s"unknown watch kind '${w.kind}'")
          (w, since, got)
        }
        val found = read.flatMap { (w, _, got) =>
          val by = CandidateDedupe.FoundBy(w.kind.value match
            case "query"  => s"${w.index} query \"${w.subject}\""
            case "citing" => s"${w.index} citing ${w.subject}"
            case _        => s"${w.index} author ${w.subject}")
          got.toOption.toList.flatten.map(by -> _)
        }
        // known already: the corpus, the acquisition list (open or done)
        val corpus = sources.doiIndex.keySet
        val queued = queues.list(Queue.Kind.Acquire, QueueScope.All).flatMap(i => Scheduling.dois(i.text.value)).toSet
        val (fresh, skipped) = CandidateDedupe.dedupe(found, corpus ++ queued)
        val earlier = candidates.dois(goal)
        val again = fresh.count((k, _, _) => earlier.contains(k))
        val added =
          if dryRun.value then fresh.count((k, _, _) => !earlier.contains(k))
          else
            fresh.count { (k, w, by) =>
              candidates.upsert(
                Source.Candidate(
                  Source.Candidate.Id(0L),
                  watchGoal,
                  com.histmersion.api.library.Doi(k.value),
                  w.title.getOrElse(com.histmersion.api.library.Title(k.value)),
                  w.year,
                  w.authors.take(8),
                  w.venue,
                  w.abstractText.map(a => Source.Candidate.AbstractText(a.value.take(2000))),
                  w.isOa,
                  w.arxivId,
                  by.map(b => Source.Candidate.FoundBy(b.value))
                )
              )
            }
        // a watch read moves its watermark to today; a failed one keeps its own (nothing is skipped)
        val watches = read.map { (w, _, got) =>
          val mark = got.toOption.map(_ => CandidateWatch.Watermark(now.toString))
          val lastFound = CandidateWatch.LastFound(got.map(_.size).getOrElse(0))
          if !dryRun.value then
            candidates.recordWatch(
              w,
              mark,
              got.left.toOption.map(e => domain.Problem(e.take(500))),
              lastFound
            )
          w.modify(_.watermark)
            .setTo(if dryRun.value then w.watermark else mark.orElse(w.watermark))
            .modify(_.lastError)
            .setTo(got.left.toOption.map(Problem(_)))
            .modify(_.lastFound)
            .setTo(lastFound)
        }
        val unseen = candidates
          .counts(Some(goal))
          .find(_.state.value == Candidates.State.New.wire.value)
          .fold(CandidateSweep.Unseen(0))(n => CandidateSweep.Unseen(n.count.value))
        val notes = leftOut.toList.map { case (index, (why, n)) =>
          CandidateSweep.Notes(
            s"$index not asked this sweep: $why — ${Plural.count(Plural.CountN(n), Plural.CountSingular("watch"), Plural.CountPlural("watches"))} left unread, which keep their " +
              "watermarks and are read next sweep"
          )
        }
        CandidateSweep(
          CandidateSweep.Goal(g.slug.value),
          watches,
          CandidateSweep.Found(found.size),
          CandidateSweep.Added(added),
          CandidateSweep.Again(again),
          skipped.map((reason, n) => CandidateSweep.SkippedKey(reason.value) -> CandidateSweep.SkippedValue(n.value)),
          unseen,
          CandidateSweep.DryRun(dryRun.value),
          notes
        )
      }

object CandidateSearch:
  type SweepDryRun = SweepDryRun.T
  object SweepDryRun extends Newtype[Boolean, Pure]
