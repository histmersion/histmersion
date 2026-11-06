package com.histmersion.api.automation
import com.histmersion.api.automation.Goal.Plans.{GoalLink, GoalSchedule, GoalView}
import com.histmersion.api.YearFmt
import com.histmersion.api.given

object GoalsExport:

  type TreeWalkResultPart2 = TreeWalkResultPart2.T
  object TreeWalkResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeWalkDepth = TreeWalkDepth.T
  object TreeWalkDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeWalkSeen = TreeWalkSeen.T
  object TreeWalkSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsSectionLabel = LinkSectionsSectionLabel.T
  object LinkSectionsSectionLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsSectionLineResult = LinkSectionsSectionLineResult.T
  object LinkSectionsSectionLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsSectionKeyResultPart1 = LinkSectionsSectionKeyResultPart1.T
  object LinkSectionsSectionKeyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsSectionKeyResultPart2 = LinkSectionsSectionKeyResultPart2.T
  object LinkSectionsSectionKeyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderItemStatusPart1 = RenderItemStatusPart1.T
  object RenderItemStatusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderItemStatusPart2 = RenderItemStatusPart2.T
  object RenderItemStatusPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryResult = SummaryResult.T
  object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeResultPart2 = TreeResultPart2.T
  object TreeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockResult = BlockResult.T
  object BlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockDepth = BlockDepth.T
  object BlockDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockKnown = BlockKnown.T
  object BlockKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockItemStatusPart1 = BlockItemStatusPart1.T
  object BlockItemStatusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockItemStatusPart2 = BlockItemStatusPart2.T
  object BlockItemStatusPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsResult = LinkSectionsResult.T
  object LinkSectionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsItemStatusPart1 = LinkSectionsItemStatusPart1.T
  object LinkSectionsItemStatusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkSectionsItemStatusPart2 = LinkSectionsItemStatusPart2.T
  object LinkSectionsItemStatusPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitledResult = TitledResult.T
  object TitledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotedResult = NotedResult.T
  object NotedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueLineResult = QueueLineResult.T
  object QueueLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueLineItemStatusPart1 = QueueLineItemStatusPart1.T
  object QueueLineItemStatusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueLineItemStatusPart2 = QueueLineItemStatusPart2.T
  object QueueLineItemStatusPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleLineResult = ScheduleLineResult.T
  object ScheduleLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneLineResult = OneLineResult.T
  object OneLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneLineS = OneLineS.T
  object OneLineS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val File = "GOALS.md"

  val Header = "# 🎯 GOALS — research goals"

  private val Notice =
    "_Generated by `histmersion export` / `histmersion queue export` from the Histmersion database. " +
      "Do not edit: the next export overwrites this file. The source of truth is Histmersion " +
      "(`histmersion goals …`)._"

  def render(views: List[GoalView], itemStatus: Map[com.histmersion.api.automation.GoalsExport.RenderItemStatusPart1, com.histmersion.api.automation.GoalsExport.RenderItemStatusPart2]): com.histmersion.api.automation.GoalsExport.RenderResult =
    val goals = views.map(_.goal)
    val head = List(Header, Notice, summary(goals), "---")
    val known = goals.map(_.slug).toSet
    val body =
      if views.isEmpty then List("_No goals._") else tree(views).map((v, depth) => block(v, depth, known, itemStatus))
    _root_.com.histmersion.api.automation.GoalsExport.RenderResult((head ++ body).mkString("\n\n") + "\n")

  private[api] def summary(goals: List[Goal]): com.histmersion.api.automation.GoalsExport.SummaryResult =
    val slugs = goals.map(_.slug).toSet
    val topLevel = goals.count(g => g.parentSlug.forall(p => !slugs.contains(p)))
    val counts = goals.groupBy(_.status).view.mapValues(_.size).toMap
    val order = com.histmersion.api.automation.Goal.Status.all.filter(counts.contains) ++ counts.keys.toList
      .filterNot(com.histmersion.api.automation.Goal.Status.all.contains)
      .sorted
    val byStatus = order.map(s => s" · $s ${counts(s)}").mkString
    _root_.com.histmersion.api.automation.GoalsExport.SummaryResult(s"${goals.size} goal(s): $topLevel top-level, ${goals.size - topLevel} sub-goal(s)$byStatus")

  private[api] def tree(views: List[GoalView]): List[(GoalView, com.histmersion.api.automation.GoalsExport.TreeResultPart2)] =
    val bySlug = views.map(v => v.goal.slug -> v).toMap
    val children = views.groupBy(_.goal.parentSlug).view.mapValues(_.sortBy(_.goal.slug)).toMap
    val roots = views.filter(v => v.goal.parentSlug.forall(p => !bySlug.contains(p))).sortBy(_.goal.slug)
    def walk(v: GoalView, depth: com.histmersion.api.automation.GoalsExport.TreeWalkDepth, seen: Set[com.histmersion.api.automation.GoalsExport.TreeWalkSeen]): List[(GoalView, com.histmersion.api.automation.GoalsExport.TreeWalkResultPart2)] =
      (v, depth) :: children
        .getOrElse(Some(v.goal.slug), Nil)
        .filterNot(c => seen.contains(c.goal.slug))
        .flatMap(c => walk(c, depth + 1, seen + c.goal.slug))
    val placed = roots.flatMap(r => walk(r, 0, Set(r.goal.slug)))
    val shown = placed.map(_._1.goal.slug).toSet
    val strays = views.filterNot(v => shown.contains(v.goal.slug)).sortBy(_.goal.slug).map(_ -> 0)
    placed ++ strays

  private def block(v: GoalView, depth: com.histmersion.api.automation.GoalsExport.BlockDepth, known: Set[com.histmersion.api.automation.GoalsExport.BlockKnown], itemStatus: Map[com.histmersion.api.automation.GoalsExport.BlockItemStatusPart1, com.histmersion.api.automation.GoalsExport.BlockItemStatusPart2]): com.histmersion.api.automation.GoalsExport.BlockResult =
    val g = v.goal
    val heading = s"${"#" * math.min(depth + 2, 6)} ${oneLine(g.title)}"
    val facts = List(
      Some(s"`${g.slug}` · ${g.status}"),
      Option(YearFmt.openPeriod(g.periodStart, g.periodEnd, "")).filter(_.nonEmpty).map(p => s"**Period:** $p"),
      Option.when(g.regions.nonEmpty)(s"**Regions:** ${g.regions.mkString(", ")}"),
      Option.when(g.tags.nonEmpty)(s"**Tags:** ${g.tags.mkString(", ")}"),
      g.parentSlug.filter(_ => depth == 0).map(p => s"**Parent:** `$p`" + (if known(p) then "" else " (not found)")),
      v.budgetUsd.map(b => s"**Run cap:** ${AgentsMarkdown.usd(b)} USD")
    ).flatten.mkString("  \n")
    val notes = g.notes.map(_.trim).filter(_.nonEmpty).toList
    val links = linkSections(v.links, itemStatus)
    val sch = if v.schedules.isEmpty then Nil
    else List("**Schedules**\n" + v.schedules.sortBy(_.kind.ordinal).map(s => s"- ${scheduleLine(s)}").mkString("\n"))
    val plan = v.plan
      .filter(p => !p.isEmpty || p.rationale.trim.nonEmpty)
      .map(p => s"**Approved plan**\n\n${AgentsMarkdown.planBlock(p)}")
      .toList
    (List(heading + "\n\n" + facts) ++ notes ++ links ++ sch ++ plan).mkString("\n\n")

  private def linkSections(links: List[GoalLink], itemStatus: Map[com.histmersion.api.automation.GoalsExport.LinkSectionsItemStatusPart1, com.histmersion.api.automation.GoalsExport.LinkSectionsItemStatusPart2]): List[com.histmersion.api.automation.GoalsExport.LinkSectionsResult] =
    def section(
        kind: com.histmersion.api.automation.Goal.LinkKind,
        label: com.histmersion.api.automation.GoalsExport.LinkSectionsSectionLabel,
        line: GoalLink => com.histmersion.api.automation.GoalsExport.LinkSectionsSectionLineResult,
        key: GoalLink => (com.histmersion.api.automation.GoalsExport.LinkSectionsSectionKeyResultPart1, com.histmersion.api.automation.GoalsExport.LinkSectionsSectionKeyResultPart2)
    ) =
      links.filter(_.kind == kind).sortBy(key) match
        case Nil => None
        case ls  => Some(s"**$label**\n" + ls.map(l => s"- ${line(l)}").mkString("\n"))
    val byRef = (l: GoalLink) => (0L, l.ref)
    val byId = (l: GoalLink) => (l.ref.toLongOption.getOrElse(Long.MaxValue), l.ref)
    List(
      section(
        com.histmersion.api.automation.Goal.LinkKind.Article,
        "Articles",
        l => s"[[${l.ref}]]${titled(l)}${noted(l)}",
        byRef
      ),
      section(
        com.histmersion.api.automation.Goal.LinkKind.Source,
        "Sources",
        l => s"[[${l.ref}]]${titled(l)}${noted(l)}",
        byRef
      ),
      section(
        com.histmersion.api.automation.Goal.LinkKind.Query,
        "Searches",
        l => s"\"${oneLine(l.ref)}\"${noted(l)}",
        byRef
      ),
      section(
        com.histmersion.api.automation.Goal.LinkKind.QueueItem,
        "Queue items",
        l => queueLine(l, itemStatus),
        byId
      )
    ).flatten

  private def titled(l: GoalLink): com.histmersion.api.automation.GoalsExport.TitledResult = l.title.fold(" — (not found)")(t => s" — ${oneLine(t)}")
  private def noted(l: GoalLink): com.histmersion.api.automation.GoalsExport.NotedResult = l.note.map(oneLine).filter(_.nonEmpty).fold("")(n => s" · $n")

  private def queueLine(l: GoalLink, itemStatus: Map[com.histmersion.api.automation.GoalsExport.QueueLineItemStatusPart1, com.histmersion.api.automation.GoalsExport.QueueLineItemStatusPart2]): com.histmersion.api.automation.GoalsExport.QueueLineResult =
    l.ref.toIntOption.flatMap(itemStatus.get) match
      case Some(st) =>
        val box = if st == Queue.Done then "[x]" else "[ ]"
        s"$box `#${l.ref}` ${l.title.map(oneLine).getOrElse("")}${noted(l)}"
      case None => _root_.com.histmersion.api.automation.GoalsExport.QueueLineResult(s"`#${l.ref}` (not found)${noted(l)}")

  private def scheduleLine(s: GoalSchedule): com.histmersion.api.automation.GoalsExport.ScheduleLineResult =
    val state = if s.enabled then "on" else "off"
    val budget = s.budgetUsd.fold("")(b => s" · cap ${AgentsMarkdown.usd(b)} USD")
    _root_.com.histmersion.api.automation.GoalsExport.ScheduleLineResult(s"${s.kind.wire} · `${s.cron}` · $state$budget")

  private def oneLine(s: com.histmersion.api.automation.GoalsExport.OneLineS): com.histmersion.api.automation.GoalsExport.OneLineResult = s.linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
