package com.histmersion.domain.library.workflows

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.managers.ManagerDefaultsViews

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
  private def watchDefaults = WatchManagerDefaults(watch.maxRuns, watch.autoIngestPerDay)

  // ── adding ─────────────────────────────────────────────────────────────────────────────────────

  /** Park legal open-access pick `w` (found by goal `goal`'s watcher `managerId`, in run `run`): onto the open
    * acquisition item that names it (linked to the goal), into the open backlog entry any watcher holds for it (the
    * goal added), or a new entry of `managerId` — nothing when the corpus has it or this watcher settled an entry for
    * it before (a person skipped it).
    */
  def add(managerId: String, goal: String, w: OaWork): Placement =
    val doi = w.doi
    val open = agents.managerStore.backlogOpenFor(doi)
    val settled = agents.managerStore
      .itemFor(managerId, OaIngest, doi)
      .filter(i => ItemStatus.parse(i.status).exists(_.isFinal))
      .map(_.id)
    val acquire = site.queues.naming(Queue.Kind.Acquire, doi).headOption.map(_.id)
    IngestBacklog.place(site.sourceStore.citekeyOfDoi(doi), acquire, open.map(_.id), settled) match
      case p @ Placement.InCorpus(ck) =>
        val _ =
          agents.goals.link(
            goal,
            io.scalaland.chimney.Transformer
              .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
              .transform(LinkKind.Source),
            ck,
            Some(s"found again by a watcher sweep: ${w.why}".take(500))
          )

        p
      case p @ Placement.JoinAcquire(id) =>
        val _ = agents.goals.link(
          goal,
          io.scalaland.chimney.Transformer
            .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
            .transform(LinkKind.QueueItem),
          id.toString,
          Some(doi)
        )
        p
      case p @ Placement.JoinBacklog(id) =>
        open.foreach { i =>
          val st = i.state.oa.getOrElse(OaWork(doi))
          if !st.goals.contains(goal) then
            val _ = agents.managerStore.setItemState(
              i.id,
              i.state.modify(_.oa).setTo(Some(st.modify(_.goals).setTo(st.goals :+ goal))),
              List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)
            )
            agents.managerStore.event(
              i.managerId,
              Some(i.id),
              None,
              Feed.Evaluated,
              s"$doi (ingest backlog): found by goal $goal too ($managerId)"
            )

        }
        p
      case p @ Placement.SettledBefore(_) => p
      case Placement.New                  =>
        val st = ItemState(oa = Some(w.modify(_.goals).setTo((w.goals :+ goal).distinct)))
        val (id, isNew) =
          agents.managerStore.upsert(managerId, OaIngest, doi, ItemOrigin.Selection, 0, reopen = false, st)
        if isNew then
          agents.managerStore.event(
            managerId,
            Some(id),
            w.foundBy,
            Feed.Evaluated,
            s"$doi (ingest backlog): ${w.title.take(200)} — legal open access, waits for a day's ingest allowance"
          )

          Placement.New
        else Placement.SettledBefore(id)

  // ── what is left, what waits ───────────────────────────────────────────────────────────────────

  /** The watchers whose backlog drains (active, auto-ingest on) → what is left of their own allowance today. */
  private def ownLeft(now: Instant): Map[String, Int] =
    val today = agents.managerStore.countEventsBy(Feed.Ingested, ManagerPolicy.dayStart(now))
    agents.managerStore
      .light()
      .filter(m => m.kind == Kind.Watch.wire && m.state == State.Active.wire && m.config.autoIngest.contains(true))
      .map(m => m.id -> watchDefaults.ingestAllowance(m.config, today.getOrElse(m.id, 0)))
      .toMap

  /** What a sweep of watcher `managerId` reserves for the backlog: its own pending backlog items, and every pending
    * backlog item of a watcher that drains (the shared cap's).
    */
  def reserved(managerId: String): (Int, Int) =
    val counts = agents.managerStore.backlogCounts()
    val draining = agents.managerStore
      .light()
      .filter(m =>
        m.kind == Kind.Watch.wire && m.state == State.Active.wire &&
          m.config.autoIngest.contains(true)
      )
      .map(_.id)
      .toSet
    (counts.get(managerId).fold(0)(_._1), counts.collect { case (m, (p, _)) if draining(m) => p }.sum)

  /** The backlog per watcher and in all, with the days to drain it ([[OaBacklog.Snapshot]]). */
  def snapshot(now: Instant = Instant.now(agents.clock), extra: Map[String, Int] = Map.empty): Snapshot =
    val stored = agents.managerStore.backlogCounts()
    // `extra` = pending items a dry run would add, per watcher
    val counts = (stored.keySet ++ extra.keySet)
      .map(id => id -> stored.get(id).fold((extra.getOrElse(id, 0), 0))((p, hd) => (p + extra.getOrElse(id, 0), hd)))
      .toMap
    if counts.isEmpty then Snapshot(Map.empty, 0, None)
    else
      val left = ownLeft(now)
      val light = agents.managerStore.light().map(m => m.id -> m).toMap
      val shared = agents.autopilot.sharedIngestLeft(now)
      val sharedCap = Option.when(shared.isDefined)(agents.autopilot.ingestCap).filter(_ > 0)
      // a held item drains once released: counted in the backlog, not in the days (it waits for a person)
      def lane(id: String, pending: Int): Lane =
        val perDay = light
          .get(id)
          .filter(_ => left.contains(id))
          .map(m => math.max(0, m.config.autoIngestPerDay.getOrElse(watch.autoIngestPerDay)))
          .getOrElse(0)
        Lane(pending, perDay, left.getOrElse(id, 0))
      val per = counts.map { case (id, (p, held)) =>
        id -> (
          p + held,
          Option.when(p > 0)(IngestBacklog.daysToDrain(List(lane(id, p)), sharedCap, shared)).getOrElse(Some(0))
        )
      }
      val pendingAll = counts.toList.map { case (id, (p, _)) => lane(id, p) }
      Snapshot(
        per,
        counts.values.map((p, hd) => p + hd).sum,
        IngestBacklog.daysToDrain(pendingAll.filter(_.backlog > 0), sharedCap, shared)
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
  def drain(now: Instant): List[String] =
    val pending = agents.managerStore.backlogPending()
    if pending.isEmpty then Nil
    else
      val left = ownLeft(now)
      val picks = IngestBacklog.drain(
        pending.map(i => Entry(i.id, i.managerId, i.priority, i.notBefore.flatMap(ManagerPolicy.parseInstant))),
        left,
        agents.autopilot.sharedIngestLeft(now),
        now,
        watch.drainPerTick
      )
      if picks.isEmpty then Nil
      else
        val byId = pending.map(i => i.id -> i).toMap
        val goalOf = agents.managerStore.light().map(m => m.id -> m.goal).toMap
        val results = picks.flatMap(e => byId.get(e.id)).map(i => one(i, goalOf.get(i.managerId).flatten, now))
        val ingested = results.collect { case Drained.Ingested(ck) => ck }
        val queued = results.collect { case Drained.Queued(id) => id }
        val known = results.count(_ == Drained.Known)
        val retried = results.count(_ == Drained.Retried)
        val waiting = agents.managerStore.backlogCounts().values.map((p, hd) => p + hd).sum
        val line =
          s"ingest backlog: ${ingested.size} ingested (legal open access${ingested.map(c => s" [[$c]]").mkString})" +
            (if queued.nonEmpty then
               s", ${queued.size} to the acquisition list (no legal copy: ${queued.map(i => s"#$i").mkString(", ")})"
             else "") +
            (if known > 0 then s", $known in the corpus already" else "") +
            (if retried > 0 then s", $retried to try again later" else "") + s"; $waiting waiting"
        val _ = site.workLists.add("log", QueueAdd(line.take(1000)))
        List(line)

  private enum Drained:
    case Ingested(citekey: String)
    case Queued(item: Int)
    case Known
    case Retried
    case Lost

  private def one(i: ManagerItem, holderGoal: Option[String], now: Instant): Drained =
    val doi = i.target
    val w = i.state.oa.getOrElse(OaWork(doi))
    val goals = (w.goals ++ holderGoal.toList).distinct
    def settle(status: ItemStatus, outcome: String, kind: String, text: String, d: Drained): Drained =
      if agents.managerStore.settle(i.id, status, outcome, List(ItemStatus.Pending)) then
        agents.managerStore.event(i.managerId, Some(i.id), None, kind, text)
        d
      else Drained.Lost
    site.sourceStore.citekeyOfDoi(doi) match
      case Some(ck) =>
        goals.foreach(g =>
          agents.goals.link(
            g,
            io.scalaland.chimney.Transformer
              .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
              .transform(LinkKind.Source),
            ck,
            Some(s"found by a watcher sweep: ${w.why}".take(500))
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
              agents.goals.link(
                g,
                io.scalaland.chimney.Transformer
                  .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
                  .transform(LinkKind.Source),
                r.citekey,
                Some(s"ingested from a watcher's backlog: ${w.why}".take(500))
              )
            )
            settle(
              ItemStatus.Done,
              s"ingested [[${r.citekey}]]",
              Feed.Ingested,
              s"[[${r.citekey}]] ingested from the backlog (legal open access, ${r.pdf.fold("no file")(_.source)}): " +
                s"${if w.title.nonEmpty then w.title else doi} — https://doi.org/$doi",
              Drained.Ingested(r.citekey)
            )
          case Left(m) =>
            val s = settingsOf(i.managerId)
            IngestBacklog.onFailure(m, i.failures, s.maxFailures, s.backoffMinutes, s.maxBackoffMinutes, now) match
              case Failure.Retry(nb, n, why) =>
                if agents.managerStore.backlogRetry(
                    i.id,
                    n,
                    nb,
                    s"not ingested ($n of ${s.maxFailures}): $why; again after $nb"
                  )
                then
                  agents.managerStore.event(
                    i.managerId,
                    Some(i.id),
                    None,
                    Feed.Evaluated,
                    s"$doi (ingest backlog): not ingested ($n of ${s.maxFailures}): $why; again after $nb"
                  )

                  Drained.Retried
                else Drained.Lost
              case Failure.ToAcquire(why) =>
                acquireOnce(goals, doi, w) match
                  case Some(id) =>
                    settle(
                      ItemStatus.Stopped,
                      s"to the acquisition list (#$id): $why",
                      Feed.Evaluated,
                      s"$doi (ingest backlog): to the acquisition list (#$id): $why",
                      Drained.Queued(id)
                    )
                  case None => Drained.Lost

  private def settingsOf(managerId: String): ManagerPolicy.ReviewSettings =
    val c = agents.managerStore.get(managerId).map(_.config).getOrElse(ManagerConfig())
    ManagerPolicy.ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Watch, c, managerDefaults)

  /** The acquisition item for `doi` (an existing one that names it, else a new one in the sweep's wording), linked to
    * every goal. None when the list refused it.
    */
  private def acquireOnce(goals: List[String], doi: String, w: OaWork): Option[Int] =
    val existing = site.queues.naming(Queue.Kind.Acquire, doi).headOption.map(_.id)
    val id = existing.orElse {
      val goal = goals.headOption.getOrElse("")
      val text = w.acquireText.getOrElse(
        AcquisitionSweepRules.acquireText(
          AcquisitionSweepRules.Candidate(
            doi,
            com.histmersion.api.library.Title(if w.title.nonEmpty then w.title else doi),
            w.year.map(com.histmersion.api.catalogue.Year.apply),
            w.authors.map(com.histmersion.api.library.AuthorName.apply),
            w.venue.map(com.histmersion.api.library.Venue.apply),
            isOa = true, "", None
          ),
          goal,
          w.why
        )
      )
      site.workLists
        .add(
          Queue.Kind.Acquire.label,
          QueueAdd(text, topic = Option.when(goal.nonEmpty)(s"goal $goal"), goal = Option.when(goal.nonEmpty)(goal))
        )
        .toOption
        .map(_.id)
    }
    id.foreach(i =>
      goals.foreach(g =>
        agents.goals.link(
          g,
          io.scalaland.chimney.Transformer
            .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
            .transform(LinkKind.QueueItem),
          i.toString,
          Some(doi)
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
  def repair(in: RepairRequest): RepairResult =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    val rows = agents.managerStore.sweepAcquireItems()
    val light = agents.managerStore.light().map(m => m.id -> m).toMap
    val items = rows.groupBy(_.item).toList.sortBy(_._1)
    val out = items.map { (item, rs) =>
      val first = rs.find(_.isOa).getOrElse(rs.head)
      val goals = rs.map(_.goal).distinct
      val w = light.get(first.managerId)
      val facts = RepairFacts(
        item,
        first.doi,
        rs.exists(_.isOa),
        first.managerId,
        w.isDefined,
        w.exists(m => m.state != State.Stopped.wire && m.config.autoIngest.contains(true)),
        site.sourceStore.citekeyOfDoi(first.doi)
      )
      IngestBacklog.repairAction(facts) match
        case RepairAction.Keep(why)  => RepairRow(item, first.doi, goals, first.managerId, Keep, why)
        case RepairAction.Remove(ck) =>
          if !in.dryRun then
            goals.foreach(g =>
              agents.goals.link(
                g,
                io.scalaland.chimney.Transformer
                  .derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind]
                  .transform(LinkKind.Source),
                ck,
                Some("found by a watcher sweep")
              )
            )
            removeAcquire(item, goals)
          RepairRow(item, first.doi, goals, first.managerId, Remove, s"in the corpus already: [[$ck]]")
        case RepairAction.Move =>
          val work = OaWork(
            first.doi,
            first.title.getOrElse(""),
            first.year,
            first.authors,
            first.venue,
            goals,
            first.why.getOrElse(""),
            Some(first.job),
            Some(first.text)
          )
          if in.dryRun then RepairRow(item, first.doi, goals, first.managerId, Move, "legal open access")
          else
            addMoved(first.managerId, work) match
              case None =>
                RepairRow(
                  item,
                  first.doi,
                  goals,
                  first.managerId,
                  Keep,
                  "its watcher settled a backlog entry for this work before (a person skipped it)"
                )
              case Some(id) =>
                goals.foreach(g => joinGoal(id, g))
                removeAcquire(item, goals)
                agents.managerStore.event(
                  first.managerId,
                  Some(id),
                  Some(first.job),
                  Feed.Evaluated,
                  s"${first.doi} (ingest backlog): moved from acquisition item #$item by repair-acquire ($by)"
                )

                RepairRow(item, first.doi, goals, first.managerId, Move, "legal open access", Some(id))
    }
    val untouched = site.queues.list(Queue.Kind.Acquire, QueueScope.Open).size - (if in.dryRun then items.size
                                                                                  else out.count(_.action == Keep))
    val snap = snapshot(extra = if in.dryRun then out.filter(_.action == Move).groupMapReduce(_.watcher)(_ => 1)(_ + _)
    else Map.empty)
    val result = RepairResult(
      in.dryRun,
      out.count(_.action == Move),
      out.count(_.action == Remove),
      out.count(_.action == Keep),
      math.max(0, untouched),
      out,
      snap.total,
      snap.totalDays
    )
    if !in.dryRun && (result.moved > 0 || result.removed > 0) then
      val _ = site.workLists.add(
        "log",
        QueueAdd(
          (s"repair-acquire ($by): ${result.moved} legal open-access work(s) moved from the " +
            s"acquisition list into the watchers' ingest backlogs, ${result.removed} in the corpus already removed, ${result.kept} kept; " +
            s"${result.backlog} waiting in the backlog").take(1000)
        )
      )
      agents.managers.nudge()
    result

  /** A moved work's backlog entry: an existing open one of any watcher, else a new one of `managerId`; None when
    * `managerId` settled one for it before (a person's skip is not undone).
    */
  private def addMoved(managerId: String, w: OaWork): Option[Long] =
    agents.managerStore.backlogOpenFor(w.doi).map(_.id).orElse {
      val (id, isNew) = agents.managerStore.upsert(
        managerId,
        OaIngest,
        w.doi,
        ItemOrigin.Selection,
        0,
        reopen = false,
        ItemState(oa = Some(w))
      )

      Option.when(isNew)(id)
    }

  private def joinGoal(itemId: Long, goal: String): Unit =
    agents.managerStore.item(itemId).foreach { i =>
      val st = i.state.oa.getOrElse(OaWork(i.target))
      if !st.goals.contains(goal) then
        val _ = agents.managerStore.setItemState(
          i.id,
          i.state.modify(_.oa).setTo(Some(st.modify(_.goals).setTo(st.goals :+ goal))),
          List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)
        )
    }

  private def removeAcquire(item: Int, goals: List[String]): Unit =
    val _ = site.workLists.delete(item)
    goals.foreach(g => agents.goals.unlink(g, com.histmersion.domain.automation.Goal.LinkKind.QueueItem, item.toString))

object OaBacklog:
  /** The backlog per watcher (pending + held, and the days its pending items take to drain at the current allowance),
    * and in all.
    */
  final case class Snapshot(perManager: Map[String, (Int, Option[Int])], total: Int, totalDays: Option[Int])
