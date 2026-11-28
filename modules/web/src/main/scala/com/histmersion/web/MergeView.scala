package com.histmersion
package web
import com.histmersion.util.problem.Text.{sentence, sentences}
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.{Api, Plural, RegisteredPlanMatches, YearFmt}
import com.histmersion.web.map.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.MatchOutcome
import com.histmersion.api.exchange.{
  IssueField,
  IssuePlace,
  IssueSide,
  IssueSpan,
  IssueText,
  MergeApplyJob,
  MergeApplyRequest,
  MergeApplyResult,
  MergeDecision,
  MergeIssue,
  MergeJobState,
  MergeMarkdown,
  MergePlan,
  MergeResolution,
  MergeResolutions
}
import com.histmersion.api.instance.{MapStartConfig, UiConfig}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Sha256
import com.histmersion.api.library.Citekey
import com.histmersion.web.ui.ThemeTokens
import com.histmersion.web.map.MapStart
import com.histmersion.lint.{tolerated, LintRule}

/** The merge review screen (`/merge`, docs/research-merge-import.md §6.5, milestone M6): upload another collection's
  * bundle, read its plan — the matched sources, every issue grouped (open first, then decided, conditional, defaults,
  * info) — and decide each one in place: a field table (here | result | bundle) for a source question, a diff3 view
  * with an editor for an article, both geometries on a small map and both spans for a place or an event. Every decision
  * is kept on the server (`merge_sessions`, so a reload or another browser carries on) and the plan is made again at
  * once, so a decision shows what it settles and what it opens. The decisions download as the resolution file the CLI
  * reads (`merge plan --resolutions`), and one written there uploads here. Apply posts the reviewed plan hash, behind
  * the write gate and a confirmation, and follows the apply job (its backup first) to its report.
  *
  * Left is always THIS collection ("here"), right the bundle — the plan's own vocabulary.
  */
object MergeView:
  type Description = Description.T
  object Description extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type FieldName = FieldName.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FieldName extends Newtype[String, Pure]
  type KindLabel = KindLabel.T
  object KindLabel extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type MarkedText = MarkedText.T
  object MarkedText extends Newtype[String, Pure]
  type ResultText = ResultText.T
  object ResultText extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** How the issue list is grouped, in the order it is shown. */

  enum Group(val title: Group.Title) derives CanEqual:
    case Open extends Group(Group.Title("to decide"))
    case Decided extends Group(Group.Title("decided"))
    case Conditional extends Group(Group.Title("conditional — listed now, so no new question appears after a decision"))
    case Defaults extends Group(Group.Title("defaults — applied unless changed"))
    case Info extends Group(Group.Title("information"))

  object Group:
    type Title = Title.T
    object Title extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def groupOf(i: MergeIssue): Group =
    if i.condition.isDefined then Group.Conditional
    else
      i.severity match
        case MergeIssue.Severity.Blocking => if i.decision.isEmpty then Group.Open else Group.Decided
        case MergeIssue.Severity.Default  => Group.Defaults
        case MergeIssue.Severity.Info     => Group.Info

  /** The issues by group, empty groups left out, the plan's order kept within a group. */
  def grouped(issues: List[MergeIssue]): List[(Group, List[MergeIssue])] =
    Group.values.toList.map(g => g -> issues.filter(groupOf(_) == g)).filter(_._2.nonEmpty)

  /** The counter: the blocking questions still open, of all the blocking ones that apply ("3 of 9"). */
  /** How each of the bundle's sources was matched. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def matched(p: MergePlan): List[Source.Match] = RegisteredPlanMatches.kind.all(p.sources)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def counter(p: MergePlan): (Int, Int) =
    val blocking = p.issues.filter(i => i.severity == MergeIssue.Severity.Blocking && i.condition.isEmpty)
    (blocking.count(_.decision.isEmpty), blocking.size)

  /** The issues a filter text keeps: it matches the id, the kind, the title or either side's label. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def filtered(issues: List[MergeIssue], text: String): List[MergeIssue] =
    val t = text.trim.toLowerCase
    if t.isEmpty then issues
    else
      issues.filter(i =>
        (List(i.id.value, MergeIssue.Kind.wireOf(i.kind).value, i.title.value, i.entity.wire.value) ++
          i.left.map(_.label.value).toList ++ i.right.map(_.label.value).toList).exists(_.toLowerCase.contains(t))
      )

  /** The resolutions with `issue` decided `decision` (and its merged text, for `file:`), replacing an earlier decision
    * of it; `None` takes the decision back.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decide(
      r: MergeResolutions,
      issue: MergeIssue.Id,
      decision: Option[MergeDecision],
      text: Option[MergeResolution.Text] = None
  ): MergeResolutions =
    val rest = r.resolve.filterNot(_.issue.value == issue.value)
    r.modify(_.resolve)
      .setTo(
        (rest ++ decision.map(d =>
          MergeResolution(
            MergeResolution.Issue(issue.value),
            MergeResolution.Decision(d.wire.value),
            text.filter(_ => d.kind == MergeDecision.Kind.File)
          )
        ))
          .sortBy(_.issue)
      )

  /** The decision a person made for an issue (not a default, a policy or a remembered one). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chosen(r: MergeResolutions, issue: MergeIssue.Id): Option[MergeDecision] =
    r.resolve
      .find(_.issue.value == issue.value)
      .flatMap(x => MergeDecision.parse(MergeDecision.Wire(x.decision.value)).toOption)

  /** What a decision kind means, in words. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def describe(k: MergeDecision.Kind): Description = k match
    case MergeDecision.Kind.KeepLeft       => Description("keep this collection's")
    case MergeDecision.Kind.KeepRight      => Description("take the bundle's")
    case MergeDecision.Kind.Both           => Description("keep both")
    case MergeDecision.Kind.Concat         => Description("join both texts")
    case MergeDecision.Kind.Hull           => Description("the span covering both")
    case MergeDecision.Kind.Map            => Description("one source, under")
    case MergeDecision.Kind.RenameRight    => Description("keep both, the bundle's under a new key")
    case MergeDecision.Kind.RenameLeft     => Description("keep both, this collection's under a new key")
    case MergeDecision.Kind.File           => Description("the merged text, as edited here")
    case MergeDecision.Kind.Merge          => Description("make them one")
    case MergeDecision.Kind.Drop           => Description("drop it")
    case MergeDecision.Kind.KeepUnanchored => Description("keep it without a place in the text")

  /** An issue kind in words (`changed-both-sides` → `changed both sides`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def kindText(k: MergeIssue.Kind): KindLabel = KindLabel(MergeIssue.Kind.wireOf(k).value.replace('-', ' '))

  /** The row of a field table an issue is about (`status` for a status question, `citekey` for the same-work and
    * collision questions), if it is about one.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def fieldOf(k: MergeIssue.Kind): Option[FieldName] = k match
    case MergeIssue.Kind.StatusDiffers                               => Some(FieldName("status"))
    case MergeIssue.Kind.TitleDiffers                                => Some(FieldName("title"))
    case MergeIssue.Kind.AuthorsDiffers                              => Some(FieldName("authors"))
    case MergeIssue.Kind.YearDiffers                                 => Some(FieldName("year"))
    case MergeIssue.Kind.DoiDiffers                                  => Some(FieldName("DOI"))
    case MergeIssue.Kind.SummaryDiffers                              => Some(FieldName("summary"))
    case MergeIssue.Kind.NotesDiffers                                => Some(FieldName("notes"))
    case MergeIssue.Kind.PeriodDiffers                               => Some(FieldName("period"))
    case MergeIssue.Kind.BodyDiffers                                 => Some(FieldName("OCR body"))
    case MergeIssue.Kind.SameWork | MergeIssue.Kind.CitekeyCollision => Some(FieldName("citekey"))
    case MergeIssue.Kind.NamedPeriodDiffers                          => Some(FieldName("span"))
    case MergeIssue.Kind.GoalPlanDiffers                             => Some(FieldName("plan"))
    case _                                                           => None

  /** The result column of a field table: for the row the issue is about, what the merge writes as decided now (`Left` =
    * still to decide); for the other rows nothing — the merge's own rules fold them (the richer title, tags unioned…),
    * the server's business.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resultOf(i: MergeIssue, f: IssueField, d: Option[MergeDecision]): Option[Either[ResultText, ResultText]] =
    if !fieldOf(i.kind).map(_.value).contains(f.field.value) then None
    else
      Some(d.map(_.kind) match
        case Some(MergeDecision.Kind.KeepLeft)  => Right(ResultText(f.left.map(_.value).getOrElse("—")))
        case Some(MergeDecision.Kind.KeepRight) => Right(ResultText(f.right.map(_.value).getOrElse("—")))
        case Some(MergeDecision.Kind.Concat)    =>
          Right(ResultText(List(f.left.map(_.value), f.right.map(_.value)).flatten.mkString("\n\n")))
        case Some(MergeDecision.Kind.Hull) => Right(ResultText("the span covering both"))
        case Some(MergeDecision.Kind.Map)  => Right(ResultText(d.flatMap(_.argument).map(_.value).getOrElse("—")))
        case Some(MergeDecision.Kind.Both) =>
          Right(ResultText("two records: " + List(f.left.map(_.value), f.right.map(_.value)).flatten.mkString(" and ")))
        case Some(MergeDecision.Kind.RenameRight) =>
          Right(
            ResultText(
              s"${f.left.map(_.value).getOrElse("")} here, ${d.flatMap(_.argument).map(_.value).getOrElse("")} for the bundle's"
            )
          )
        case Some(MergeDecision.Kind.RenameLeft) =>
          Right(
            ResultText(
              s"${d.flatMap(_.argument).map(_.value).getOrElse("")} here, ${f.right.map(_.value).getOrElse("")} for the bundle's"
            )
          )
        case Some(other) => Right(ResultText(MergeDecision.Kind.wireOf(other).value))
        case None        => Left(ResultText("to decide")))

  /** The rows of a field table worth a person's eye: the ones that differ come first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rows(fs: List[IssueField]): List[(IssueField, Boolean)] =
    val (diff, same) =
      fs.filter(f => f.left.isDefined || f.right.isDefined).partition(f => f.left.map(_.value) != f.right.map(_.value))
    diff.map(_ -> true) ++ same.map(_ -> false)

  /** The text the article editor starts from: the agreed regions as they are, each conflict with git-style markers
    * around this collection's and the bundle's lines (and the common version's).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markedText(t: IssueText): MarkedText =
    MarkedText(
      t.hunks
        .flatMap { h =>
          if !h.conflict.conflict then h.left.map(_.value)
          else
            (("<<<<<<< here" :: h.left.map(_.value)) ++ (if h.base.nonEmpty then
                                                           "||||||| common version" :: h.base.map(_.value)
                                                         else Nil) ++
              ("=======" :: h.right.map(_.value)) :+ ">>>>>>> bundle"
          )
        }
        .mkString("\n")
    )

  /** Conflict markers still in an edited text: it is not merged yet. */
  def hasMarkers(text: String): Boolean =
    text.linesIterator.exists(l => l.startsWith("<<<<<<< ") || l == "=======" || l.startsWith(">>>>>>> "))

  /** The argument a `file:` decision made on this screen names (no file exists: the text travels in the decision, and
    * in the resolution file's `texts:`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fileName(i: MergeIssue): MergeDecision.Argument =
    MergeDecision.Argument(s"merged/${i.left.orElse(i.right).map(_.label.value).getOrElse(i.id.value)}.md")

  /** May the plan be applied now? Nothing open, nothing wrong, something to do. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def applicable(p: MergePlan): Boolean =
    p.open.isEmpty && p.problems.isEmpty && p.resolutionErrors.isEmpty && p.changes.value > 0

  /** A span as a bar on a common axis: left offset and width in per cent of `[lo, hi]`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bar(s: IssueSpan, lo: Year, hi: Year): (Double, Double) =
    val w = math.max(1L, hi.value - lo.value).toDouble
    val a = s.start.getOrElse(lo).value
    val b = s.end.getOrElse(hi).value
    (100.0 * (a - lo.value) / w, math.max(1.0, 100.0 * (b - a) / w))

  /** The axis of two spans: their hull, open ends given a margin of a tenth. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def axis(spans: List[IssueSpan]): Option[(Year, Year)] =
    val ends = spans.flatMap(s => s.start.toList ++ s.end.toList)
    Option.when(ends.nonEmpty) {
      val (lo, hi) = (ends.minBy(e => e.value: Long), ends.maxBy(e => e.value: Long))
      val m = math.max(10L, (hi.value - lo.value) / 10)
      (Year(lo.value - m), Year(hi.value + m))
    }

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  /** The window's state: one review at a time (the bundle named in the URL, `bundle=`). */
  private val upload = Var(Option.empty[Bundle.Upload])
  private val plan = Var(Option.empty[MergePlan])
  private val res = Var(MergeResolutions())
  private val selected = Var(Option.empty[MergeIssue.Id])
  private val filter = Var("")
  private val planning = Var(false)
  private val progress = Var(Option.empty[Double])
  private val failure = Var(Option.empty[String])
  private val message = Var(Option.empty[String])
  private val job = Var(Option.empty[MergeApplyJob])
  private val confirm = Var(false)

  /** The base map's tiles, from `/api/config` ([[configure]]). */
  private var tiles: Option[(UiConfig.Tiles, UiConfig.TilesMaxZoom)] = None

  /** The latest plan request: an answer to an older one is dropped (a `var`: it counts requests). */
  private var planReq = 0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def configure(ui: UiConfig): Unit =
    tiles = Some(ui.tiles -> ui.tilesMaxZoom)
    startView = ui.mapStart

  /** Where a map with nothing to show opens (`ui.map-start`); set from `/api/config` like `tiles`. */
  private var startView = MapStartConfig()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bundle: Option[Sha256] = UiState.mergeBundle.now().map(Sha256(_))

  private def fail(what: String)(e: Throwable): Unit = failure.set(Some(s"$what: ${e.getMessage}"))

  /** Open a bundle that is on the server: its draft, then its plan. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def load(sha: Sha256): Unit =
    failure.set(None)
    ApiClient.mergeSession(sha).onComplete(_.fold(fail("the review draft"), s => { res.set(s.resolutions); replan() }))

  /** Plan again with the decisions made so far. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def replan(): Unit =
    bundle.foreach { sha =>
      planReq += 1
      val mine = planReq
      planning.set(true)
      ApiClient.mergePlan(MergePlan.Request(MergePlan.Request.Bundle(sha.value), res.now())).onComplete { r =>
        if mine == planReq then
          planning.set(false)
          r.fold(
            fail("the plan"),
            p => {
              failure.set(None)
              plan.set(Some(p))
              if selected.now().forall(id => !p.issues.exists(_.id == id)) then
                selected.set(p.open.headOption.orElse(p.issues.headOption).map(_.id))
            }
          )
      }
    }

  /** Keep a decision (on the server), then plan again. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def set(issue: MergeIssue.Id, d: Option[MergeDecision], text: Option[MergeResolution.Text] = None): Unit =
    val next = decide(res.now(), issue, d, text)
    res.set(next)
    message.set(None)
    bundle.foreach(sha =>
      ApiClient.putMergeSession(sha, next).onComplete(_.fold(fail("keeping the decision"), _ => replan()))
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def chooseBundle(f: dom.File): Unit =
    failure.set(None); message.set(None); job.set(None); confirm.set(false)
    progress.set(Some(0))
    ApiClient.uploadBundle(f, p => progress.set(Some(p))).onComplete { r =>
      progress.set(None)
      r.fold(
        fail("the upload"),
        u => {
          if u.manifest.isEmpty then
            failure.set(Some(s"${f.name} is not a readable bundle: ${u.problems.map(_.sentence).mkString("; ")}"))
          else
            upload.set(Some(u))
            plan.set(None); selected.set(None)
            UiState.set(UiState.mergeBundle := Some(u.sha256.value))()
            load(u.sha256)
        }
      )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def download(): Unit =
    plan.now().foreach { p =>
      val text = MergeResolutions.Text.template(p, res.now())
      val blob = dom.Blob(js.Array(text.value), new dom.BlobPropertyBag { `type` = "application/yaml" })
      val url = dom.URL.createObjectURL(blob)
      val a = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
      a.href = url
      a.setAttribute("download", shown"resolutions-${p.planHash}.yaml")
      dom.document.body.appendChild(a)
      a.click()
      a.remove()
      dom.window.setTimeout(() => dom.URL.revokeObjectURL(url), 1000)
    }

  /** A resolution file written by the CLI (or downloaded here): read on the server, its decisions over the ones made so
    * far.
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def uploadResolutions(f: dom.File): Unit =
    f.asInstanceOf[js.Dynamic]
      .text()
      .asInstanceOf[js.Promise[String]]
      .toFuture
      .flatMap(yaml => ApiClient.parseResolutions(Api.Merge.Id(yaml)))
      .onComplete(
        _.fold(
          fail(s"the resolution file ${f.name}"),
          r => {
            val next = res.now().overriddenBy(r)
            res.set(next)
            message.set(
              Some(
                s"${Plural.count(Plural.CountN(r.resolve.size.toLong), Plural.CountSingular("decision"))} read from ${f.name}"
              )
            )
            bundle.foreach(sha =>
              ApiClient.putMergeSession(sha, next).onComplete(_.fold(fail("keeping the decisions"), _ => replan()))
            )
          }
        )
      )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def apply(): Unit =
    for sha <- bundle; p <- plan.now() do
      confirm.set(false)
      failure.set(None)
      ApiClient
        .mergeApply(MergeApplyRequest(MergeApplyRequest.Bundle(sha.value), p.planHash, res.now()))
        .onComplete(_.fold(fail("the merge"), follow))

  /** Follow an apply job (its backup comes first) until it is done. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def follow(j: MergeApplyJob): Unit =
    job.set(Some(j))
    if j.state == MergeJobState.Running then
      dom.window.setTimeout(() => ApiClient.mergeApplyStatus(j.job).onComplete(_.fold(fail("the merge"), follow)), 2000)
    else if j.result.exists(r => r.applied.applied && r.mergeId.isDefined) then
      res.set(MergeResolutions())
      replan()
    // a restart cut it short: it committed or rolled back as a whole — planning again shows which
    else if j.state == MergeJobState.Interrupted then replan()

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  def pane: HtmlElement =
    div(
      cls := "tool-pane merge-pane",
      onMountCallback(_ => bundle.foreach(load)),
      header,
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- plan.signal.combineWith(planning.signal).map {
        case (None, true) => p(cls := "muted", "Planning…")
        case (None, _)    =>
          p(
            cls := "muted",
            "Upload a bundle another collection exported (",
            code("histmersion bundle export"),
            "): its plan lists every question the merge raises, at once."
          )
        case (Some(p), _) => body(p)
      }
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def header: HtmlElement =
    div(
      cls := "merge-head",
      label(
        cls := "btn merge-upload",
        Icon(Icon.Upload),
        " bundle (.hmx)…",
        input(
          typ := "file",
          cls := "file-input",
          accept := ".hmx",
          onChange --> { e =>
            val in = e.target.asInstanceOf[dom.HTMLInputElement]
            Option(in.files).filter(_.length > 0).foreach(fs => chooseBundle(fs(0)))
            in.value = ""
          }
        )
      ),
      child.maybe <-- progress.signal.map(_.map(x => span(cls := "muted", f" uploading ${x * 100}%.0f%%"))),
      child.maybe <-- plan.signal.map(_.map { p =>
        span(
          cls := "merge-from",
          s"from ",
          strong(MergeMarkdown.who(p.from)),
          s" into ",
          strong(MergeMarkdown.who(p.into)),
          span(cls := "muted", shown" · plan ${p.planHash}" + p.asOf.fold("")(a => s" · as of ${a.value.take(10)}"))
        )
      }),
      child.maybe <-- plan.signal.map(
        _.map(_ =>
          div(
            cls := "merge-files",
            button(
              typ := "button",
              cls := "btn",
              "download decisions",
              title := "the resolution file `merge plan --resolutions` reads",
              onClick --> (_ => download())
            ),
            label(
              cls := "btn",
              "upload decisions…",
              input(
                typ := "file",
                cls := "file-input",
                accept := ".yaml,.yml",
                onChange --> { e =>
                  val in = e.target.asInstanceOf[dom.HTMLInputElement]
                  Option(in.files).filter(_.length > 0).foreach(fs => uploadResolutions(fs(0)))
                  in.value = ""
                }
              )
            ),
            button(
              typ := "button",
              cls := "btn",
              "plan again",
              disabled <-- planning.signal,
              onClick --> (_ => replan())
            )
          )
        )
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def body(p: MergePlan): HtmlElement =
    val (open, total) = counter(p)
    div(
      cls := "merge-body",
      div(
        cls := "merge-summary",
        span(cls := "merge-counter", cls("done") := open == 0, s"$open of $total to decide"),
        span(
          cls := "muted",
          s" · ${Plural.count(Plural.CountN(p.matches.sources.value.toLong), Plural.CountSingular("source"))}: ${p.matches.byUid} the same record · ${p.matches.byLedger} by ledger · " +
            s"${p.matches.byStrongKey} by DOI/file/ISBN · ${p.matches.byScore} by score · ${p.matches.unmatched} new" +
            shown" · ${Plural.count(Plural.CountN(p.changes.value.toLong), Plural.CountSingular("change"))} to write"
        ),
        child.maybe <-- planning.signal.map(b => Option.when(b)(span(cls := "muted", " · planning…")))
      ),
      Option.when(p.problems.nonEmpty)(
        div(
          cls := "merge-problems",
          role := "alert",
          strong("No decision fixes these: "),
          ul(p.problems.map(x => li(x.sentence)))
        )
      ),
      Option.when(p.resolutionErrors.nonEmpty)(
        div(
          cls := "merge-problems",
          role := "alert",
          strong("Decisions that could not be used: "),
          ul(p.resolutionErrors.map(x => li(x.sentence)))
        )
      ),
      div(
        cls := "merge-grid",
        div(cls := "merge-list", issueList(p)),
        div(
          cls := "merge-review",
          child <-- selected.signal.map(id =>
            p.issues.find(i => id.contains(i.id)) match
              case Some(i) => review(i)
              case None    => p_(cls := "muted", "Nothing to review: pick an issue on the left.")
          )
        )
      ),
      applySection(p)
    )

  private def p_(mods: Modifier[HtmlElement]*): HtmlElement = com.raquo.laminar.api.L.p(mods*)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def issueList(p: MergePlan): HtmlElement =
    div(
      input(
        typ := "search",
        cls := "field merge-filter",
        placeholder := "filter: id, kind, citekey, slug…",
        aria.label := "filter the issues",
        controlled(value <-- filter.signal, onInput.mapToValue --> filter.writer)
      ),
      children <-- filter.signal.map(t =>
        grouped(filtered(p.issues, t)).map { (g, is) =>
          div(
            cls := "merge-group",
            h4(cls := "merge-group-title", shown"${g.title} (${is.size})"),
            ul(
              cls := "merge-issues",
              is.map(i =>
                li(
                  cls := "merge-issue",
                  cls <-- selected.signal.map(s => if s.contains(i.id) then "selected" else ""),
                  cls := s"group-${g.toString.toLowerCase}",
                  button(
                    typ := "button",
                    cls := "link-btn",
                    code(i.id),
                    " ",
                    span(cls := "merge-entity", i.entity.wire),
                    " ",
                    span(i.left.orElse(i.right).map(_.label.value).getOrElse("")),
                    div(cls := "muted", i.title),
                    i.decision.map(d =>
                      div(
                        cls := "merge-decided",
                        s"→ ${d.wire.value}" + i.decidedBy.fold("")(b => s" (${b.toString.toLowerCase})")
                      )
                    ),
                    onClick --> { _ =>
                      selected.set(Some(i.id))
                      // on a phone the review sits under the list: bring it into view
                      if Viewport.narrowNow then
                        dom.window.setTimeout(
                          () => Option(dom.document.querySelector(".merge-review")).foreach(_.scrollIntoView()),
                          60
                        )
                    }
                  )
                )
              )
            )
          )
        }
      ),
      Option.when(p.info.nonEmpty)(
        htmlTag("details")(
          cls := "merge-info",
          htmlTag("summary")(s"information (${p.info.size})"),
          ul(p.info.map(x => li(x)))
        )
      ),
      Option.when(!p.sources.isEmpty)(
        htmlTag("details")(
          cls := "merge-info",
          htmlTag("summary")(
            s"sources matched (${matched(p).count(_.outcome != MatchOutcome.New)}) and new (${matched(p).count(_.outcome == MatchOutcome.New)})"
          ),
          ul(
            matched(p).map(m =>
              li(
                code(m.rightCitekey),
                m.leftCitekey.fold(span(" — new"))(l => span(" → ", SourceLink(l))),
                span(
                  cls := "muted",
                  s" · ${MatchOutcome.wireOf(m.outcome)}" + m.score.fold("")(s => f" · score $s%.1f")
                )
              )
            )
          )
        )
      )
    )

  // ── one issue ──

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def review(i: MergeIssue): HtmlElement =
    val mine = res.now().resolve.find(_.issue.value == i.id.value)
    div(
      cls := "merge-issue-review",
      h3(
        code(i.id),
        " ",
        kindText(i.kind),
        span(cls := "muted", s" · ${i.entity.wire} · ${i.severity.toString.toLowerCase}")
      ),
      p_(i.title.value),
      i.condition.map(c =>
        p_(
          cls := "muted",
          s"Applies only if ${c.issue} is decided ${MergeDecision.Kind.wireOf(c.when)}. ",
          button(
            typ := "button",
            cls := "link-btn",
            shown"go to ${c.issue}",
            onClick --> (_ => selected.set(Some(c.issue)))
          )
        )
      ),
      div(cls := "merge-sides", side("here", i.left, i.entity), side("bundle", i.right, i.entity)),
      Option.when(i.why.nonEmpty)(div(cls := "merge-why", h5("why"), ul(i.why.map(w => li(shown"$w"))))),
      i.detail.toList.flatMap(d =>
        d.fields.headOption.map(_ => fieldTable(i, d.fields)).toList ++
          d.text.map(t => articleView(i, t, mine.flatMap(_.text))).toList ++
          d.place.map(placeView).toList
      ),
      decisions(i)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def side(who: String, s: Option[IssueSide], entity: Bundle.Kind): HtmlElement =
    div(
      cls := s"merge-side merge-$who",
      h5(who),
      s.fold(span(cls := "muted", "nothing")) { x =>
        div(
          if who == "here" && entity == Bundle.Kind.Source then SourceLink(Citekey(x.label.value))
          else code(x.label),
          x.lines.map(l => div(cls := "muted", l))
        )
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fieldTable(i: MergeIssue, fs: List[IssueField]): HtmlElement =
    table(
      cls := "merge-fields",
      thead(tr(th("field"), th("here"), th("result"), th("bundle"))),
      tbody(rows(fs).map { (f, differs) =>
        tr(
          cls("differs") := differs,
          td(f.field),
          td(f.left.map(_.value).getOrElse("—")),
          resultOf(i, f, i.decision) match
            case Some(Right(v)) => td(cls := "merge-result", v)
            case Some(Left(v))  => td(cls := "merge-result pending", v)
            case None           => td(cls := "merge-result"),
          td(f.right.map(_.value).getOrElse("—"))
        )
      })
    )

  /** The diff3 view: the agreed regions folded, each conflict as here | common | bundle; and the editor the merged text
    * is written in (`file:`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def articleView(i: MergeIssue, t: IssueText, edited: Option[MergeResolution.Text]): HtmlElement =
    val draft = Var[String](edited.map(_.value).getOrElse(markedText(t).value))
    def lines(ls: List[String]) = pre(cls := "merge-lines", ls.mkString("\n"))
    div(
      cls := "merge-diff",
      h5(if t.base.isDefined then "the two versions against their common version"
      else "the two versions (no common version)"),
      t.hunks.map { h =>
        if !h.conflict.conflict then
          htmlTag("details")(
            cls := "merge-agreed",
            htmlTag("summary")(
              shown"${Plural.count(Plural.CountN(h.left.size.toLong), Plural.CountSingular("line"))} the same on both sides"
            ),
            lines(h.left.map(_.value))
          )
        else
          div(
            cls := "merge-hunk",
            div(cls := "merge-col merge-here", h5("here"), lines(h.left.map(_.value))),
            Option.when(h.base.nonEmpty || t.base.isDefined)(
              div(cls := "merge-col merge-base", h5("common version"), lines(h.base.map(_.value)))
            ),
            div(cls := "merge-col merge-bundle", h5("bundle"), lines(h.right.map(_.value)))
          )
      },
      h5("the merged text"),
      textArea(
        cls := "field merge-editor",
        aria.label := "the merged text",
        controlled(value <-- draft.signal, onInput.mapToValue --> draft.writer)
      ),
      div(
        cls := "form-row",
        child <-- Auth.writable.map(w =>
          if !w then Auth.keyNeeded("decide")
          else
            button(
              typ := "button",
              cls := "btn btn--primary",
              "use this text",
              disabled <-- draft.signal.map(hasMarkers),
              title <-- draft.signal.map(d =>
                if hasMarkers(d) then "resolve every <<<<<<< … >>>>>>> region first" else "decide file: with this text"
              ),
              onClick --> (_ =>
                set(
                  i.id,
                  Some(MergeDecision.withArg(MergeDecision.Kind.File, fileName(i))),
                  Some(MergeResolution.Text(draft.now()))
                )
              )
            )
        ),
        child.maybe <-- draft.signal.map(d =>
          Option.when(hasMarkers(d))(span(cls := "muted", " conflict markers left in the text"))
        )
      )
    )

  /** Both geometries on a small map (here and bundle in their own colours) and both spans on one axis. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def placeView(x: IssuePlace): HtmlElement =
    val spans = x.leftSpan.toList ++ x.rightSpan.toList
    div(
      cls := "merge-place",
      Option.when(x.leftGeometry.isDefined || x.rightGeometry.isDefined)(
        miniMap(x.leftGeometry.map(_.value), x.rightGeometry.map(_.value))
      ),
      div(
        cls := "merge-legend",
        span(cls := "merge-swatch merge-left"),
        " here ",
        span(cls := "merge-swatch merge-right"),
        " bundle"
      ),
      axis(spans).map { (lo, hi) =>
        div(
          cls := "merge-spans",
          List("here" -> x.leftSpan, "bundle" -> x.rightSpan).collect { case (who, Some(s)) =>
            val (at, w) = bar(s, lo, hi)
            div(
              cls := "merge-span-row",
              span(cls := "merge-span-label", who),
              div(
                cls := "merge-span-track",
                div(
                  cls := s"merge-span-bar merge-${if who == "here" then "left" else "right"}",
                  styleAttr := f"--at:$at%.2f%%;--w:$w%.2f%%"
                )
              ),
              span(cls := "muted", s" ${YearFmt.openPeriod(s.start, s.end)}")
            )
          }
        )
      }
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def miniMap(left: Option[String], right: Option[String]): HtmlElement =
    // a `var`: the map lives as long as the element; created on mount, removed on unmount
    var map: Option[MapSurface] = None
    div(
      cls := "merge-map",
      onMountUnmountCallback(
        mount = ctx =>
          val m =
            Engines.surface(ctx.thisNode.ref, MapSurface.MaxZoom(tiles.fold(12.0)(_._2.value.toDouble)), _ => None)
          m.setBaseMap(BaseMaps.current)
          val geoms = List("left" -> left, "right" -> right).collect { case (who, Some(g)) =>
            val geo = js.JSON.parse(g)
            m.shapes(
              MapSurface.ShapesId(s"merge-$who"),
              ShapeStyle(
                fillOpacity = ShapeStyle.FillOpacity(0.15),
                lineWidth = ShapeStyle.LineWidth(2.0),
                lineOpacity = ShapeStyle.LineOpacity(0.9),
                colour = Some(ShapeStyle.Colour(ThemeTokens(ThemeTokens.Name(s"--merge-$who")).value)),
                circleRadius = ShapeStyle.CircleRadius(7.0)
              )
            ).set(List(ShapeFeature(ShapeFeature.Id(who), geo)))
            GeoBounds.ofGeometry(geo)
          }.flatten
          geoms.reduceOption(_.extend(_)) match
            case Some(b) =>
              m.fitBounds(
                b,
                MapSurface.FitBoundsPadding(24),
                MapSurface.MaxZoom(9.0),
                animate = MapSurface.FitBoundsAnimate.Instant
              )
            case None =>
              val (at, zoom) = MapStart.initial(startView, None)
              m.setView(at, MapSurface.Zoom(math.max(1.0, zoom.value - 1)))
          map = Some(m)
          dom.window.setTimeout(() => map.foreach(_.resize()), 50)
        ,
        unmount = _ =>
          map.foreach(_.destroy())
          map = None
      )
    )

  /** The decisions an issue allows, as radio buttons; an argument (a citekey, an id) where the decision takes one;
    * "take back" to make it open (or its default) again.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def decisions(i: MergeIssue): HtmlElement =
    val current = chosen(res.now(), i.id)
    val name = shown"merge-decision-${i.id}"
    def radio(d: MergeDecision, labelText: Modifier[HtmlElement]*): HtmlElement =
      label(
        cls := "merge-option",
        input(
          typ := "radio",
          nameAttr := name,
          checked := i.decision.contains(d),
          onChange --> (_ => set(i.id, Some(d)))
        ),
        labelText,
        " ",
        code(d.wire)
      )
    def withArgument(k: MergeDecision.Kind): HtmlElement =
      val arg = Var[String](
        current
          .filter(_.kind == k)
          .flatMap(_.argument)
          .orElse(i.suggested.filter(_.kind == k).flatMap(_.argument))
          .map(_.value)
          .getOrElse("")
      )
      div(
        cls := "merge-option merge-option-arg",
        label(
          input(
            typ := "radio",
            nameAttr := name,
            checked := i.decision.exists(_.kind == k),
            onChange --> (_ =>
              Option(arg.now().trim)
                .filter(_.nonEmpty)
                .foreach(a => set(i.id, Some(MergeDecision.withArg(k, MergeDecision.Argument(a)))))
            )
          ),
          describe(k)
        ),
        input(
          typ := "text",
          cls := "field",
          aria.label := describe(k).value,
          placeholder := (if i.entity == Bundle.Kind.Event then "a new event id" else "a new citekey"),
          controlled(value <-- arg.signal, onInput.mapToValue --> arg.writer)
        ),
        button(
          typ := "button",
          cls := "btn",
          "set",
          onClick --> (_ =>
            Option(arg.now().trim)
              .filter(_.nonEmpty)
              .foreach(a => set(i.id, Some(MergeDecision.withArg(k, MergeDecision.Argument(a)))))
          )
        )
      )
    div(
      cls := "merge-decisions",
      h5("decide"),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("decide")
        else
          div(
            i.options.map {
              case MergeDecision.Kind.Map =>
                div(
                  List(i.left, i.right).flatten.map(s =>
                    radio(
                      MergeDecision.withArg(MergeDecision.Kind.Map, MergeDecision.Argument(s.label.value)),
                      describe(MergeDecision.Kind.Map).value,
                      " ",
                      strong(s.label)
                    )
                  )
                )
              case k @ (MergeDecision.Kind.RenameRight | MergeDecision.Kind.RenameLeft) => withArgument(k)
              case MergeDecision.Kind.File                                              =>
                label(
                  cls := "merge-option",
                  input(
                    typ := "radio",
                    nameAttr := name,
                    checked := i.decision.exists(_.kind == MergeDecision.Kind.File),
                    disabled := true
                  ),
                  describe(MergeDecision.Kind.File),
                  span(cls := "muted", " — write it above and press “use this text”")
                )
              case k => radio(MergeDecision.of(k), describe(k).value)
            },
            i.suggested.filter(_ => i.decision.isEmpty).map(s => p_(cls := "muted", "suggested: ", code(s.wire))),
            Option.when(current.isDefined)(
              button(typ := "button", cls := "btn", "take the decision back", onClick --> (_ => set(i.id, None)))
            )
          )
      }
    )

  // ── apply ──

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def applySection(p: MergePlan): HtmlElement =
    div(
      cls := "merge-apply",
      child <-- job.signal.map {
        case Some(j) if j.state == MergeJobState.Running =>
          p_(
            cls := "muted",
            s"Applying plan ${j.planHash}: ${j.elapsedMs.value / 1000} s so far — the backup is taken first."
          )
        case Some(j) if j.state == MergeJobState.Failed || j.state == MergeJobState.Interrupted =>
          div(
            j.problems
              .fold(Vector("the merge failed"))(_.sentences)
              .map(s => p_(cls := "form-error", role := "alert", s))
          )
        case Some(j) => j.result.fold(emptyNode)(appliedReport)
        case None    => emptyNode
      },
      child <-- Auth.writable.combineWith(confirm.signal, job.signal).map { (w, asking, j) =>
        val running = j.exists(_.state == MergeJobState.Running)
        if !w then Auth.keyNeeded("apply the merge")
        else if !applicable(p) then
          p_(
            cls := "muted",
            if p.changes.value == 0 && p.open.isEmpty then "Nothing to apply: the bundle is merged already."
            else "Apply waits until every question is decided and nothing is wrong."
          )
        else if asking then
          div(
            cls := "form-row merge-confirm",
            span(
              shown"Merge ${MergeMarkdown.who(p.from)} into this collection: ${Plural.count(Plural.CountN(p.changes.value.toLong), Plural.CountSingular("change"))}, " +
                shown"plan ${p.planHash}. A backup is taken first."
            ),
            button(
              typ := "button",
              cls := "btn btn--primary",
              "apply now",
              disabled := running,
              onClick --> (_ => apply())
            ),
            button(typ := "button", cls := "btn", "cancel", onClick --> (_ => confirm.set(false)))
          )
        else
          button(
            typ := "button",
            cls := "btn btn--primary merge-apply-btn",
            shown"apply the merge (${Plural.count(Plural.CountN(p.changes.value.toLong), Plural.CountSingular("change"))})",
            disabled := running,
            onClick --> (_ => confirm.set(true))
          )
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def appliedReport(r: MergeApplyResult): HtmlElement =
    val t = r.tally
    if !r.applied.applied then
      div(
        cls := "form-error",
        role := "alert",
        p_("Not merged: nothing was written."),
        ul(r.problems.map(x => li(x.sentence)))
      )
    else if r.mergeId.isEmpty then p_(cls := "form-ok", "Nothing to merge: the bundle is merged already.")
    else
      div(
        cls := "form-ok merge-report",
        p_(s"Merged as ${r.mergeId.fold("")(_.toString.take(8))}" + r.backup.fold("")(b => s" (backup $b)") + "."),
        ul(
          li(
            s"${Plural.count(Plural.CountN(t.sourcesMerged.value.toLong), Plural.CountSingular("source"))} merged (${t.sourcesByScore} by score), ${t.sourcesRenamed} renamed, ${t.aliases} old citekeys"
          ),
          li(
            s"${t.findingsMerged} findings and ${t.assetsMerged} figures merged; ${t.attestationsReanchored} attestations re-anchored, ${t.attestationsDropped} dropped"
          ),
          li(
            s"articles: ${t.articlesThreeWay} three-way, ${t.articlesFastForward} fast-forward, ${t.articlesBoth} kept twice, ${t.articlesFromFile} as edited"
          ),
          li(
            s"${t.eventsMerged} events, ${t.goalsMerged} goals, ${t.queueMerged} queue items, ${t.placesMerged} places, ${t.periodsMerged} periods merged"
          ),
          Option.when(r.reviews.nonEmpty)(li(s"queued for review: ${r.reviews.mkString(", ")}")),
          r.replanChanges.map(n =>
            li(
              if n.value == 0 then "planned again: nothing left to do"
              else
                shown"planned again: ${Plural.count(Plural.CountN(n.value.toLong), Plural.CountSingular("change"))} left — report it"
            )
          )
        )
      )
