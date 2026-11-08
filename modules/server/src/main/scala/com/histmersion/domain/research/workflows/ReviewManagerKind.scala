package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.managers.ManagerSupervisor
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.domain.automation.worklists.QueueScope

import com.histmersion.api.Plural
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueuePatch}
import com.histmersion.domain.*
import com.histmersion.domain.ManagerPolicy.Next
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts
import com.histmersion.domain.research.workflows.ReviewManagerPolicy.ReviewFacts
import com.histmersion.config.{AgentsConfig, ResearchAgentsConfig, ReviewManagerConfig}
import com.histmersion.domain.ManagerDefaults
import com.histmersion.domain.ReviewManagerDefaults
import io.scalaland.chimney.dsl.*

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.automation.Queue
import com.histmersion.api.Api
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.automation.goals.GoalRow
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.api.research.Articles
import com.histmersion.api.research.Article

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

  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = itemKind.value match
    case InferFilter   => Pipeline.InferFilter
    case ArticleExtend => Pipeline.CompileArticle
    case _             => Pipeline.ReviewArticle

  /** When each review manager last looked for stale articles (epoch ms); in memory — a restart looks again. */
  private val lastStale = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  /** When each review manager last looked for articles to extend (epoch ms); in memory. */
  private val lastExtend = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[ManagerKindDefinition.FirePhrase] = List(ManagerKindDefinition.FirePhrase("selection refilled"))

  // ── evaluate ───────────────────────────────────────────────────────────────────────────────────

  override def evaluate(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[ManagerContext.EvaluateStepError],
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit =
    if item.kind.value == ArticleReview then evaluateReview(c, m, s, item, st, error, now, out)
    else c.evaluateStep(m, s, item, st, error, now, out, this)

  private def evaluateReview(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[ManagerContext.EvaluateStepError],
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit =
    val run = Run.Id.assume(item.runId.get.value)
    val slug = Slug.assume(item.target.value)
    val itemId = ManagerContext.ItemId(item.id.value)
    val manager = ManagerLight.Id(m.id.value)
    val ledger = ledgers.get(slug).toOption.flatten
    val facts = Option.when(st == RunState.Done) {
      val pairs = site.articles.openCritiques(slug).map(p => ReviewFacts.OpenPairs(p.value))
      ledger
        .map(ReviewFacts.of(_, pairs))
        .getOrElse(
          ReviewFacts(
            ReviewFacts.OpenMajors(0),
            ReviewFacts.MaxReopened(0),
            ReviewFacts.Escalated(0),
            onlyMissingCritiques = ReviewFacts.OnlyMissingCritiques(false),
            pairs
          )
        )
    }
    val task = ledger.flatMap(ReviewLedger.taskText(_, run)).map(t => ManagerPolicy.ItemTask(t.value))
    ReviewManagerPolicy.evaluateReview(
      s,
      slug,
      ManagerPolicy.Attempts(item.attempts.value),
      Next.Retry.Failures(item.failures.value),
      item.state,
      run,
      st.transformInto[Run.State],
      error,
      facts,
      task,
      now
    ) match
      case Next.Done(outcome, state) =>
        // what its filter returns now: more later makes it stale (phase 2)
        val withHits = state.modify(_.filterHits).setTo(site.articles.filterTotal(slug).map(h => ItemState.FilterHits(h.value)))
        if c.evaluated(itemId, Some(run), ItemStatus.Done, None, None, withHits, Some(outcome)) then
          c.event(manager, Some(itemId), Some(run), ManagerContext.EventFeed(Feed.Evaluated), ManagerContext.EventText(s"${item.target}: $outcome"))
          out += ManagerContext.TickLine(s"${item.target}: done")
      case Next.Again(notBefore, state, note) =>
        if c.evaluated(itemId, Some(run), ItemStatus.Pending, notBefore, None, state, Some(ManagerPolicy.ItemOutcome(note.value))) then
          c.event(manager, Some(itemId), Some(run), ManagerContext.EventFeed(Feed.Evaluated), ManagerContext.EventText(s"${item.target}: $note"))
          out += ManagerContext.TickLine(s"${item.target}: again")
      case Next.Retry(notBefore, failures, note) => c.retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state)       =>
        val failed = st == RunState.Failed
        val status = if failed then ItemStatus.Failed else ItemStatus.Stopped
        val filed = task.flatMap(t => fileTask(slug, ledger, t))
        val text = outcome.value + filed.fold("")(id => s" (task #$id)")
        if c.evaluated(
            itemId,
            Some(run),
            status,
            None,
            Option.when(failed)(ManagerContext.FailureCount(item.failures.value + 1)),
            state,
            Some(ManagerPolicy.ItemOutcome(text))
          )
        then
          c.event(manager, Some(itemId), Some(run), ManagerContext.EventFeed(Feed.Stopped), ManagerContext.EventText(s"${item.target}: $text"))
          out += ManagerContext.TickLine(s"${item.target}: stopped")
      case Next.Wait(_, _) => () // a review leaves no approval to wait for

  /** The stop's backlog item: the article's own review task (the ledger's, while it is open) is rewritten with the
    * reason, else a new one is added. Returns its id.
    */
  private def fileTask(slug: Slug, ledger: Option[ReviewLedger.Ledger], text: ManagerPolicy.ItemTask): Option[Queue.QueueItem.Id] =
    val open = ledger
      .flatMap(_.task)
      .flatMap(i => site.queues.get(QueueItemId(i.value)))
      .filter(i => i.list == Queue.Kind.Tasks.label && i.status.value == Queue.Open)
      // the article's collapsed item the ledger lost track of (#934)
      .orElse(
        site.queues
          .list(Queue.Kind.Tasks, QueueScope.Open)
          .filter(i => ReviewLedger.articleTask(slug, ReviewLedger.TaskText(i.text.value)))
          .sortBy(_.id)
          .headOption
      )
    open match
      case Some(item) => site.workLists.patch(QueueItemId(item.id.value), QueuePatch(text = Some(Queue.QueueItem.Text(text.value)))).toOption.map(_.id)
      case None       =>
        site.workLists
          .add(
            WorkListControllers.ListName(Queue.Kind.Tasks.label.value),
            QueueAdd(Queue.QueueItem.Text(text.value), topic = Some(Queue.QueueItem.Topic("agent review")))
          )
          .toOption
          .map(_.id)

  def facts(c: ManagerContext, item: ManagerItem, run: Run.Id): StepFacts =
    lazy val progress = c.progress(run)
    item.kind.value match
      case InferFilter =>
        c.approvalFacts(
          run,
          Approval.Kind.ArticleFilter,
          ManagerPolicy.ItemOutcome(s"no filter proposed — $progress")
        )
      case ArticleExtend =>
        CompileArticlePipeline.written(c, run) match
          case Some(w) if w.written.value => StepFacts.Finished(ManagerPolicy.ItemOutcome(s"extended (${w.why})"))
          case Some(w)                    => StepFacts.Finished(ManagerPolicy.ItemOutcome(s"nothing appended: ${w.why}"))
          case None                       => StepFacts.Finished(ManagerPolicy.ItemOutcome(s"nothing to extend — $progress"))
      case _ => StepFacts.Finished(ManagerPolicy.ItemOutcome("enriched"))

  /** An extended article is reviewed again. */
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[Run.Id],
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit =
    if item.kind.value == ArticleExtend && run.flatMap(CompileArticlePipeline.written(c, _)).exists(_.written.value) then
      c.chainReview(
        ManagerLight.Id(m.id.value),
        Slug.assume(item.target.value),
        ManagerContext.ChainReviewWhy(s"extended by run ${run.fold("")(_.value.take(8))}"),
        out
      )

  // ── the selection ──────────────────────────────────────────────────────────────────────────────

  /** The saved include-tags and include-goal of a manager as the selection queries take them; None when the goal it
    * names is no goal slug (nothing can match it).
    */
  private def scopeOf(m: ManagerInfo): Option[(List[GoalRow.Tags], Option[Goal.Slug])] =
    val tags = m.config.includeTags.getOrElse(Nil).map(t => GoalRow.Tags(t.value))
    m.config.includeGoal.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Some((tags, None))
      case Some(g) => Goal.Slug.option(g).map(slug => (tags, Some(slug)))

  /** Below the low water: the selection — articles with open MAJORs (most first), never-reviewed ones, then stale ones
    * — with an infer-filter item before the review of an article that records no filter, and the standing prioritise
    * hints applied. Then the articles new sources reach.
    */
  override def refill(
      c: ManagerContext,
      m: ManagerInfo,
      now: Instant,
      force: ManagerKindDefinition.RefillForce,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit =
    val s = c.settings(m)
    val manager = ManagerLight.Id(m.id.value)
    val pending = c.pending(manager)
    if (force.value || pending.value < s.lowWater.value) && s.refillBatch.value > 0 then
      val (tags, goal) = scopeOf(m).getOrElse((Nil, None))
      val selectable = scopeOf(m).isDefined
      val majors =
        if selectable then c.openMajorArticles(manager, tags, goal, ManagerContext.Limit(s.refillBatch.value)) else Nil
      val fresh = if majors.size >= s.refillBatch.value || !selectable then Nil
      else c.unreviewedArticles(manager, tags, goal, ManagerContext.Limit(s.refillBatch.value - majors.size))
      val room = s.refillBatch.value - majors.size - fresh.size
      val stale = if room > 0 && selectable then staleArticles(c, m, s, tags, goal, room, now) else Nil
      val picks = ReviewManagerPolicy.select(
        majors.map((slug, n) => (slug, ReviewFacts.OpenMajors(n.value))),
        fresh,
        stale.map((slug, why, _) => (slug, why)),
        s.refillBatch,
        s.inferFilter,
        slug => site.articles.recordedFilter(slug).isDefined,
        slug => c.hasItem(manager, ManagerPolicy.ItemKind.InferFilter, ManagerContext.ItemTarget(slug.value))
      )
      val hints = c.hints(manager).filter(_.effect.exists(_.action.value == HintEffect.Prioritise))
      val added = picks.flatMap { p =>
        val prio = boosted(c, p.target, p.priority, hints)
        val boost = if prio != p.priority then s", priority $prio by a hint" else ""
        stale
          .find((slug, _, item) => p.kind == ManagerPolicy.ItemKind.ArticleReview && slug == p.target && item.isDefined)
          .flatMap(_._3) match
          case Some(itemId) =>
            Option.when(c.restale(itemId, prio, ManagerRepo.RestaleReason(p.why.value)))(s"${p.target} (${p.why}$boost)")
          case None =>
            val (_, isNew) = c.upsert(manager, p.kind, ManagerContext.ItemTarget(p.target.value), ItemOrigin.Selection, prio, reopen = ManagerContext.UpsertReopen(false))
            val label = if p.kind == ManagerPolicy.ItemKind.ArticleReview then p.target.value else s"${p.target} [${p.kind}]"
            Option.when(isNew)(s"$label (${p.why}$boost)")
      }
      if added.nonEmpty then
        c.event(
          manager,
          None,
          None,
          ManagerContext.EventFeed(Feed.Evaluated),
          ManagerContext.EventText(s"selected ${Plural.count(Plural.CountN(added.size.toLong), Plural.CountSingular("item"))}: ${added.mkString(", ")}".take(2000))
        )
        out += ManagerContext.TickLine(s"${m.id}: selected ${added.size}")
    extensions(c, m, now, out)

  /** New sources reaching the articles they belong to (the autopilot): at most every `stale-scan-minutes`, an article
    * whose filter returns at least `extendMinNew` sources it neither cites nor gives a reason for — and that was not
    * extended in `extend-cooldown-days` — gets an `article-extend` item; the extension's review follows as a chained
    * item. `extendMinNew` 0 = never.
    */
  private def extensions(c: ManagerContext, m: ManagerInfo, now: Instant, out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]): Unit =
    val mcfg = cfg.managers
    val min = m.config.extendMinNew.fold(mcfg.review.extendMinNew.value)(_.value)
    val last = Option(lastExtend.get(m.id.value)).map(_.longValue)
    val every = math.max(1, mcfg.review.staleScanMinutes.value).toLong * 60000L
    if min > 0 && last.forall(l => now.toEpochMilli - l >= every) then
      lastExtend.put(m.id.value, now.toEpochMilli)
      val manager = ManagerLight.Id(m.id.value)
      val s = c.settings(m)
      val due = scopeOf(m).toList.flatMap { (tags, goal) =>
        c.extendCandidates(manager, tags, goal, ManagerContext.Limit(math.max(1, s.refillBatch.value) * ManagerSupervisor.StaleLookahead))
          .filter((slug, _) => c.unsettledOn(ManagerContext.ItemTarget(slug.value)).isEmpty)
          .flatMap { (slug, at) =>
            site.articles
              .newFilterSources(slug)
              .flatMap(n =>
                AutopilotPolicy
                  .extendDue(
                    AutopilotPolicy.NewSources(n.value),
                    AutopilotPolicy.Settings.ExtendMinNew(min),
                    at.flatMap(a => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(a.value))),
                    AutopilotPolicy.CooldownDays(mcfg.review.extendCooldownDays.value),
                    now
                  )
              )
              .map(slug -> _)
          }
      }.take(math.max(1, s.refillBatch.value))
      val added = due.flatMap { (slug, why) =>
        val (id, isNew) = c.upsert(manager, ManagerContext.ItemKind(ArticleExtend), ManagerContext.ItemTarget(slug.value), ItemOrigin.Selection, ManagerContext.Priority(0), reopen = ManagerContext.UpsertReopen(true))
        val item = c.item(id)
        Option.when(isNew || item.exists(_.status == ItemStatus.Pending.wire))(s"$slug ($why)")
      }
      if added.nonEmpty then
        c.event(manager, None, None, ManagerContext.EventFeed(Feed.Evaluated), ManagerContext.EventText(s"to extend with new sources: ${added.mkString(", ")}".take(2000)))
        out += ManagerContext.TickLine(s"${m.id}: ${added.size} to extend")

  /** Converged articles that are due again (at most every `stale-scan-minutes` per manager): (slug, why, its done
    * item). Their filter is counted only for the ones whose count was recorded.
    */
  private def staleArticles(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      tags: List[GoalRow.Tags],
      goal: Option[Goal.Slug],
      room: Int,
      now: Instant
  ): List[(Slug, ReviewManagerPolicy.Pick.Why, Option[ManagerContext.ItemId])] =
    val last = Option(lastStale.get(m.id.value)).map(_.longValue).getOrElse(Long.MinValue)
    val every = math.max(1, cfg.managers.review.staleScanMinutes.value).toLong * 60000L
    if s.staleAfterDays.value <= 0 || (last != Long.MinValue && now.toEpochMilli - last < every) then Nil
    else
      lastStale.put(m.id.value, now.toEpochMilli)
      c.staleCandidates(ManagerLight.Id(m.id.value), tags, goal, ManagerContext.Limit(room * ManagerSupervisor.StaleLookahead))
        .flatMap { (slug, at, hits, item) =>
          ManagerPolicy
            .parseInstant(ManagerPolicy.InstantText(at.value))
            .flatMap(t =>
              ReviewManagerPolicy
                .staleWhy(
                  t,
                  now,
                  s.staleAfterDays,
                  hits.map(h => ReviewManagerPolicy.FilterHits(h.value)),
                  hits.flatMap(_ => site.articles.filterTotal(slug))
                )
            )
            .map(why => (slug, why, item))
        }
        .take(room)

  /** A target's priority under the standing prioritise hints that match it. */
  private def boosted(c: ManagerContext, slug: Slug, base: ManagerContext.Priority, hints: List[ManagerHint]): ManagerContext.Priority =
    if hints.isEmpty then base
    else
      val (tags, goals) = c.articleFacets(slug)
      ManagerContext.Priority(
        hints
          .flatMap(_.effect)
          .filter(e => ManagerPolicy.matches(e, ManagerPolicy.Candidate.Target(slug.value), tags.map(t => ManagerPolicy.Tag(t.value)), Nil, goals))
          .map(_.priority.fold(ManagerSupervisor.HintPriority)(_.value))
          .foldLeft(base.value)(math.max)
      )

  // ── what a person adds ─────────────────────────────────────────────────────────────────────────

  def target(m: ManagerInfo, itemKind: ManagerContext.ItemKind, raw: ManagerItem.Target): Either[Api.Error, ManagerContext.ItemTarget] =
    itemKind.value match
      case ArticleReview | InferFilter | ArticleExtend =>
        Slug
          .parse(raw.value.trim)
          .left
          .map(_ => Articles.notFound(Article.Slug(raw.value.trim)))
          .flatMap(site.articles.get(_))
          .map(v => ManagerContext.ItemTarget(v.article.slug.value))
      case other => Left(Api.Error(Api.Error.Message(s"unknown item kind '$other'")))

  /** An article that records no filter and never had an infer-filter item gets one, at its review's priority, so its
    * review waits for it (when the manager's `inferFilter` is on).
    */
  override def added(c: ManagerContext, m: ManagerInfo, itemKind: ManagerContext.ItemKind, target: ManagerContext.ItemTarget, priority: ManagerContext.Priority): Unit =
    if itemKind.value == ArticleReview then
      val on = m.config.inferFilter.fold(cfg.managers.review.inferFilter.value)(_.value)
      val has = site.articles.recordedFilter(Slug.assume(target.value)).isDefined
      val manager = ManagerLight.Id(m.id.value)
      if on && !has && !c.hasItem(manager, ManagerPolicy.ItemKind.InferFilter, target) then
        val (fid, _) = c.upsert(manager, ManagerPolicy.ItemKind.InferFilter, target, ItemOrigin.Chain, priority, reopen = ManagerContext.UpsertReopen(false))
        c.event(
          manager,
          Some(fid),
          None,
          ManagerContext.EventFeed(Feed.Evaluated),
          ManagerContext.EventText(s"$target ($InferFilter): it records no coverage filter — inferred before its review")
        )

  // ── dispatch ───────────────────────────────────────────────────────────────────────────────────

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Option[ManagerLight.Id] =
    val slug = Slug.assume(item.target.value)
    val manager = ManagerLight.Id(m.id.value)
    item.kind.value match
      case ArticleReview =>
        if ManagerPolicy.waitsFor(ManagerPolicy.ItemKind.ArticleReview, ManagerPolicy.Candidate.Target(item.target.value), c.openKeys(manager)) then None
        else
          site.articles.get(slug) match
            case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the article no longer exists"))
            case Right(_) =>
              val before = ledgers.get(slug).toOption.flatten.map(_.openMajors.size).getOrElse(0)
              val pairs = site.articles.openCritiques(slug)
              val rounds = math.min(s.rounds.value, cfg.review.roundsCap.value)
              c.start(
                m,
                item,
                RunRequest(
                  RunRequest.Pipeline(Pipeline.ReviewArticle.wire.value),
                  RunRequest.Target(item.target.value),
                  budgetUsd = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value)),
                  fix = Some(RunRequest.Fix(true)),
                  rounds = Some(RunRequest.Rounds(rounds))
                ),
                identity,
                item.state.modify(_.before).setTo(Some(ItemState.Before(before))).modify(_.pairsBefore).setTo(pairs.map(p => ItemState.PairsBefore(p.value))),
                ManagerContext.StartWhat(
                  s"review-article fix, $rounds rounds; $before open MAJOR(s)${pairs
                      .fold("")(n => s", $n open critique pair(s)")}"
                ),
                out
              )
      case InferFilter =>
        site.articles.get(slug) match
          case Left(_) => c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the article no longer exists"))
          case Right(v) if site.articles.recordedFilter(Slug.assume(v.article.slug.value)).isDefined =>
            c.settleNow(m, item, ItemStatus.Done, ManagerContext.SettleNowWhy("the article records a filter already"))
          case Right(_) =>
            c.start(
              m,
              item,
              RunRequest(
                RunRequest.Pipeline(Pipeline.InferFilter.wire.value),
                RunRequest.Target(item.target.value),
                budgetUsd = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value))
              ),
              identity,
              item.state,
              ManagerContext.StartWhat("infer-filter"),
              out
            )
      case ArticleExtend =>
        site.articles.get(slug) match
          case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the article no longer exists"))
          case Right(_) =>
            c.start(
              m,
              item,
              RunRequest(
                RunRequest.Pipeline(Pipeline.CompileArticle.wire.value),
                RunRequest.Target(item.target.value),
                budgetUsd = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value))
              ),
              _.modify(_.chainReview).setTo(Some(RunParams.ChainReview(false))),
              item.state,
              ManagerContext.StartWhat("compile-article (extend; its review follows as an item)"),
              out
            )
      case other =>
        c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy(s"'$other' items are not driven by a ${m.kind} manager"))

object ReviewManagerKind:
  /** A review manager's defaults as research's policy reads them; the scan timings stay with the kind. */
  def defaultsOf(c: ReviewManagerConfig): ReviewManagerDefaults = c.transformInto[ReviewManagerDefaults]
