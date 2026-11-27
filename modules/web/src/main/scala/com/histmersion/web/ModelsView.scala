package com.histmersion
package web

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Wording
import com.histmersion.api.automation.Agents.{CheckLevel, DoctorReport, ModelsInfo}
import com.histmersion.api.automation.ModelChains.*
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.{Agents, AgentsMarkdown, PlanQuota}
import com.histmersion.lint.{tolerated, LintRule}

/** The agent models window (docs/research-embedded-agents.md §15): each step role's model CHAIN — the models tried in
  * order, the one the next call would use, why the others are passed over — and each allowance (tokens/USD per rolling
  * window, a reserve) with what is used, left, and when it frees. Chains and allowances are edited here and saved in
  * the database over the server's config (behind the write gate, as every write); `check providers` runs `agent doctor`
  * (nothing is spent).
  */
object ModelsView:

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A chain as typed: `a > b > c` (commas also separate). */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chainInput(s: String): List[ChainEntry.Model] =
    s.split("[>,]").toList.map(_.trim).filter(_.nonEmpty).map(ChainEntry.Model(_))

  /** Limits as typed: `2M tokens/5h, 5 usd/day` → their wire form, or the first problem. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limitsInput(s: String): Either[String, List[LimitSpec]] =
    s.split(',').toList.map(_.trim).filter(_.nonEmpty).map(t => Limit.parse(Limit.Spec(t))).partitionMap(identity) match
      case (Nil, ls) => Right(ls.map(_.spec))
      case (errs, _) => Left(errs.map(_.sentence).mkString("; "))

  /** A reserve as typed: 0..99, blank = 0. */
  def reserveInput(s: String): Either[String, AllowanceSet.ReservePct] =
    val t = s.trim.stripSuffix("%").trim
    if t.isEmpty then Right(AllowanceSet.ReservePct(0))
    else
      t.toIntOption
        .filter(r => r >= 0 && r <= 99)
        .map(AllowanceSet.ReservePct(_))
        .toRight(s"'$s' is not a reserve of 0 to 99 %")

  /** How much of a limit is used, 0..100 (a bar's width). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def usedPct(l: LimitStatus): Int =
    if l.amount.value <= 0 then 100
    else math.max(0, math.min(100, math.round(l.used.value / l.amount.value * 100).toInt))

  /** The state class of one chained model. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def entryState(e: ChainEntry): String =
    if e.usable.value then "is-usable" else if !e.ready.value then "is-unready" else "is-paused"

  /** A plan window's bar: how much is used (0..100) and where its floor sits, as the share USED at which the floor is
    * reached (a floor of 25 % left sits at 75 %).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def windowBar(w: PlanQuota.WindowStatus): (Int, Int) =
    (math.max(0, math.min(100, math.round(w.usedPct.value).toInt)), math.max(0, math.min(100, 100 - w.floor.value)))

  /** How old a reading is, in words. */
  def ageText(seconds: Long): String =
    if seconds < 120 then s"$seconds s ago"
    else if seconds < 7200 then s"${seconds / 60} min ago"
    else if seconds < 172800 then s"${seconds / 3600} h ago"
    else s"${seconds / 86400} d ago"

  /** The allowance's limits as one editable line (what [[limitsInput]] reads back). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limitsText(a: AllowanceStatus): String = a.limits.map(_.limit.value).mkString(", ")

  // ── the window ──────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pane: HtmlElement =
    val info = Var(Option.empty[ModelsInfo])
    val quota = Var(Option.empty[PlanQuota.QuotaInfo])
    val doctor = Var(Option.empty[DoctorReport])
    val failure = Var(Option.empty[String])
    val busy = Var(false)

    def load(): Unit =
      ApiClient.agentModels.onComplete { r =>
        r.fold(e => failure.set(Some(e.getMessage)), m => { failure.set(None); info.set(Some(m)) })
      }
      ApiClient.agentQuota.onComplete(_.foreach(q => quota.set(Some(q))))

    /** A write, then the picture again; its failure is shown, not thrown. */
    def write[A](f: => Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.failed.foreach(e => failure.set(Some(e.getMessage)))
        load()
      }

    def check(offline: Boolean): Unit =
      busy.set(true)
      ApiClient.agentDoctor(Agents.DoctorReport.Online(offline)).onComplete { r =>
        busy.set(false)
        r.fold(e => failure.set(Some(e.getMessage)), d => doctor.set(Some(d)))
      }

    div(
      cls := "tool-pane models-pane",
      onMountCallback(_ => load()),
      div(
        cls := "pane-bar",
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => load())),
        button(
          typ := "button",
          cls := "btn",
          disabled <-- busy.signal,
          title := "keys, the free model listing, prices, the claude CLI and its login — nothing is spent",
          "check providers",
          onClick --> (_ => check(offline = false))
        )
      ),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- doctor.signal.map(_.map(doctorBlock)),
      child.maybe <-- quota.signal.map(_.map(q => plansBlock(q, busy.signal, write))),
      child <-- info.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(m) => body(m, busy.signal, write)
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def body(m: ModelsInfo, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    div(
      Option.when(!m.enabled.value)(
        p(
          cls := "muted",
          "Embedded agents are off on this server (agents.enabled); chains and allowances can still be set."
        )
      ),
      h4("Model chains"),
      p(
        cls := "muted",
        "Each role tries its models in order and uses the first with allowance left; " +
          "when none has, a run pauses and resumes by itself once a window frees."
      ),
      table(
        cls := "models-chains",
        thead(tr(th("role"), th("chain (tried in order)"), th("next call"), th("from"))),
        tbody(m.chains.map(c => chainRow(c, busy, write)))
      ),
      h4("Allowances"),
      p(
        cls := "muted",
        "Per model, or per provider (provider:*): tokens or USD per rolling window, and a reserve " +
          "below which the model is passed over. claude-code counts what claude -p reported — the plan's real " +
          "remaining usage cannot be seen from here."
      ),
      if m.allowances.isEmpty then
        p(cls := "muted", "No allowance: every model may be used up to the runs' and the day's budgets.")
      else ul(cls := "allowance-list", m.allowances.map(a => allowanceRow(a, busy, write))),
      allowanceForm(None, busy, write),
      Option.when(m.problems.nonEmpty)(
        div(cls := "run-warnings", h4("Problems"), ul(m.problems.map(x => li(x.sentence))))
      )
    )

  /** The subscription pools: one bar per window with its floor marker, the reset, how old the reading is and where it
    * came from, and the hold switch (behind the write gate).
    */
  /** `five_hour=25, seven_day=40` → `5-hour limit 25 %, this week's limit 40 %`: the floors as limits. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def floorsText(floors: PlanQuota.PoolStatus.Floors): String =
    floors.value
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { f =>
        f.split("=", 2) match
          case Array(k, v) =>
            s"${Wording.allowanceWindow(PlanQuota.WindowStatus.Kind(k.trim)).toLowerCase} ${v.trim.stripSuffix("%")} %"
          case _ => f
      }
      .mkString(", ")

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def plansBlock(q: PlanQuota.QuotaInfo, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    div(
      cls := "plan-pools",
      h4("Subscription plans"),
      p(
        cls := "muted",
        "A plan is shared with your own use: a run draws on it only while every window keeps more " +
          "than its floor left, and pauses until the reset otherwise."
      ),
      ul(
        cls := "allowance-list",
        q.plans.map { pl =>
          li(
            cls := s"allowance ${if pl.usable.value then "is-usable" else "is-paused"}",
            div(
              cls := "allowance-head",
              strong(pl.title),
              span(
                cls := "muted",
                title := pl.floors.value,
                s" · floors ${if pl.floors.value.isEmpty then "none" else floorsText(pl.floors)} (${pl.floorsSource})" +
                  pl.planType.fold("")(t => s" · plan $t")
              ),
              if !pl.available.value then
                span(cls := "run-paused", s" · ${pl.reason.map(w => s"$w").getOrElse("unavailable: no login")}")
              else if pl.held.value then span(cls := "run-paused", " · held")
              else if pl.usable.value then span(cls := "muted", pl.reason.fold(" · usable")(r => s" · usable ($r)"))
              else
                span(
                  cls := "run-paused",
                  s" · paused: ${pl.reason.map(w => s"$w").getOrElse("below its floor")}${pl.until
                      .fold("")(u => s" until $u")}"
                )
              ,
              child <-- Auth.writable.map(w =>
                if !w then span()
                else
                  span(
                    " · ",
                    button(
                      typ := "button",
                      cls := "link-btn",
                      disabled <-- busy,
                      title := (if pl.held.value then "let agent runs use this plan again"
                                else "pause every agent use of this plan"),
                      if pl.held.value then "release" else "hold",
                      onClick --> (_ =>
                        write(
                          ApiClient
                            .setPool(pl.pool, PlanQuota.PoolSet(hold = Some(PlanQuota.PoolSet.Hold(!pl.held.value))))
                        )
                      )
                    ),
                    " · ",
                    button(
                      typ := "button",
                      cls := "link-btn",
                      disabled <-- busy,
                      title := (if pl.pool == PlanQuota.PoolId.ClaudePlan.wire then
                                  "read the plan now (one minimal claude -p turn)"
                                else "read the plan now (codex app-server; spends nothing)"),
                      "refresh",
                      onClick --> (_ => write(ApiClient.refreshQuota(pl.pool)))
                    )
                  )
              )
            ),
            pl.note.map(n => div(cls := "muted", n)),
            div(
              cls := "muted",
              pl.refresh.fold("No fresh reading is taken (refresh off)")(m => s"Fresh reading by $m") +
                s" · refreshed ${pl.refreshesToday} time${if pl.refreshesToday.value == 1 then "" else "s"} today" +
                pl.lastRefresh.fold("")(r =>
                  s" · last ${r.at} (${r.trigger}): ${if r.ok.value then "" else "failed — "}${r.detail}"
                )
            ),
            if pl.windows.isEmpty then
              div(cls := "muted", "No reading yet: unknown until a run or a fresh reading reads it.")
            else
              div(pl.windows.map { w =>
                val (used, floorAt) = windowBar(w)
                div(
                  cls := "allowance-limit",
                  span(
                    cls := "allowance-name",
                    title := s"${w.limit} ${w.kind}",
                    if w.limit.value == "claude" || w.limit.value == "codex" then Wording.allowanceWindow(w.kind)
                    else
                      s"${Wording.humanize(Wording.Id(w.limit.value))}: ${Wording.allowanceWindow(w.kind).toLowerCase}"
                  ),
                  div(
                    cls := s"allowance-bar plan-bar ${if w.usable.value then "" else "is-over"}",
                    styleAttr := s"--pct:$used%; --floor:$floorAt%",
                    role := "img",
                    aria.label := s"$used % used, floor at ${w.floor} % left"
                  ),
                  span(
                    cls := "muted",
                    s"$used % used · floor ${w.floor} % left" + w.resetsAt.fold("")(t => s" · resets $t") +
                      s" · read ${ageText(w.ageSeconds.value)} from ${w.source}" + (if w.stale.value then " (stale)"
                                                                                    else "")
                  )
                )
              })
          )
        }
      ),
      p(
        cls := "muted",
        if q.fallthrough.isEmpty then "No chain falls from a plan below its floor to a paid key."
        else s"May fall from a plan to a paid key: ${q.fallthrough.mkString(", ")}."
      )
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def chainRow(c: RoleChain, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    val editing = Var(false)
    val draft = Var[String](c.models.map(_.model.value).mkString(" > "))
    tr(
      td(c.role.value),
      td(
        if c.models.isEmpty then
          span(cls := "muted", if c.problems.isEmpty then "no model" else c.problems.map(_.sentence).mkString("; "))
        else
          span(
            cls := "chain",
            c.models.zipWithIndex.flatMap { (e, i) =>
              (if i > 0 then List(span(cls := "chain-sep", " → ")) else Nil) :+
                span(
                  cls := s"chain-entry ${entryState(e)}",
                  title := Option((e.problems.map(_.sentence) ++ e.billing.toList).mkString(" · "))
                    .filter(_.nonEmpty)
                    .getOrElse("usable"),
                  e.model.value,
                  e.freesAt.map(t => span(cls := "muted", s" until $t"))
                )
            }
          )
        ,
        child <-- Auth.writable.combineWith(editing.signal).map { (w, ed) =>
          if !w then span()
          else if !ed then
            span(
              " ",
              button(typ := "button", cls := "link-btn", "edit", onClick --> (_ => editing.set(true))),
              Option.when(c.source == SettingSource.Saved.wire)(
                span(
                  " · ",
                  button(
                    typ := "button",
                    cls := "link-btn",
                    disabled <-- busy,
                    title := "back to the server's config",
                    "reset",
                    onClick --> (_ => write(ApiClient.resetChain(c.role)))
                  )
                )
              )
            )
          else
            form(
              cls := "form-row chain-form",
              input(
                typ := "text",
                cls := "field",
                aria.label := s"chain of ${c.role}",
                placeholder := "claude-code:sonnet > anthropic:claude-sonnet-4-5 > local:qwen3",
                controlled(value <-- draft.signal, onInput.mapToValue --> draft.writer)
              ),
              button(typ := "submit", cls := "btn btn--primary", disabled <-- busy, "save"),
              button(typ := "button", cls := "btn", "cancel", onClick --> (_ => editing.set(false))),
              onSubmit.preventDefault --> (_ => {
                editing.set(false); write(ApiClient.setChain(c.role, chainInput(draft.now())))
              })
            )
        }
      ),
      td(c.active.map(_.value).getOrElse(if c.models.isEmpty then "—" else "none: would pause")),
      td(cls := "muted", c.source.value)
    )

  private def amount(v: Double, m: Measure): String = m match
    case Measure.Tokens => Limit.compact(BigDecimal(math.round(v)))
    case Measure.Usd    => s"${AgentsMarkdown.usd(v)} USD"

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def allowanceRow(a: AllowanceStatus, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    li(
      cls := s"allowance ${if a.usable.value then "is-usable" else "is-paused"}",
      div(
        cls := "allowance-head",
        code(a.target.value),
        span(cls := "muted", s" · ${a.source}${if a.reservePct.value > 0 then s" · reserve ${a.reservePct}%" else ""}"),
        if a.usable.value then span(cls := "muted", " · has headroom")
        else
          span(
            cls := "run-paused",
            s" · below its reserve${a.freesAt.fold(" (does not free by itself)")(t => s" until $t")}"
          )
        ,
        child <-- Auth.writable.map(w =>
          if w && a.source == SettingSource.Saved.wire then
            span(
              " · ",
              button(
                typ := "button",
                cls := "link-btn",
                disabled <-- busy,
                title := "back to the server's config",
                "remove",
                onClick --> (_ => write(ApiClient.removeAllowance(a.target)))
              )
            )
          else span()
        )
      ),
      a.note.map(n => div(cls := "muted", n)),
      a.limits.map { l =>
        div(
          cls := "allowance-limit",
          span(
            cls := "allowance-name",
            title := l.limit.value,
            Wording.allowanceWindow(PlanQuota.WindowStatus.Kind(l.limit.value))
          ),
          div(
            cls := s"allowance-bar ${if l.usable.value then "" else "is-over"}",
            styleAttr := s"--pct:${usedPct(l)}%",
            role := "img",
            aria.label := s"${usedPct(l)} % used"
          ),
          span(
            cls := "muted",
            s"${amount(l.used.value, l.measure)} used · ${amount(l.remaining.value, l.measure)} left" +
              (if l.reserve.value > 0 then s" · reserve ${amount(l.reserve.value, l.measure)}" else "") + l.freesAt
                .fold("")(t => s" · frees $t")
          )
        )
      },
      child <-- Auth.writable.map(w => if w then allowanceForm(Some(a), busy, write) else span())
    )

  /** Set an allowance: a new one (`None`), or change an existing one (prefilled). */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def allowanceForm(
      of: Option[AllowanceStatus],
      busy: Signal[Boolean],
      write: (=> Future[Any]) => Unit
  ): HtmlElement =
    val target = Var[String](of.map(_.target.value).getOrElse(""))
    val limits = Var[String](of.map(limitsText).getOrElse(""))
    val reserve = Var[String](of.map(_.reservePct.value.toString).getOrElse(""))
    val problem = Var(Option.empty[String])
    val open = Var(false)
    def submit(): Unit =
      (limitsInput(limits.now()), reserveInput(reserve.now())) match
        case (Left(m), _)          => problem.set(Some(m))
        case (_, Left(m))          => problem.set(Some(m))
        case (Right(ls), Right(r)) =>
          problem.set(None); open.set(false)
          write(ApiClient.setAllowance(AllowanceSet(AllowanceSet.Target(target.now().trim), ls, r)))
    div(
      cls := "allowance-edit",
      child <-- Auth.writable.combineWith(open.signal).map { (w, o) =>
        if !w then (if of.isEmpty then Auth.keyNeeded("set an allowance") else span())
        else if !o then
          button(
            typ := "button",
            cls := "link-btn",
            if of.isEmpty then "add an allowance" else "change",
            onClick --> (_ => open.set(true))
          )
        else
          form(
            cls := "form-row allowance-form",
            Option.when(of.isEmpty)(
              input(
                typ := "text",
                cls := "field",
                aria.label := "target",
                placeholder := "claude-code:* or provider:model",
                controlled(value <-- target.signal, onInput.mapToValue --> target.writer)
              )
            ),
            input(
              typ := "text",
              cls := "field allowance-limits",
              aria.label := "limits",
              placeholder := "2M tokens/5h, 20M tokens/7d, 5 usd/day",
              controlled(value <-- limits.signal, onInput.mapToValue --> limits.writer)
            ),
            input(
              typ := "text",
              cls := "field allowance-reserve",
              aria.label := "reserve in percent",
              placeholder := "reserve %",
              controlled(value <-- reserve.signal, onInput.mapToValue --> reserve.writer)
            ),
            button(typ := "submit", cls := "btn btn--primary", disabled <-- busy, "save"),
            button(typ := "button", cls := "btn", "cancel", onClick --> (_ => open.set(false))),
            onSubmit.preventDefault --> (_ => submit())
          )
      },
      child.maybe <-- problem.signal.map(_.map(m => p(cls := "form-error", role := "alert", m)))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def doctorBlock(d: DoctorReport): HtmlElement =
    def level(l: CheckLevel) = span(cls := s"doctor-level level-${l.wire}", l.wire.value)
    def checks(cs: List[Agents.DoctorCheck]) =
      ul(cls := "doctor-checks", cs.map(c => li(level(c.level), s" ${c.name}: ${c.detail}")))
    div(
      cls := "doctor",
      h4(s"Provider check${if d.online.value then "" else " (offline)"}"),
      checks(d.checks),
      d.providers.map { p =>
        htmlTag[org.scalajs.dom.HTMLElement]("details")(
          // a used provider with a problem starts unfolded
          onMountCallback(ctx =>
            if p.used.value && p.level != CheckLevel.Ok then ctx.thisNode.ref.setAttribute("open", "")
          ),
          htmlTag("summary")(
            level(p.level),
            s" ${p.provider}",
            span(
              cls := "muted",
              if p.used.value then s" · used by ${p.models.mkString(", ")}" else " · no role uses it"
            )
          ),
          checks(p.checks)
        )
      }
    )
