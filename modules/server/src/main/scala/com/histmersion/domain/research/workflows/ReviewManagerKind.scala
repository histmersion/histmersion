package com.histmersion.domain.research.workflows

import com.histmersion.domain.automation.managers.{ManagerContext, ManagerKindDefinition, ManagerSupervisor}
import com.histmersion.domain.automation.worklists.{QueueItemId, QueueScope}

import com.histmersion.api.Plural
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueuePatch}
import com.histmersion.domain.*
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings, StepFacts}
import com.histmersion.domain.research.workflows.ReviewManagerPolicy.ReviewFacts
import com.histmersion.config.{AgentsConfig, ResearchAgentsConfig, ReviewManagerConfig}
import com.histmersion.domain.{ManagerDefaults, ReviewManagerDefaults}
import io.scalaland.chimney.dsl.*

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.automation.Queue
import com.histmersion.api.Api

/** The `review` manager kind (library-wide: article-review, infer-filter and article-extend items), research's: the
  * review's own evaluation over the article's review ledger, the selection that refills the queue (open MAJORs,
  * never-reviewed, then stale articles, an infer-filter item before the review of an article that records no filter,
  * the prioritise hints), the articles new sources reach, and the runs its items start.
  */
final class ReviewManagerKind(site: ResearchSite, ledgers: => ReviewLedgerService, cfg: => ResearchAgentsConfig)
    extends ManagerKindDefinition:
  val kind: Kind = Kind.Review

  /** Research's defaults of a review manager (`agents.managers.review`). */
  private def reviewDefaults: ReviewManagerDefaults = ReviewManagerKind.defaultsOf(cfg.managers.review)

  def defaults(agents: AgentsConfig, m: ManagerDefaults): ManagerConfig =
    ReviewManagerPolicy.defaults(reviewDefaults, m)

  override def settings(c: ManagerConfig, m: ManagerDefaults): ReviewSettings =
    ReviewManagerPolicy.settings(c, reviewDefaults, m)

  def pipelineOf(itemKind: String): Pipeline = itemKind match
    case InferFilter   => Pipeline.InferFilter
    case ArticleExtend => Pipeline.CompileArticle
    case _             => Pipeline.ReviewArticle

  /** When each review manager last looked for stale articles (epoch ms); in memory — a restart looks again. */
  private val lastStale = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  /** When each review manager last looked for articles to extend (epoch ms); in memory. */
  private val lastExtend = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[String] = List("selection refilled")

  // ── evaluate ───────────────────────────────────────────────────────────────────────────────────

  override def evaluate(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[String],
      now: Instant,
      out: Builder[String, List[String]]
  ): Unit =
    if item.kind == ArticleReview then evaluateReview(c, m, s, item, st, error, now, out)
    else c.evaluateStep(m, s, item, st, error, now, out, this)

  private def evaluateReview(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[String],
      now: Instant,
      out: Builder[String, List[String]]
  ): Unit =
    val run = item.runId.get
    val ledger = ledgers.get(item.target).toOption.flatten
    val facts = Option.when(st == RunState.Done) {
      val pairs = site.articles.openCritiques(item.target)
      ledger.map(ReviewFacts.of(_, pairs)).getOrElse(ReviewFacts(0, 0, 0, onlyMissingCritiques = false, pairs))
    }
    val task = ledger.flatMap(ReviewLedger.taskText(_, run))
    ReviewManagerPolicy.evaluateReview(
      s,
      item.target,
      item.attempts,
      item.failures,
      item.state,
      run,
      st,
      error,
      facts,
      task,
      now
    ) match
      case Next.Done(outcome, state) =>
        // what its filter returns now: more later makes it stale (phase 2)
        val withHits = state.copy(filterHits = site.articles.filterTotal(item.target))
        if c.evaluated(item.id, Some(run), ItemStatus.Done, None, None, withHits, Some(outcome)) then
          c.event(m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $outcome")
          out += s"${item.target}: done"
      case Next.Again(notBefore, state, note) =>
        if c.evaluated(item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note)) then
          c.event(m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")
          out += s"${item.target}: again"
      case Next.Retry(notBefore, failures, note) => c.retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state)       =>
        val failed = st == RunState.Failed
        val status = if failed then ItemStatus.Failed else ItemStatus.Stopped
        val filed = task.flatMap(t => fileTask(item.target, ledger, t))
        val text = outcome + filed.fold("")(id => s" (task #$id)")
        if c.evaluated(item.id, Some(run), status, None, Option.when(failed)(item.failures + 1), state, Some(text))
        then
          c.event(m.id, Some(item.id), Some(run), Feed.Stopped, s"${item.target}: $text")
          out += s"${item.target}: stopped"
      case Next.Wait(_, _) => () // a review leaves no approval to wait for

  /** The stop's backlog item: the article's own review task (the ledger's, while it is open) is rewritten with the
    * reason, else a new one is added. Returns its id.
    */
  private def fileTask(slug: String, ledger: Option[ReviewLedger.Ledger], text: String): Option[Int] =
    val open = ledger
      .flatMap(_.task)
      .flatMap(i => site.queues.get(QueueItemId(i)))
      .filter(i => i.list == Queue.Kind.Tasks.label && i.status == Queue.Open)
      // the article's collapsed item the ledger lost track of (#934)
      .orElse(
        site.queues
          .list(Queue.Kind.Tasks, QueueScope.Open)
          .filter(i => ReviewLedger.articleTask(slug, i.text))
          .sortBy(_.id)
          .headOption
      )
    open match
      case Some(item) => site.workLists.patch(item.id, QueuePatch(text = Some(text))).toOption.map(_.id)
      case None       =>
        site.workLists.add(Queue.Kind.Tasks.label, QueueAdd(text, topic = Some("agent review"))).toOption.map(_.id)

  def facts(c: ManagerContext, item: ManagerItem, run: String): StepFacts =
    lazy val progress = c.progress(run)
    item.kind match
      case InferFilter   => c.approvalFacts(run, ApprovalKind.ArticleFilter, s"no filter proposed — $progress")
      case ArticleExtend =>
        CompileArticlePipeline.written(c, run) match
          case Some(w) if w.written => StepFacts.Finished(s"extended (${w.why})")
          case Some(w)              => StepFacts.Finished(s"nothing appended: ${w.why}")
          case None                 => StepFacts.Finished(s"nothing to extend — $progress")
      case _ => StepFacts.Finished("enriched")

  /** An extended article is reviewed again. */
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[String],
      out: Builder[String, List[String]]
  ): Unit =
    if item.kind == ArticleExtend && run.flatMap(CompileArticlePipeline.written(c, _)).exists(_.written) then
      c.chainReview(m.id, item.target, s"extended by run ${run.fold("")(_.take(8))}", out)

  // ── the selection ──────────────────────────────────────────────────────────────────────────────

  /** Below the low water: the selection — articles with open MAJORs (most first), never-reviewed ones, then stale ones
    * — with an infer-filter item before the review of an article that records no filter, and the standing prioritise
    * hints applied. Then the articles new sources reach.
    */
  override def refill(
      c: ManagerContext,
      m: ManagerInfo,
      now: Instant,
      force: Boolean,
      out: Builder[String, List[String]]
  ): Unit =
    val s = c.settings(m)
    val pending = c.pending(m.id)
    if (force || pending < s.lowWater) && s.refillBatch > 0 then
      val tags = m.config.includeTags.getOrElse(Nil)
      val goal = m.config.includeGoal.filter(_.trim.nonEmpty)
      val majors = c.openMajorArticles(m.id, tags, goal, s.refillBatch)
      val fresh = if majors.size >= s.refillBatch then Nil
      else c.unreviewedArticles(m.id, tags, goal, s.refillBatch - majors.size)
      val room = s.refillBatch - majors.size - fresh.size
      val stale = if room > 0 then staleArticles(c, m, s, tags, goal, room, now) else Nil
      val picks = ReviewManagerPolicy.select(
        majors,
        fresh,
        stale.map((slug, why, _) => (slug, why)),
        s.refillBatch,
        s.inferFilter,
        slug => site.articles.recordedFilter(slug).isDefined,
        slug => c.hasItem(m.id, InferFilter, slug)
      )
      val hints = c.hints(m.id).filter(_.effect.exists(_.action == HintEffect.Prioritise))
      val added = picks.flatMap { p =>
        val prio = boosted(c, p.target, p.priority, hints)
        val boost = if prio != p.priority then s", priority $prio by a hint" else ""
        stale
          .find((slug, _, item) => p.kind == ArticleReview && slug == p.target && item.isDefined)
          .flatMap(_._3) match
          case Some(itemId) =>
            Option.when(c.restale(itemId, prio, p.why))(s"${p.target} (${p.why}$boost)")
          case None =>
            val (_, isNew) = c.upsert(m.id, p.kind, p.target, ItemOrigin.Selection, prio, reopen = false)
            val label = if p.kind == ArticleReview then p.target else s"${p.target} [${p.kind}]"
            Option.when(isNew)(s"$label (${p.why}$boost)")
      }
      if added.nonEmpty then
        c.event(
          m.id,
          None,
          None,
          Feed.Evaluated,
          s"selected ${Plural.count(added.size, "item")}: ${added.mkString(", ")}".take(2000)
        )
        out += s"${m.id}: selected ${added.size}"
    extensions(c, m, now, out)

  /** New sources reaching the articles they belong to (the autopilot): at most every `stale-scan-minutes`, an article
    * whose filter returns at least `extendMinNew` sources it neither cites nor gives a reason for — and that was not
    * extended in `extend-cooldown-days` — gets an `article-extend` item; the extension's review follows as a chained
    * item. `extendMinNew` 0 = never.
    */
  private def extensions(c: ManagerContext, m: ManagerInfo, now: Instant, out: Builder[String, List[String]]): Unit =
    val mcfg = cfg.managers
    val min = m.config.extendMinNew.getOrElse(mcfg.review.extendMinNew)
    val last = Option(lastExtend.get(m.id)).map(_.longValue)
    val every = math.max(1, mcfg.review.staleScanMinutes).toLong * 60000L
    if min > 0 && last.forall(l => now.toEpochMilli - l >= every) then
      lastExtend.put(m.id, now.toEpochMilli)
      val s = c.settings(m)
      val tags = m.config.includeTags.getOrElse(Nil)
      val goal = m.config.includeGoal.filter(_.trim.nonEmpty)
      val due = c
        .extendCandidates(m.id, tags, goal, math.max(1, s.refillBatch) * ManagerSupervisor.StaleLookahead)
        .filter((slug, _) => c.unsettledOn(slug).isEmpty)
        .flatMap { (slug, at) =>
          site.articles
            .newFilterSources(slug)
            .flatMap(n =>
              AutopilotPolicy
                .extendDue(n, min, at.flatMap(ManagerPolicy.parseInstant), mcfg.review.extendCooldownDays, now)
            )
            .map(slug -> _)
        }
        .take(math.max(1, s.refillBatch))
      val added = due.flatMap { (slug, why) =>
        val (id, isNew) = c.upsert(m.id, ArticleExtend, slug, ItemOrigin.Selection, 0, reopen = true)
        val item = c.item(id)
        Option.when(isNew || item.exists(_.status == ItemStatus.Pending.wire))(s"$slug ($why)")
      }
      if added.nonEmpty then
        c.event(m.id, None, None, Feed.Evaluated, s"to extend with new sources: ${added.mkString(", ")}".take(2000))
        out += s"${m.id}: ${added.size} to extend"

  /** Converged articles that are due again (at most every `stale-scan-minutes` per manager): (slug, why, its done
    * item). Their filter is counted only for the ones whose count was recorded.
    */
  private def staleArticles(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      tags: List[String],
      goal: Option[String],
      room: Int,
      now: Instant
  ): List[(String, String, Option[Long])] =
    val last = Option(lastStale.get(m.id)).map(_.longValue).getOrElse(Long.MinValue)
    val every = math.max(1, cfg.managers.review.staleScanMinutes).toLong * 60000L
    if s.staleAfterDays <= 0 || (last != Long.MinValue && now.toEpochMilli - last < every) then Nil
    else
      lastStale.put(m.id, now.toEpochMilli)
      c.staleCandidates(m.id, tags, goal, room * ManagerSupervisor.StaleLookahead)
        .flatMap { (slug, at, hits, item) =>
          ManagerPolicy
            .parseInstant(at)
            .flatMap(t =>
              ReviewManagerPolicy
                .staleWhy(t, now, s.staleAfterDays, hits, hits.flatMap(_ => site.articles.filterTotal(slug)))
            )
            .map(why => (slug, why, item))
        }
        .take(room)

  /** A target's priority under the standing prioritise hints that match it. */
  private def boosted(c: ManagerContext, slug: String, base: Int, hints: List[ManagerHint]): Int =
    if hints.isEmpty then base
    else
      val (tags, goals) = c.articleFacets(slug)
      hints
        .flatMap(_.effect)
        .filter(e => ManagerPolicy.matches(e, slug, tags, Nil, goals))
        .map(_.priority.getOrElse(ManagerSupervisor.HintPriority))
        .foldLeft(base)(math.max)

  // ── what a person adds ─────────────────────────────────────────────────────────────────────────

  def target(m: ManagerInfo, itemKind: String, raw: String): Either[Api.Error, String] =
    itemKind match
      case ArticleReview | InferFilter | ArticleExtend => site.articles.get(raw.trim).map(_.article.slug)
      case other                                       => Left(Api.Error(s"unknown item kind '$other'"))

  /** An article that records no filter and never had an infer-filter item gets one, at its review's priority, so its
    * review waits for it (when the manager's `inferFilter` is on).
    */
  override def added(c: ManagerContext, m: ManagerInfo, itemKind: String, slug: String, priority: Int): Unit =
    if itemKind == ArticleReview then
      val on = m.config.inferFilter.getOrElse(cfg.managers.review.inferFilter)
      val has = site.articles.recordedFilter(slug).isDefined
      if on && !has && !c.hasItem(m.id, InferFilter, slug) then
        val (fid, _) = c.upsert(m.id, InferFilter, slug, ItemOrigin.Chain, priority, reopen = false)
        c.event(
          m.id,
          Some(fid),
          None,
          Feed.Evaluated,
          s"$slug ($InferFilter): it records no coverage filter — inferred before its review"
        )

  // ── dispatch ───────────────────────────────────────────────────────────────────────────────────

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[String, List[String]]
  ): Option[String] =
    item.kind match
      case ArticleReview =>
        if ManagerPolicy.waitsFor(item.kind, item.target, c.openKeys(m.id)) then None
        else
          site.articles.get(item.target) match
            case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
            case Right(_) =>
              val before = ledgers.get(item.target).toOption.flatten.map(_.openMajors.size).getOrElse(0)
              val pairs = site.articles.openCritiques(item.target)
              val rounds = math.min(s.rounds, cfg.review.roundsCap)
              c.start(
                m,
                item,
                RunRequest(
                  Pipeline.ReviewArticle.wire,
                  item.target,
                  budgetUsd = m.config.budgetUsd,
                  fix = Some(true),
                  rounds = Some(rounds)
                ),
                identity,
                item.state.copy(before = Some(before), pairsBefore = pairs),
                s"review-article fix, $rounds rounds; $before open MAJOR(s)${pairs
                    .fold("")(n => s", $n open critique pair(s)")}",
                out
              )
      case InferFilter =>
        site.articles.get(item.target) match
          case Left(_) => c.settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
          case Right(v) if site.articles.recordedFilter(v.article.slug).isDefined =>
            c.settleNow(m, item, ItemStatus.Done, "the article records a filter already")
          case Right(_) =>
            c.start(
              m,
              item,
              RunRequest(Pipeline.InferFilter.wire, item.target, budgetUsd = m.config.budgetUsd),
              identity,
              item.state,
              "infer-filter",
              out
            )
      case ArticleExtend =>
        site.articles.get(item.target) match
          case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
          case Right(_) =>
            c.start(
              m,
              item,
              RunRequest(Pipeline.CompileArticle.wire, item.target, budgetUsd = m.config.budgetUsd),
              _.copy(chainReview = Some(false)),
              item.state,
              "compile-article (extend; its review follows as an item)",
              out
            )
      case other =>
        c.settleNow(m, item, ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")

object ReviewManagerKind:

  /** A review manager's defaults as research's policy reads them; the scan timings stay with the kind. */
  def defaultsOf(c: ReviewManagerConfig): ReviewManagerDefaults = c.transformInto[ReviewManagerDefaults]
