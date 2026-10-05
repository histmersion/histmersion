package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.{CheckLevel, DoctorReport, ModelsInfo}
import com.histmersion.core.ModelChains.*
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The agent models window (docs/research-embedded-agents.md §15): each step role's model CHAIN — the
  * models tried in order, the one the next call would use, why the others are passed over — and each
  * allowance (tokens/USD per rolling window, a reserve) with what is used, left, and when it frees.
  * Chains and allowances are edited here and saved in the database over the server's config (behind
  * the write gate, as every write); `check providers` runs `agent doctor` (nothing is spent). */
object ModelsView:

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A chain as typed: `a > b > c` (commas also separate). */
  def chainInput(s: String): List[String] = s.split("[>,]").toList.map(_.trim).filter(_.nonEmpty)

  /** Limits as typed: `2M tokens/5h, 5 usd/day` → their wire form, or the first problem. */
  def limitsInput(s: String): Either[String, List[LimitSpec]] =
    s.split(',').toList.map(_.trim).filter(_.nonEmpty).map(Limit.parse).partitionMap(identity) match
      case (Nil, ls) => Right(ls.map(_.spec))
      case (errs, _) => Left(errs.mkString("; "))

  /** A reserve as typed: 0..99, blank = 0. */
  def reserveInput(s: String): Either[String, Int] =
    val t = s.trim.stripSuffix("%").trim
    if t.isEmpty then Right(0)
    else t.toIntOption.filter(r => r >= 0 && r <= 99).toRight(s"'$s' is not a reserve of 0 to 99 %")

  /** How much of a limit is used, 0..100 (a bar's width). */
  def usedPct(l: LimitStatus): Int =
    if l.amount <= 0 then 100 else math.max(0, math.min(100, math.round(l.used / l.amount * 100).toInt))

  /** The state class of one chained model. */
  def entryState(e: ChainEntry): String =
    if e.usable then "is-usable" else if !e.ready then "is-unready" else "is-paused"

  /** The allowance's limits as one editable line (what [[limitsInput]] reads back). */
  def limitsText(a: AllowanceStatus): String = a.limits.map(_.limit).mkString(", ")

  // ── the window ──────────────────────────────────────────────────────────────────────────────────

  def pane: HtmlElement =
    val info    = Var(Option.empty[ModelsInfo])
    val doctor  = Var(Option.empty[DoctorReport])
    val failure = Var(Option.empty[String])
    val busy    = Var(false)

    def load(): Unit =
      ApiClient.agentModels.onComplete { r =>
        r.fold(e => failure.set(Some(e.getMessage)), m => { failure.set(None); info.set(Some(m)) })
      }

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
      ApiClient.agentDoctor(offline).onComplete { r =>
        busy.set(false)
        r.fold(e => failure.set(Some(e.getMessage)), d => doctor.set(Some(d)))
      }

    div(
      cls := "tool-pane models-pane",
      onMountCallback(_ => load()),
      div(cls := "pane-bar",
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => load())),
        button(typ := "button", cls := "btn", disabled <-- busy.signal, title := "keys, the free model listing, prices, the claude CLI and its login — nothing is spent",
          "check providers", onClick --> (_ => check(offline = false)))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- doctor.signal.map(_.map(doctorBlock)),
      child <-- info.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(m) => body(m, busy.signal, write)
      }
    )

  private def body(m: ModelsInfo, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    div(
      Option.when(!m.enabled)(p(cls := "muted",
        "Embedded agents are off on this server (agents.enabled); chains and allowances can still be set.")),
      h4("Model chains"),
      p(cls := "muted", "Each role tries its models in order and uses the first with allowance left; " +
        "when none has, a run pauses and resumes by itself once a window frees."),
      table(cls := "models-chains",
        thead(tr(th("role"), th("chain (tried in order)"), th("next call"), th("from"))),
        tbody(m.chains.map(c => chainRow(c, busy, write)))),
      h4("Allowances"),
      p(cls := "muted", "Per model, or per provider (provider:*): tokens or USD per rolling window, and a reserve " +
        "below which the model is passed over. claude-code counts what claude -p reported — the plan's real " +
        "remaining usage cannot be seen from here."),
      if m.allowances.isEmpty then p(cls := "muted", "No allowance: every model may be used up to the runs' and the day's budgets.")
      else ul(cls := "allowance-list", m.allowances.map(a => allowanceRow(a, busy, write))),
      allowanceForm(None, busy, write),
      Option.when(m.problems.nonEmpty)(div(cls := "run-warnings", h4("Problems"), ul(m.problems.map(li(_)))))
    )

  private def chainRow(c: RoleChain, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    val editing = Var(false)
    val draft   = Var(c.models.map(_.model).mkString(" > "))
    tr(
      td(c.role),
      td(
        if c.models.isEmpty then span(cls := "muted", c.problem.getOrElse("no model"))
        else span(cls := "chain", c.models.zipWithIndex.flatMap { (e, i) =>
          (if i > 0 then List(span(cls := "chain-sep", " → ")) else Nil) :+
            span(cls := s"chain-entry ${entryState(e)}", title := e.problem.getOrElse("usable"), e.model,
              e.freesAt.map(t => span(cls := "muted", s" until $t")))
        }),
        child <-- Auth.writable.combineWith(editing.signal).map { (w, ed) =>
          if !w then span()
          else if !ed then
            span(" ", button(typ := "button", cls := "link-btn", "edit", onClick --> (_ => editing.set(true))),
              Option.when(c.source == SettingSource.Saved.wire)(span(" · ",
                button(typ := "button", cls := "link-btn", disabled <-- busy, title := "back to the server's config",
                  "reset", onClick --> (_ => write(ApiClient.resetChain(c.role)))))))
          else
            form(cls := "form-row chain-form",
              input(typ := "text", cls := "field", aria.label := s"chain of ${c.role}",
                placeholder := "claude-code:sonnet > anthropic:claude-sonnet-4-5 > local:qwen3",
                controlled(value <-- draft.signal, onInput.mapToValue --> draft.writer)),
              button(typ := "submit", cls := "btn btn--primary", disabled <-- busy, "save"),
              button(typ := "button", cls := "btn", "cancel", onClick --> (_ => editing.set(false))),
              onSubmit.preventDefault --> (_ => { editing.set(false); write(ApiClient.setChain(c.role, chainInput(draft.now()))) }))
        }
      ),
      td(c.active.getOrElse(if c.models.isEmpty then "—" else "none: would pause")),
      td(cls := "muted", c.source)
    )

  private def amount(v: Double, m: Measure): String = m match
    case Measure.Tokens => Limit.compact(BigDecimal(math.round(v)))
    case Measure.Usd    => s"${AgentsMarkdown.usd(v)} USD"

  private def allowanceRow(a: AllowanceStatus, busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    li(cls := s"allowance ${if a.usable then "is-usable" else "is-paused"}",
      div(cls := "allowance-head",
        code(a.target), span(cls := "muted", s" · ${a.source}${if a.reservePct > 0 then s" · reserve ${a.reservePct}%" else ""}"),
        if a.usable then span(cls := "muted", " · has headroom")
        else span(cls := "run-paused", s" · below its reserve${a.freesAt.fold(" (does not free by itself)")(t => s" until $t")}"),
        child <-- Auth.writable.map(w => if w && a.source == SettingSource.Saved.wire then
          span(" · ", button(typ := "button", cls := "link-btn", disabled <-- busy, title := "back to the server's config",
            "remove", onClick --> (_ => write(ApiClient.removeAllowance(a.target)))))
          else span())),
      a.note.map(n => div(cls := "muted", n)),
      a.limits.map { l =>
        div(cls := "allowance-limit",
          span(cls := "allowance-name", l.limit),
          div(cls := s"allowance-bar ${if l.usable then "" else "is-over"}", styleAttr := s"--pct:${usedPct(l)}%",
            role := "img", aria.label := s"${usedPct(l)} % used"),
          span(cls := "muted", s"${amount(l.used, l.measure)} used · ${amount(l.remaining, l.measure)} left" +
            (if l.reserve > 0 then s" · reserve ${amount(l.reserve, l.measure)}" else "") + l.freesAt.fold("")(t => s" · frees $t")))
      },
      child <-- Auth.writable.map(w => if w then allowanceForm(Some(a), busy, write) else span())
    )

  /** Set an allowance: a new one (`None`), or change an existing one (prefilled). */
  private def allowanceForm(of: Option[AllowanceStatus], busy: Signal[Boolean], write: (=> Future[Any]) => Unit): HtmlElement =
    val target  = Var(of.map(_.target).getOrElse(""))
    val limits  = Var(of.map(limitsText).getOrElse(""))
    val reserve = Var(of.map(_.reservePct.toString).getOrElse(""))
    val problem = Var(Option.empty[String])
    val open    = Var(false)
    def submit(): Unit =
      (limitsInput(limits.now()), reserveInput(reserve.now())) match
        case (Left(m), _) => problem.set(Some(m))
        case (_, Left(m)) => problem.set(Some(m))
        case (Right(ls), Right(r)) =>
          problem.set(None); open.set(false)
          write(ApiClient.setAllowance(AllowanceSet(target.now().trim, ls, r)))
    div(
      cls := "allowance-edit",
      child <-- Auth.writable.combineWith(open.signal).map { (w, o) =>
        if !w then (if of.isEmpty then Auth.keyNeeded("set an allowance") else span())
        else if !o then button(typ := "button", cls := "link-btn", if of.isEmpty then "add an allowance" else "change",
          onClick --> (_ => open.set(true)))
        else
          form(cls := "form-row allowance-form",
            Option.when(of.isEmpty)(input(typ := "text", cls := "field", aria.label := "target",
              placeholder := "claude-code:* or provider:model",
              controlled(value <-- target.signal, onInput.mapToValue --> target.writer))),
            input(typ := "text", cls := "field allowance-limits", aria.label := "limits",
              placeholder := "2M tokens/5h, 20M tokens/7d, 5 usd/day",
              controlled(value <-- limits.signal, onInput.mapToValue --> limits.writer)),
            input(typ := "text", cls := "field allowance-reserve", aria.label := "reserve in percent",
              placeholder := "reserve %", controlled(value <-- reserve.signal, onInput.mapToValue --> reserve.writer)),
            button(typ := "submit", cls := "btn btn--primary", disabled <-- busy, "save"),
            button(typ := "button", cls := "btn", "cancel", onClick --> (_ => open.set(false))),
            onSubmit.preventDefault --> (_ => submit()))
      },
      child.maybe <-- problem.signal.map(_.map(m => p(cls := "form-error", role := "alert", m)))
    )

  private def doctorBlock(d: DoctorReport): HtmlElement =
    def level(l: CheckLevel) = span(cls := s"doctor-level level-${l.wire}", l.wire)
    def checks(cs: List[Agents.DoctorCheck]) = ul(cls := "doctor-checks", cs.map(c => li(level(c.level), s" ${c.name}: ${c.detail}")))
    div(cls := "doctor",
      h4(s"Provider check${if d.online then "" else " (offline)"}"),
      checks(d.checks),
      d.providers.map { p =>
        htmlTag[org.scalajs.dom.HTMLElement]("details")(
          // a used provider with a problem starts unfolded
          onMountCallback(ctx => if p.used && p.level != CheckLevel.Ok then ctx.thisNode.ref.setAttribute("open", "")),
          htmlTag("summary")(level(p.level), s" ${p.provider}", span(cls := "muted",
            if p.used then s" · used by ${p.models.mkString(", ")}" else " · no role uses it")),
          checks(p.checks))
      })
