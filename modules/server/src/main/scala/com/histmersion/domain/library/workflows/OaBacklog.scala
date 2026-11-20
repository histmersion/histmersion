package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.managers.{ManagerDefaultsViews, ManagerLight}
import com.histmersion.api.automation.Goal.Plans.GoalLink
import com.histmersion.domain.automation.managers.SweepAcquireItem

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.domain.IngestBacklog.Entry
import com.histmersion.domain.IngestBacklog.Failure
import com.histmersion.domain.IngestBacklog.Lane
import com.histmersion.domain.IngestBacklog.Placement
import com.histmersion.domain.IngestBacklog.RepairAction
import com.histmersion.domain.IngestBacklog.RepairFacts
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.api.library.RepairAcquire.*
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.library.workflows.OaBacklog.Snapshot

import java.time.Instant
import com.histmersion.api.automation.Queue
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Doi
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Manager
import com.histmersion.api.library.WorkRef
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.api.automation.Managers
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused

/** The watchers' INGEST BACKLOG (docs/research-process-managers.md "As built: the ingest backlog"). A watcher with
  * auto-ingest on used to send every legal open-access pick past the day's allowance to the acquisition list — the
  * person's buy/borrow list; now such a pick waits here, as an `oa-ingest` item of the watcher that found it (one entry
  * per DOI however many goals found it), and the managers' tick ingests it on a later day through the legal chain,
  * within the same caps — the backlog before any new find. Only a work with no legal file reaches the acquisition list.
  *
  *   - [[add]]: a sweep parks a pick (deduplicated against the corpus, the acquisition list and every watcher's
  *     backlog);
  *   - [[drain]]: one tick's share of the backlog ingested;
  *   - [[reserved]], [[snapshot]]: what a sweep may still ingest, and the status lines' counts and days;
  *   - [[repair]]: the one-shot move of the acquisition items a sweep created for legal open-access works.
  */
final class OaBacklog(
    cfg: => AgentsConfig,
    lib: => LibraryAgentsConfig,
    site: LibrarySite,
    upstream: () => AgentUpstream,
    agents: AgentsControllers
):

  private def watch = lib.managers.watch
  private def managerDefaults = ManagerDefaultsViews.defaults(cfg.managers)
  private def watchDefaults = WatchManagerDefaults(
    WatchManagerDefaults.MaxRuns(watch.maxRuns.value),
    WatchManagerDefaults.AutoIngestPerDay(watch.autoIngestPerDay.value)
  )

  // ── adding ─────────────────────────────────────────────────────────────────────────────────────

  /** Park legal open-access pick `w` (found by goal `goal`'s watcher `managerId`, in run `run`): onto the open
    * acquisition item that names it (linked to the goal), into the open backlog entry any watcher holds for it (the
    * goal added), or a new entry of `managerId` — nothing when the corpus has it or this watcher settled an entry for
    * it before (a person skipped it).
    */
  def add(managerId: ManagerInfo.Id, goal: Goal.Slug, w: OaWork)(using WritePermit): Placement =
    val doi = Doi.assume(w.doi.value)
    val apiGoal = com.histmersion.api.automation.Goal.Slug(goal.value)
    val open = agents.managerStore.backlogOpenFor(doi)
    val settled = agents.managerStore
      .itemFor(managerId, ManagerItem.Kind(OaIngest), ManagerItem.Target(doi.value))
      .filter(i => ItemStatus.parse(i.status).exists(_.isFinal))
      .map(i => Placement.SettledBefore.Item(i.id.value))
    val acquire =
      site.queues.naming(Queue.Kind.Acquire, doi).headOption.map(i => Placement.JoinAcquire.Item(i.id.value))
    IngestBacklog.place(
      site.sourceStore.citekeyOfDoi(doi),
      acquire,
      open.map(i => Placement.JoinBacklog.Item(i.id.value)),
      settled
    ) match
      case p @ Placement.InCorpus(ck) =>
        agents.goals.linkIfThere(
          goal,
          io.scalaland.chimney.Transformer
            .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
            .transform(LinkKind.Source),
          GoalLink.Ref(ck.value),
          Some(GoalLink.Note(s"found again by a watcher sweep: ${w.why}".take(500)))
        )

        p
      case p @ Placement.JoinAcquire(id) =>
        agents.goals.linkIfThere(
          goal,
          io.scalaland.chimney.Transformer
            .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
            .transform(LinkKind.QueueItem),
          GoalLink.Ref(id.value.toString),
          Some(GoalLink.Note(doi.value))
        )
        p
      case p @ Placement.JoinBacklog(id) =>
        open.foreach { i =>
          val st = i.state.oa.getOrElse(OaWork(w.doi))
          if !st.goals.contains(apiGoal) then
            val joined = agents.managerStore.setItemState(
              i.id,
              i.state.modify(_.oa).setTo(Some(st.modify(_.goals).setTo(st.goals :+ apiGoal))),
              List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)
            )
            // settled meanwhile (ingested, failed): the goal is not added to it
            if !joined then scribe.info(s"$doi (ingest backlog): settled before goal $goal could join it")
            agents.managerStore.event(
              i.managerId,
              Some(i.id),
              None,
              ManagerEvent.Kind(Feed.Evaluated),
              ManagerEvent.Text(s"$doi (ingest backlog): found by goal $goal too ($managerId)")
            )

        }
        p
      case p @ Placement.SettledBefore(_) => p
      case Placement.New                  =>
        val st = ItemState(oa = Some(w.modify(_.goals).setTo((w.goals :+ apiGoal).distinct)))
        val (id, isNew) =
          agents.managerStore.upsert(
            managerId,
            ManagerItem.Kind(OaIngest),
            ManagerItem.Target(doi.value),
            ItemOrigin.Selection,
            ManagerItem.Priority(0),
            reopen = false,
            st
          )
        if isNew then
          agents.managerStore.event(
            managerId,
            Some(id),
            w.foundBy.map(r => Run.Id.assume(r.value)),
            ManagerEvent.Kind(Feed.Evaluated),
            ManagerEvent.Text(
              s"$doi (ingest backlog): ${w.title.value.take(200)} — legal open access, waits for a day's ingest allowance"
            )
          )

          Placement.New
        else Placement.SettledBefore(Placement.SettledBefore.Item(id.value))

  // ── what is left, what waits ───────────────────────────────────────────────────────────────────

  /** The watchers whose backlog drains (active, auto-ingest on) → what is left of their own allowance today. */
  private def ownLeft(now: Instant): Map[ManagerLight.Id, IngestBacklog.Lane.LeftToday] =
    val today = agents.managerStore
      .countEventsBy(ManagerEvent.Kind(Feed.Ingested), ManagerPolicy.dayStart(now))
      .map((k, v) => ManagerLight.Id(k.value) -> v.value)
    agents.managerStore
      .light()
      .filter(m =>
        m.kind.value == Kind.Watch.wire.value && m.state.value == State.Active.wire.value && m.config.autoIngest
          .contains(ManagerConfig.AutoIngest(true))
      )
      .map(m =>
        m.id -> IngestBacklog.Lane.LeftToday(
          watchDefaults.ingestAllowance(m.config, WatchManagerDefaults.IngestedToday(today.getOrElse(m.id, 0))).value
        )
      )
      .toMap

  /** What a sweep of watcher `managerId` reserves for the backlog: its own pending backlog items, and every pending
    * backlog item of a watcher that drains (the shared cap's).
    */
  def reserved(managerId: ManagerLight.Id): (IngestBacklog.Lane.Backlog, IngestBacklog.TotalBacklog) =
    val counts = agents.managerStore.backlogCounts().map((k, v) => ManagerLight.Id(k.value) -> (v._1.value, v._2.value))
    val draining = agents.managerStore
      .light()
      .filter(m =>
        m.kind.value == Kind.Watch.wire.value && m.state.value == State.Active.wire.value &&
          m.config.autoIngest.contains(ManagerConfig.AutoIngest(true))
      )
      .map(_.id)
      .toSet
    (
      IngestBacklog.Lane.Backlog(counts.get(managerId).fold(0)(_._1)),
      IngestBacklog.TotalBacklog(counts.collect { case (m, (p, _)) if draining(m) => p }.sum)
    )

  /** The backlog per watcher and in all, with the days to drain it ([[OaBacklog.Snapshot]]). */
  def snapshot(
      now: Instant = Instant.now(agents.clock),
      extra: Map[ManagerLight.Id, IngestBacklog.Lane.Backlog] = Map.empty
  ): Snapshot =
    val stored = agents.managerStore.backlogCounts().map((k, v) => ManagerLight.Id(k.value) -> (v._1.value, v._2.value))
    // `extra` = pending items a dry run would add, per watcher
    val counts = (stored.keySet ++ extra.keySet).map { id =>
      val more = extra.get(id).fold(0)(_.value)
      id -> stored.get(id).fold((more, 0))((p, hd) => (p + more, hd))
    }.toMap
    if counts.isEmpty then Snapshot(Map.empty, IngestBacklog.TotalBacklog(0), None)
    else
      val left = ownLeft(now)
      val light = agents.managerStore.light().map(m => m.id -> m).toMap
      val shared = agents.autopilot.sharedIngestLeft(now).map(s => IngestBacklog.Lane.LeftToday(s.value))
      val sharedCap = Option
        .when(shared.isDefined)(agents.autopilot.ingestCap.value)
        .filter(_ > 0)
        .map(IngestBacklog.Lane.PerDay(_))
      // a held item drains once released: counted in the backlog, not in the days (it waits for a person)
      def lane(id: ManagerLight.Id, pending: Int): Lane =
        val perDay = light
          .get(id)
          .filter(_ => left.contains(id))
          .map(m => math.max(0, m.config.autoIngestPerDay.map(_.value).getOrElse(watch.autoIngestPerDay.value)))
          .getOrElse(0)
        Lane(Lane.Backlog(pending), Lane.PerDay(perDay), left.getOrElse(id, Lane.LeftToday(0)))
      val per = counts.map { case (id, (p, held)) =>
        id -> (
          Lane.Backlog(p + held),
          Option
            .when(p > 0)(IngestBacklog.daysToDrain(List(lane(id, p)), sharedCap, shared))
            .getOrElse(Some(IngestBacklog.DaysToDrain(0)))
        )
      }
      val pendingAll = counts.toList.map { case (id, (p, _)) => lane(id, p) }
      Snapshot(
        per,
        IngestBacklog.TotalBacklog(counts.values.map((p, hd) => p + hd).sum),
        IngestBacklog.daysToDrain(pendingAll.filter(_.backlog.value > 0), sharedCap, shared)
      )

  // ── the drain (each managers' tick) ────────────────────────────────────────────────────────────

  /** One tick's share of the backlog: the due items of the active watchers with auto-ingest on, in order (priority,
    * then the oldest), within each one's own allowance today and the shared one, at most
    * `agents.managers.watch.drain-per-tick`. Each is ingested through the legal chain only and linked to every goal
    * that found it (an `ingested` event of its watcher: what the caps count); one the corpus has meanwhile is settled
    * without an ingest; one with no legal file goes to the acquisition list (linked to its goals); any other failure is
    * tried again after the backoff (the acquisition list after `max-failures`). One worklog line per tick that did
    * something.
    */
  def drain(now: Instant)(using WritePermit): List[String] =
    val pending = agents.managerStore.backlogPending()
    if pending.isEmpty then Nil
    else
      val left = ownLeft(now)
      val picks = IngestBacklog.drain(
        pending.map(i =>
          Entry(
            Entry.Id(i.id.value),
            Entry.ManagerId(i.managerId.value),
            Entry.Priority(i.priority.value),
            i.notBefore.flatMap(n => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(n.value)))
          )
        ),
        left.map((k, v) => Entry.ManagerId(k.value) -> v),
        agents.autopilot.sharedIngestLeft(now).map(s => IngestBacklog.SharedAllowance(s.value)),
        now,
        IngestBacklog.DrainLimit(watch.drainPerTick.value)
      )
      if picks.isEmpty then Nil
      else
        val byId = pending.map(i => i.id -> i).toMap
        val goalOf = agents.managerStore.light().map(m => m.id.value -> m.goal).toMap
        val results = picks
          .flatMap(e => byId.get(ManagerItem.Id(e.id.value)))
          .map(i => one(i, goalOf.get(i.managerId.value).flatten.map(g => Goal.Slug.assume(g.value)), now))
        val ingested = results.collect { case Drained.Ingested(ck) => ck }
        val queued = results.collect { case Drained.Queued(id) => id }
        val known = results.count(_ == Drained.Known)
        val retried = results.count(_ == Drained.Retried)
        val waiting = agents.managerStore.backlogCounts().values.map((p, hd) => p.value + hd.value).sum
        val line =
          s"ingest backlog: ${ingested.size} ingested (legal open access${ingested.map(c => s" [[$c]]").mkString})" +
            (if queued.nonEmpty then
               s", ${queued.size} to the acquisition list (no legal copy: ${queued.map(i => s"#$i").mkString(", ")})"
             else "") +
            (if known > 0 then s", $known in the corpus already" else "") +
            (if retried > 0 then s", $retried to try again later" else "") + s"; $waiting waiting"
        site.workLists.logLine(QueueAdd(Queue.QueueItem.Text(line.take(1000))))
        List(line)

  private enum Drained derives CanEqual:
    case Ingested(citekey: Citekey)
    case Queued(item: Queue.QueueItem.Id)
    case Known
    case Retried
    case Lost

  private def one(i: ManagerItem, holderGoal: Option[Goal.Slug], now: Instant)(using WritePermit): Drained =
    val doi = Doi.assume(i.target.value)
    val w = i.state.oa.getOrElse(OaWork(com.histmersion.api.library.Doi(doi.value)))
    val goals = (w.goals.map(g => Goal.Slug.assume(g.value)) ++ holderGoal.toList).distinct
    def settle(status: ItemStatus, outcome: String, kind: String, text: String, d: Drained)(using
        WritePermit
    ): Drained =
      if agents.managerStore.settle(i.id, status, ManagerItem.Outcome(outcome), List(ItemStatus.Pending)) then
        agents.managerStore.event(
          i.managerId,
          Some(i.id),
          None,
          ManagerEvent.Kind(kind),
          ManagerEvent.Text(text)
        )
        d
      else Drained.Lost
    site.sourceStore.citekeyOfDoi(doi) match
      case Some(ck) =>
        goals.foreach(g =>
          Unused(
            agents.goals.link(
              g,
              io.scalaland.chimney.Transformer
                .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
                .transform(LinkKind.Source),
              GoalLink.Ref(ck.value),
              Some(GoalLink.Note(s"found by a watcher sweep: ${w.why}".take(500)))
            ),
            Unused.Why.Idempotent
          )
        )
        settle(
          ItemStatus.Done,
          s"in the corpus already: [[$ck]]",
          Feed.Evaluated,
          s"$doi (ingest backlog): in the corpus already [[$ck]]",
          Drained.Known
        )
      case None =>
        upstream().ingestLegal(doi) match
          case Right(r) =>
            goals.foreach(g =>
              Unused(
                agents.goals.link(
                  g,
                  io.scalaland.chimney.Transformer
                    .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
                    .transform(LinkKind.Source),
                  GoalLink.Ref(r.citekey.value),
                  Some(GoalLink.Note(s"ingested from a watcher's backlog: ${w.why}".take(500)))
                ),
                Unused.Why.Idempotent
              )
            )
            settle(
              ItemStatus.Done,
              s"ingested [[${r.citekey}]]",
              Feed.Ingested,
              s"[[${r.citekey}]] ingested from the backlog (legal open access, ${r.pdf.fold("no file")(_.source.value)}): " +
                s"${if w.title.value.nonEmpty then w.title.value else doi.value} — https://doi.org/$doi",
              Drained.Ingested(Citekey.assume(r.citekey.value))
            )
          case Left(m) =>
            val s = settingsOf(i.managerId)
            IngestBacklog.onFailure(
              m,
              Failure.Retry.Failures(i.failures.value),
              s.maxFailures,
              s.backoffMinutes,
              s.maxBackoffMinutes,
              now
            ) match
              case Failure.Retry(nb, n, why) =>
                if agents.managerStore.backlogRetry(
                    i.id,
                    ManagerItem.Failures(n.value),
                    nb,
                    ManagerItem.Outcome(
                      s"not ingested (${n.value} of ${s.maxFailures}): ${why.said}; again after $nb"
                    )
                  )
                then
                  agents.managerStore.event(
                    i.managerId,
                    Some(i.id),
                    None,
                    ManagerEvent.Kind(Feed.Evaluated),
                    ManagerEvent.Text(
                      s"$doi (ingest backlog): not ingested (${n.value} of ${s.maxFailures}): ${why.said}; again after $nb"
                    )
                  )

                  Drained.Retried
                else Drained.Lost
              case Failure.ToAcquire(why) =>
                acquireOnce(goals, doi, w) match
                  case Some(id) =>
                    settle(
                      ItemStatus.Stopped,
                      s"to the acquisition list (#$id): ${why.said}",
                      Feed.Evaluated,
                      s"$doi (ingest backlog): to the acquisition list (#$id): ${why.said}",
                      Drained.Queued(id)
                    )
                  case None => Drained.Lost

  private def settingsOf(managerId: ManagerInfo.Id): ManagerPolicy.ReviewSettings =
    val c = agents.managerStore.get(managerId).map(_.config).getOrElse(ManagerConfig())
    ManagerPolicy.ReviewSettings.forKind(Manager.Kind.Watch, c, managerDefaults)

  /** The acquisition item for `doi` (an existing one that names it, else a new one in the sweep's wording), linked to
    * every goal. None when the list refused it.
    */
  private def acquireOnce(goals: List[Goal.Slug], doi: Doi, w: OaWork)(using WritePermit): Option[Queue.QueueItem.Id] =
    val existing = site.queues.naming(Queue.Kind.Acquire, doi).headOption.map(_.id)
    val id = existing.orElse {
      val goal = goals.headOption
      val text = w.acquireText
        .map(t => Queue.QueueItem.Text(t.value))
        .orElse(
          goal.map(g =>
            Queue.QueueItem.Text(
              AcquisitionSweepRules
                .acquireText(
                  AcquisitionSweepRules.Candidate(
                    doi,
                    if w.title.value.nonEmpty then w.title else com.histmersion.api.library.Title(doi.value),
                    w.year,
                    w.authors,
                    w.venue,
                    isOa = WorkRef.IsOa(true),
                    AcquisitionSweepRules.Candidate.Query(""),
                    None
                  ),
                  g,
                  AcquisitionSweepRules.Rationale(s"${w.why}")
                )
                .value
            )
          )
        )
      text.flatMap(t =>
        site.workLists
          .add(
            WorkListControllers.ListName(Queue.Kind.Acquire.label.value),
            QueueAdd(
              t,
              topic = goal.map(g => Queue.QueueItem.Topic(s"goal $g")),
              goal = goal.map(g => com.histmersion.api.automation.Goal.Slug(g.value))
            )
          )
          .toOption
          .map(_.id)
      )
    }
    id.foreach(i =>
      goals.foreach(g =>
        Unused(
          agents.goals.link(
            g,
            io.scalaland.chimney.Transformer
              .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
              .transform(LinkKind.QueueItem),
            GoalLink.Ref(i.value.toString),
            Some(GoalLink.Note(doi.value))
          ),
          Unused.Why.Idempotent
        )
      )
    )
    id

  // ── the one-shot repair ────────────────────────────────────────────────────────────────────────

  /** `agent autopilot repair-acquire`: every OPEN acquisition item a watcher's sweep created
    * ([[ManagerService.sweepAcquireItems]] — found by structure: the sweep run that picked the DOI, the step that
    * queued it, the goal link it made) for a work its ranking marked open access moves into that watcher's ingest
    * backlog and leaves the list (with its goal links); one the corpus has meanwhile just leaves the list; the others
    * stay. Nothing a person added is touched. `dryRun` changes nothing. Each moved item is a manager event; the whole a
    * worklog line.
    */
  def repair(in: RepairRequest)(using WritePermit): RepairResult =
    val dryRun = in.dryRun.value
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    val rows = agents.managerStore.sweepAcquireItems()
    val light = agents.managerStore.light().map(m => m.id.value -> m).toMap
    val items = rows.groupBy(_.item).toList.sortBy[Int](_._1.value)
    val out = items.map { (item, rs) =>
      val first = rs.find(_.isOa.value).getOrElse(rs.head)
      val goals = rs.map(_.goal).distinct.map(g => Goal.Slug.assume(g.value))
      val w = light.get(first.managerId.value)
      val watcher = ManagerInfo.Id(first.managerId.value)
      val apiGoals = goals.map(g => com.histmersion.api.automation.Goal.Slug(g.value))
      val facts = RepairFacts(
        RepairFacts.Item(item.value),
        first.doi,
        RepairFacts.IsOa(rs.exists(_.isOa.value)),
        RepairFacts.Watcher(first.managerId.value),
        RepairFacts.WatcherExists(w.isDefined),
        RepairFacts.WatcherAutoIngest(
          w.exists(m =>
            m.state.value != State.Stopped.wire.value && m.config.autoIngest.contains(ManagerConfig.AutoIngest(true))
          )
        ),
        site.sourceStore.citekeyOfDoi(first.doi)
      )
      def row(action: String, detail: String, backlogItem: Option[ManagerItem.Id] = None) =
        RepairRow(
          RepairRow.Item(item.value),
          com.histmersion.api.library.Doi(first.doi.value),
          goals.map(g => RepairRow.Goals(g.value)),
          RepairRow.Watcher(first.managerId.value),
          RepairRow.Action(action),
          RepairRow.Detail(detail),
          backlogItem.map(i => RepairRow.BacklogItem(i.value))
        )
      IngestBacklog.repairAction(facts) match
        case RepairAction.Keep(why)  => row(Keep, why.said)
        case RepairAction.Remove(ck) =>
          if !dryRun then
            goals.foreach(g =>
              Unused(
                agents.goals.link(
                  g,
                  io.scalaland.chimney.Transformer
                    .derive[com.histmersion.api.automation.Goal.LinkKind, Goal.LinkKind]
                    .transform(LinkKind.Source),
                  GoalLink.Ref(ck.value),
                  Some(GoalLink.Note("found by a watcher sweep"))
                ),
                Unused.Why.Idempotent
              )
            )
            removeAcquire(item, goals)
          row(Remove, s"in the corpus already: [[$ck]]")
        case RepairAction.Move =>
          val work = OaWork(
            com.histmersion.api.library.Doi(first.doi.value),
            first.title.fold(com.histmersion.api.library.Title(""))(t => com.histmersion.api.library.Title(t.value)),
            first.year.map(y => Year(y.value)),
            first.authors.map(a => com.histmersion.api.library.AuthorName(a.value)),
            first.venue.map(v => Source.Venue(v.value)),
            apiGoals,
            Managers.Rationale(first.why.fold("")(w => s"$w")),
            Some(OaWork.FoundBy(first.job.value)),
            Some(OaWork.AcquireText(first.text.value))
          )
          if dryRun then row(Move, "legal open access")
          else
            addMoved(watcher, work) match
              case None =>
                row(Keep, "its watcher settled a backlog entry for this work before (a person skipped it)")
              case Some(id) =>
                goals.foreach(g => joinGoal(id, g))
                removeAcquire(item, goals)
                agents.managerStore.event(
                  watcher,
                  Some(id),
                  Some(Run.Id.assume(first.job.value)),
                  ManagerEvent.Kind(Feed.Evaluated),
                  ManagerEvent.Text(
                    s"${first.doi} (ingest backlog): moved from acquisition item #$item by repair-acquire ($by)"
                  )
                )

                row(Move, "legal open access", Some(id))
    }
    val untouched =
      site.queues.list(Queue.Kind.Acquire, QueueScope.Open).size - (if dryRun then items.size
                                                                    else out.count(_.action.value == Keep))
    val snap = snapshot(extra =
      if dryRun then
        out
          .filter(_.action.value == Move)
          .groupMapReduce(r => ManagerLight.Id(r.watcher.value))(_ => 1)(_ + _)
          .map((k, v) => k -> IngestBacklog.Lane.Backlog(v))
      else Map.empty
    )
    val result = RepairResult(
      in.dryRun,
      RepairResult.Moved(out.count(_.action.value == Move)),
      RepairResult.Removed(out.count(_.action.value == Remove)),
      RepairResult.Kept(out.count(_.action.value == Keep)),
      RepairResult.Untouched(math.max(0, untouched)),
      out,
      ManagerInfo.Backlog(snap.total.value),
      snap.totalDays.map(d => ManagerInfo.BacklogDays(d.value))
    )
    if !dryRun && (result.moved.value > 0 || result.removed.value > 0) then
      site.workLists.logLine(
        QueueAdd(
          Queue.QueueItem.Text(
            (s"repair-acquire ($by): ${result.moved} legal open-access work(s) moved from the " +
              s"acquisition list into the watchers' ingest backlogs, ${result.removed} in the corpus already removed, ${result.kept} kept; " +
              s"${result.backlog} waiting in the backlog").take(1000)
          )
        )
      )
      agents.managers.nudge()
    result

  /** A moved work's backlog entry: an existing open one of any watcher, else a new one of `managerId`; None when
    * `managerId` settled one for it before (a person's skip is not undone).
    */
  private def addMoved(managerId: ManagerInfo.Id, w: OaWork)(using WritePermit): Option[ManagerItem.Id] =
    val doi = Doi.assume(w.doi.value)
    agents.managerStore.backlogOpenFor(doi).map(_.id).orElse {
      val (id, isNew) = agents.managerStore.upsert(
        managerId,
        ManagerItem.Kind(OaIngest),
        ManagerItem.Target(doi.value),
        ItemOrigin.Selection,
        ManagerItem.Priority(0),
        reopen = false,
        ItemState(oa = Some(w))
      )

      Option.when(isNew)(id)
    }

  private def joinGoal(itemId: ManagerItem.Id, goal: Goal.Slug)(using WritePermit): Unit =
    val apiGoal = com.histmersion.api.automation.Goal.Slug(goal.value)
    agents.managerStore.item(itemId).foreach { i =>
      val st = i.state.oa.getOrElse(OaWork(com.histmersion.api.library.Doi(i.target.value)))
      if !st.goals.contains(apiGoal)
        && !agents.managerStore.setItemState(
          i.id,
          i.state.modify(_.oa).setTo(Some(st.modify(_.goals).setTo(st.goals :+ apiGoal))),
          List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)
        )
      then scribe.info(s"backlog item $itemId settled before goal $goal could join it")
    }

  private def removeAcquire(item: SweepAcquireItem.Item, goals: List[Goal.Slug])(using WritePermit): Unit =
    site.workLists.removeItem(QueueItemId(item.value))
    goals.foreach(g =>
      Unused(agents.goals.unlink(g, Goal.LinkKind.QueueItem, GoalLink.Ref(item.value.toString)), Unused.Why.Idempotent)
    )

object OaBacklog:
  /** The backlog per watcher (pending + held, and the days its pending items take to drain at the current allowance),
    * and in all.
    */
  final case class Snapshot(
      perManager: Map[ManagerLight.Id, (IngestBacklog.Lane.Backlog, Option[IngestBacklog.DaysToDrain])],
      total: IngestBacklog.TotalBacklog,
      totalDays: Option[IngestBacklog.DaysToDrain]
  )
