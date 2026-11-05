package com.histmersion.api.automation
import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.api.given

object ManagersMarkdown:
  type TokensOneX = TokensOneX.T
  object TokensOneX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CountsResult = CountsResult.T
  object CountsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TodayResult = TodayResult.T
  object TodayResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UsdResult = UsdResult.T
  object UsdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UsdD = UsdD.T
  object UsdD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensResult = TokensResult.T
  object TokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensN = TokensN.T
  object TokensN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogResult = BacklogResult.T
  object BacklogResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogN = BacklogN.T
  object BacklogN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogDays = BacklogDays.T
  object BacklogDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResult = ItemResult.T
  object ItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsResult = ItemsResult.T
  object ItemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventResult = EventResult.T
  object EventResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsResult = EventsResult.T
  object EventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectResult = EffectResult.T
  object EffectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigLineResult = ConfigLineResult.T
  object ConfigLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintResult = HintResult.T
  object HintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueuedResult = EnqueuedResult.T
  object EnqueuedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetailResult = DetailResult.T
  object DetailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Managers.*

  private def counts(c: ItemCounts): com.histmersion.api.automation.ManagersMarkdown.CountsResult =
    List(
      "pending" -> c.pending,
      "held" -> c.held,
      "active" -> c.active,
      "done" -> c.done,
      "stopped" -> c.stopped,
      "failed" -> c.failed,
      "cancelled" -> c.cancelled
    ).filter(_._2 > 0).map((k, n) => s"$n $k").mkString(", ") match
      case "" => "no items"
      case s  => s

  def today(m: ManagerInfo): com.histmersion.api.automation.ManagersMarkdown.TodayResult =
    val cap = m.effective.dailyUsd.orElse(m.config.dailyUsd).filter(_ > 0)
    s"today ${usd(m.spentTodayUsd)}${cap.fold("")(c => s" of ${usd(c)}")} USD" +
      (if m.tokensToday > 0 then s", ${tokens(m.tokensToday)} tokens" else "")

  def usd(d: com.histmersion.api.automation.ManagersMarkdown.UsdD): com.histmersion.api.automation.ManagersMarkdown.UsdResult =
    BigDecimal(d).setScale(if d != 0 && math.abs(d) < 0.1 then 4 else 2, BigDecimal.RoundingMode.HALF_UP).toString

  private def tokens(n: com.histmersion.api.automation.ManagersMarkdown.TokensN): com.histmersion.api.automation.ManagersMarkdown.TokensResult =
    def one(x: com.histmersion.api.automation.ManagersMarkdown.TokensOneX) = BigDecimal(x).setScale(1, BigDecimal.RoundingMode.HALF_UP).toString
    if n >= 1000000 then s"${one(n / 1e6)}M" else if n >= 1000 then s"${one(n / 1e3)}k" else n.toString

  def backlog(n: com.histmersion.api.automation.ManagersMarkdown.BacklogN, days: Option[com.histmersion.api.automation.ManagersMarkdown.BacklogDays]): com.histmersion.api.automation.ManagersMarkdown.BacklogResult =
    s"${Plural.count(n, "legal open-access work")} in the ingest backlog" + (if n == 0 then ""
                                                                             else
                                                                               days match
                                                                                 case Some(0) =>
                                                                                   " (drained today at the current allowance)"
                                                                                 case Some(d) =>
                                                                                   s" (~${Plural.count(d, "more day")} at the current allowance)"
                                                                                 case None =>
                                                                                   " (not draining: auto-ingest off, a cap of 0, or the watcher not active)")

  def line(m: ManagerInfo): com.histmersion.api.automation.ManagersMarkdown.LineResult =
    val goal = m.goal.fold("")(g => s" · goal $g")
    val runs = if m.pauseRuns then " (its runs paused too)" else ""
    s"**${m.id}** (${m.kind}$goal) · ${m.state}$runs · ${counts(m.counts)} · ${m.inFlight} run(s) in flight · ${today(m)}" +
      (if m.backlog > 0 then s" · ${backlog(m.backlog, m.backlogDays)}" else "") +
      m.statusNote.filter(_.nonEmpty).fold("")(n => s" · $n") +
      m.lastEvent.fold("")(e => s" · last: ${e.kind} ${e.text.take(120)}")

  def list(ms: List[ManagerInfo]): com.histmersion.api.automation.ManagersMarkdown.ListResult =
    if ms.isEmpty then "_No managers._" else "# Managers\n\n" + ms.map(m => s"- ${line(m)}").mkString("\n")

  def item(i: ManagerItem): com.histmersion.api.automation.ManagersMarkdown.ItemResult =
    val run = i.run.fold(i.runId.fold("")(r => s" · run ${r.take(8)}"))(r =>
      s" · run ${r.id.take(8)} ${r.status}" + r.progress.filter(_.nonEmpty).fold("")(p => s" (${p.take(80)})")
    )
    val hold = i.hold.fold("")(h =>
      s" · held${h.until.fold("")(u => s" until $u")}${h.untilSource.fold("")(s => s" until $s exists")}" +
        h.untilApproval
          .fold("")(a => s" until approval #$a is decided") + Option(h.reason).filter(_.nonEmpty).fold("")(r => s": $r")
    )
    val nb = i.notBefore.fold("")(t => s" · not before $t")
    val kind = if i.kind == ArticleReview then "" else s" (${i.kind})"
    s"#${i.id} ${i.target}$kind · ${i.status} · priority ${i.priority} · ${i.attempts} run(s)" +
      (if i.failures > 0 then s", ${i.failures} failed" else "") + run + hold + nb +
      i.outcome.filter(_.nonEmpty).fold("")(o => s" · $o")

  def items(p: ItemPage): com.histmersion.api.automation.ManagersMarkdown.ItemsResult =
    if p.items.isEmpty then "_No items._"
    else
      s"Items ${p.offset + 1}–${p.offset + p.items.size} of ${p.total}\n\n" + p.items
        .map(i => s"- ${item(i)}")
        .mkString("\n")

  def event(e: ManagerEvent): com.histmersion.api.automation.ManagersMarkdown.EventResult =
    s"${e.at} · ${e.kind}${e.itemId.fold("")(i => s" · item #$i")}${e.runId.fold("")(r => s" · run ${r.take(8)}")} · ${e.text}"

  def events(p: EventPage): com.histmersion.api.automation.ManagersMarkdown.EventsResult =
    if p.events.isEmpty then "_No events._" else p.events.map(e => s"- ${event(e)}").mkString("\n")

  def effect(e: HintEffect): com.histmersion.api.automation.ManagersMarkdown.EffectResult =
    if e.action == HintEffect.Config then s"settings: ${e.config.fold("none")(configLine)}"
    else
      e.action + (e.targets ++ e.tag.map(t => s"tag $t") ++ e.topic.map(t => s"topic $t") ++
        e.goal.map(g => s"goal $g")).mkString(" ", ", ", "") + e.until.fold("")(u => s" until $u") +
        e.untilSource.fold("")(s => s" until $s exists") + e.priority.fold("")(p => s" (priority $p)")

  def configLine(c: ManagerConfig): com.histmersion.api.automation.ManagersMarkdown.ConfigLineResult =
    List(
      c.maxRuns.map(n => s"maxRuns=$n"),
      c.maxOpen.map(n => s"maxOpen=$n"),
      c.rounds.map(n => s"rounds=$n"),
      c.maxRunsPerArticle.map(n => s"maxRunsPerArticle=$n"),
      c.noProgressRuns.map(n => s"noProgressRuns=$n"),
      c.reopenLimit.map(n => s"reopenLimit=$n"),
      c.cooldownMinutes.map(n => s"cooldownMinutes=$n"),
      c.lowWater.map(n => s"lowWater=$n"),
      c.refillBatch.map(n => s"refillBatch=$n"),
      c.maxFailures.map(n => s"maxFailures=$n"),
      c.budgetUsd.map(n => s"budgetUsd=$n"),
      c.includeTags.map(t => s"includeTags=${t.mkString(",")}"),
      c.includeGoal.map(g => s"includeGoal=$g"),
      c.models.map(ms => s"models=${ms.map(_.text).mkString(";")}"),
      c.staleAfterDays.map(n => s"staleAfterDays=$n"),
      c.inferFilter.map(b => s"inferFilter=$b"),
      c.minSources.map(n => s"minSources=$n"),
      c.maxArticles.map(n => s"maxArticles=$n"),
      c.sweeps.map(ws => s"sweeps=${ws.mkString(",")}"),
      c.autoIngest.map(b => s"autoIngest=$b"),
      c.autoIngestPerDay.map(n => s"autoIngestPerDay=$n"),
      c.dailyUsd.map(n => s"dailyUsd=$n"),
      c.autoApprove.map(b => s"autoApprove=$b"),
      c.extendMinNew.map(n => s"extendMinNew=$n")
    ).flatten.mkString(", ") match
      case "" => "the defaults"
      case s  => s

  def hint(h: ManagerHint): com.histmersion.api.automation.ManagersMarkdown.HintResult =
    val scope = h.itemId.fold("manager-wide")(i => s"item #$i")
    val eff = h.effect.fold("")(e => s" · ${effect(e)}")
    val proposed = h.proposal.fold("")(a =>
      s" · proposed: ${h.proposed.fold("an action")(effect)} (approval #$a waits for a person)"
    )
    s"#${h.id} (${if h.standing then "standing" else "once"}, $scope, by ${h.author}$eff) ${h.text}$proposed" +
      h.retiredAt.fold("")(r => s" · retired $r") + (if h.uses > 0 then s" · reached ${h.uses} run(s)" else "")

  def enqueued(r: EnqueueResult): com.histmersion.api.automation.ManagersMarkdown.EnqueuedResult =
    val head = if r.items.isEmpty then s"Nothing queued for **${r.managerId}**." else s"Queued for **${r.managerId}**:"
    (List(head) ++ r.items.map(i => s"- ${item(i)}") ++ r.skipped.map(x => s"- not queued: $x")).mkString("\n")

  def detail(d: ManagerDetail): com.histmersion.api.automation.ManagersMarkdown.DetailResult =
    val m = d.manager
    val cfg = m.effective
    val settings = List(
      cfg.maxRuns.map(n => s"max-runs $n"),
      cfg.rounds.map(n => s"rounds $n"),
      cfg.maxRunsPerArticle.map(n => s"max-runs-per-article $n"),
      cfg.noProgressRuns.map(n => s"no-progress-runs $n"),
      cfg.reopenLimit.map(n => s"reopen-limit $n"),
      cfg.cooldownMinutes.map(n => s"cooldown $n min"),
      cfg.includeTags.filter(_.nonEmpty).map(t => s"tags ${t.mkString(", ")}"),
      cfg.includeGoal.map(g => s"goal $g"),
      cfg.models.filter(_.nonEmpty).map(ms => s"models ${ms.map(_.text).mkString("; ")}"),
      cfg.budgetUsd.map(u => s"budget $u USD/run"),
      cfg.staleAfterDays.filter(_ > 0).map(n => s"stale after $n days"),
      cfg.inferFilter.map(b => s"infer-filter ${if b then "on" else "off"}"),
      cfg.minSources.map(n => s"min-sources $n"),
      cfg.maxArticles.map(n => s"max-articles $n"),
      cfg.sweeps.map(ws => s"sweeps ${if ws.isEmpty then "none" else ws.mkString(", ")}"),
      cfg.autoIngest.map(b =>
        if b then s"auto-ingest of legal open access on (at most ${cfg.autoIngestPerDay.getOrElse(0)} a day)"
        else "auto-ingest off"
      ),
      cfg.dailyUsd.filter(_ > 0).map(u => s"daily cap ${usd(u)} USD (metered spend; plan-billed runs count 0 USD)"),
      cfg.autoApprove.map(b => if b then "its approvals decided by code checks" else "its approvals always a person's"),
      cfg.extendMinNew.filter(_ > 0).map(n => s"extend at $n new sources")
    ).flatten.mkString(" · ")
    val cron = m.cron.fold("")(c => s"\n- cron `$c` (UTC)" + m.nextTickAt.fold("")(t => s", next $t"))
    s"# Manager ${m.id}\n\n- ${line(m)}\n- settings: $settings$cron\n" +
      "\n## In flight\n\n" + (if d.current.isEmpty then "_none_"
                              else d.current.map(i => s"- ${item(i)}").mkString("\n")) +
      "\n\n## Hints\n\n" + (if d.hints.isEmpty then "_none_" else d.hints.map(h => s"- ${hint(h)}").mkString("\n")) +
      "\n\n## Events\n\n" + (if d.events.isEmpty then "_none_" else d.events.map(e => s"- ${event(e)}").mkString("\n"))
