package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{ListPatch, Wording, YearFmt, Years}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, MetadataPatch}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Facet

/** Edit a source's metadata — the [[SourcePatch]] fields a person maintains: title, authors, year, venue, DOI, status,
  * topics, regions, tags, the period it is about, and the summary. Sent as ONE `PATCH` carrying only what changed, with
  * the `updatedAt` the form was loaded from as `ifUpdatedAt`: if someone else wrote in between, the server answers 412
  * and the form reloads.
  */
object SourceEditor:
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The form as typed. Years are text so `1300 BCE` can be typed and shown as such. */


  final case class Draft(
      title: Draft.Title,
      authors: List[Draft.Authors],
      year: Draft.Year,
      venue: Draft.Venue,
      doi: Draft.Doi,
      status: Source.Status,
      topics: List[Draft.Topics],
      regions: List[Draft.Regions],
      tags: List[Draft.Tags],
      periodStart: Draft.PeriodStart,
      periodEnd: Draft.PeriodEnd,
      summary: Draft.Summary
  )

  object Draft:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Venue = Venue.T
    object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doi = Doi.T
    object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(s: com.histmersion.api.library.Source.Detail): Draft = Draft(
      title = s.title.value,
      authors = s.authors.map(_.value),
      year = s.year.map(_.value.toInt).fold("")(_.toString),
      venue = s.venue.map(_.value).getOrElse(""),
      doi = s.doi.map(_.value).getOrElse(""),
      status = s.status,
      topics = s.topics,
      regions = s.regions,
      tags = s.tags,
      periodStart = s.periodStart.fold("")(YearFmt(_)),
      periodEnd = s.periodEnd.fold("")(YearFmt(_)),
      summary = s.summary.getOrElse("")
    )

  /** The form field a message is shown next to. */
  enum Field:
    case Title, Authors, Year, Venue, Doi, Status, Topics, Regions, Tags, Period, Summary, Form

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  private def cannotClear(what: com.histmersion.api.UiLabels.Text) =
    s"$what cannot be removed here, only changed — the patch leaves an omitted value alone"

  /** A signed year from what was typed (`1300 BCE`, `-1300`, `530 CE`, `530`), `None` for blank. */
  def year(raw: com.histmersion.api.Years.Text): Either[com.histmersion.web.SourceEditor.Problem, Option[com.histmersion.api.Years.Astronomical]] =
    if raw.trim.isEmpty then Right(None)
    else Years.parse(raw).map(Some(_))

  /** The patch that turns `orig` into `d`: only the changed parts, with `orig`'s `updatedAt` as the precondition —
    * `Right(None)` when nothing changed, `Left` with what is wrong, per field.
    */
  def toPatch(
      orig: com.histmersion.api.library.Source.Detail,
      d: Draft
  ): Either[List[(Field, com.histmersion.api.UiLabels.Text)], Option[com.histmersion.api.library.Source.Patch]] =
    val errs = List.newBuilder[(Field, String)]
    def text(v: com.histmersion.api.UiLabels.Text, was: Option[com.histmersion.api.UiLabels.Text], field: Field, what: com.histmersion.api.UiLabels.Text): Option[com.histmersion.api.UiLabels.Text] =
      val t = v.trim
      if t.isEmpty then
        if was.exists(_.trim.nonEmpty) then errs += field -> cannotClear(what)
        None
      else Option.when(!was.map(_.trim).contains(t))(t)

    val title =
      if d.title.trim.isEmpty then { errs += Field.Title -> "a source needs a title"; None }
      else Option.when(d.title.trim != orig.title.value)(d.title.trim)
    val authors = Option.when(d.authors != orig.authors.map(_.value))(d.authors)
    val yr = d.year.trim match
      case "" =>
        if orig.year.map(_.value.toInt).isDefined then errs += Field.Year -> cannotClear("the year")
        None
      case y =>
        y.toIntOption.filter(v => v > 0 && v < 3000) match
          case Some(v) => Option.when(!orig.year.map(_.value.toInt).contains(v))(v)
          case None    => errs += Field.Year -> s"'$y' is not a publication year (e.g. 2007)"; None
    val venue = text(d.venue, orig.venue.map(_.value), Field.Venue, "the venue")
    val doi = text(d.doi, orig.doi.map(_.value), Field.Doi, "the DOI")
    doi.foreach(v => if !v.matches("^10\\.\\S+/\\S+$") then errs += Field.Doi -> s"'$v' is not a DOI (10.…/…)")
    val status = Option.when(d.status != orig.status)(d.status)
    val summary = text(d.summary, orig.summary, Field.Summary, "the summary")

    val period = (year(d.periodStart), year(d.periodEnd)) match
      case (Left(e), _)               => errs += Field.Period -> e; None
      case (_, Left(e))               => errs += Field.Period -> e; None
      case (Right(None), Right(None)) =>
        if orig.periodStart.isDefined then errs += Field.Period -> cannotClear("the period")
        None
      case (Right(Some(a)), Right(Some(b))) =>
        if a > b then {
          errs += Field.Period -> s"the period starts (${YearFmt(a)}) after it ends (${YearFmt(b)})"; None
        } else Option.when(!(orig.periodStart.contains(a) && orig.periodEnd.contains(b)))(Period.Input(a, b))
      case _ => errs += Field.Period -> "give both the start and the end of the period"; None

    val tags = Option.when(d.tags.toSet != orig.tags.toSet)(
      ListPatch(add = d.tags.filterNot(orig.tags.contains), remove = orig.tags.filterNot(d.tags.contains))
    )

    val problems = errs.result()
    if problems.nonEmpty then Left(problems)
    else
      val meta = MetadataPatch(title = (title).map(com.histmersion.api.library.Title.apply), authors = (authors).map(_.map(com.histmersion.api.library.AuthorName.apply)), year = (yr).map(com.histmersion.api.catalogue.Year.apply), venue = (venue).map(com.histmersion.api.library.Source.Venue.apply), doi = (doi).map(com.histmersion.api.library.Doi.apply), status = status)
      val patch = com.histmersion.api.library.Source.Patch(
        metadata = Option.when(meta != MetadataPatch())(meta),
        summary = summary,
        topics = Option.when(d.topics != orig.topics)(d.topics),
        regions = Option.when(d.regions != orig.regions)(d.regions),
        period = period,
        tags = tags
      )
      if patch == com.histmersion.api.library.Source.Patch() then Right(None)
      else Right(Some(patch.modify(_.ifUpdatedAt).setTo(orig.updatedAt)))

  /** Which field a server refusal is about, from its message (`period_start …`, `status=superseded requires …`); a
    * message about none of them is shown above the buttons.
    */
  def fieldOf(message: com.histmersion.web.SourceEditor.Problem): Field =
    val m = message.toLowerCase
    List(
      "period" -> Field.Period,
      "status" -> Field.Status,
      "title" -> Field.Title,
      "doi" -> Field.Doi,
      "venue" -> Field.Venue,
      "author" -> Field.Authors,
      "topic" -> Field.Topics,
      "region" -> Field.Regions,
      "tag" -> Field.Tags,
      "summary" -> Field.Summary,
      "year" -> Field.Year
    ).collectFirst { case (k, f) if m.contains(k) => f }.getOrElse(Field.Form)

  val ConflictMessage =
    "Someone else changed this source after you opened it. The form now shows the current version — " +
      "make your change again and save."

  // ── the component (one per open editor window) ──────────────────────────────────────────────────

  def apply(ck: com.histmersion.web.Preview.Target.Source.Citekey): HtmlElement =
    val orig = Var(Option.empty[com.histmersion.api.library.Source.Detail])
    // the form's own model until it is saved; it shadows nothing in UiState
    val draft = Var(Option.empty[Draft])
    val errors = Var(List.empty[(Field, String)])
    val notice = Var(Option.empty[String])
    val saving = Var(false)

    def load(then0: Option[String] = None): Unit =
      ApiClient.getSource(ck, body = false).onComplete { r =>
        r.fold(
          e => errors.set(List(Field.Form -> s"Could not load $ck: ${e.getMessage}")),
          sw =>
            orig.set(Some(sw.source))
            draft.set(Some(Draft.of(sw.source)))
            notice.set(then0)
            WindowManager.retitle(WindowKey.EditSource(ck), s"Edit · ${sw.source.title}")
        )
      }

    def save(): Unit =
      (orig.now(), draft.now()) match
        case (Some(o), Some(d)) =>
          toPatch(o, d) match
            case Left(es)       => errors.set(es); notice.set(None)
            case Right(None)    => errors.set(Nil); notice.set(Some("Nothing changed."))
            case Right(Some(p)) =>
              errors.set(Nil)
              notice.set(None)
              saving.set(true)
              ApiClient.patchSource(ck, p).onComplete { r =>
                saving.set(false)
                r.fold(
                  {
                    case f: ApiFailure if f.conflict =>
                      errors.set(List(Field.Form -> ConflictMessage))
                      load()
                    case f: ApiFailure if f.unauthorized => errors.set(List(Field.Form -> f.error.message))
                    case f: ApiFailure => errors.set(List(fieldOf(f.error.message) -> f.error.message))
                    case e             => errors.set(List(Field.Form -> e.getMessage))
                  },
                  sw =>
                    orig.set(Some(sw.source))
                    draft.set(Some(Draft.of(sw.source)))
                    notice.set(Some("Saved."))
                    Preview.forget(ck)
                    WindowManager.reload(WindowKey.Source(ck))
                )
              }
        case _ => ()

    def errorFor(f: Field): Modifier[HtmlElement] =
      child.maybe <-- errors.signal.map(_.collectFirst { case (`f`, m) =>
        div(cls := "field-error", role := "alert", m)
      })

    def edit(f: Draft => Draft): Unit = draft.update(_.map(f))
    def textIn(get: Draft => com.histmersion.api.UiLabels.Text, set: (Draft, com.histmersion.api.UiLabels.Text) => Draft, hint: com.histmersion.api.UiLabels.Text = "") =
      input(
        typ := "text",
        cls := "field",
        placeholder := hint,
        controlled(value <-- draft.signal.map(_.fold("")(get)), onInput.mapToValue --> (v => edit(set(_, v))))
      )

    def field(name: com.histmersion.web.SourceEditor.FieldName, f: Field, control: HtmlElement, extra: Modifier[HtmlElement]*): HtmlElement =
      div(cls := "form-field", label(name, control), extra, errorFor(f))

    def chips(
        facet: Facet.Field,
        get: Draft => List[com.histmersion.api.UiLabels.Text],
        set: (Draft, List[com.histmersion.api.UiLabels.Text]) => Draft,
        hint: com.histmersion.api.UiLabels.Text
    ): HtmlElement =
      val zoomed = draft.zoomLazy(_.fold(Nil)(get))((od, v) => od.map(set(_, v)))
      ChipInput(
        ChipInput.Config(
          field = Facet.Field.label(facet),
          label = Facet.Field.label(facet),
          placeholder = hint,
          debounceMs = 150,
          getSuggestions = prefix => ApiClient.facets(Facet.Field.label(facet), Some(prefix), Filter()),
          onChange = vs => zoomed.set(vs)
        ),
        zoomed.signal
      )

    /** What a typed year reads as, next to its box: `= 1300 BCE`, or why it is not a year. */
    def yearEcho(get: Draft => com.histmersion.api.Years.Text): HtmlElement =
      span(
        cls := "year-echo muted",
        child.text <-- draft.signal.map(
          _.fold("")(d =>
            year(get(d)) match
              case Right(Some(v)) => s"= ${YearFmt(v)}"
              case Right(None)    => ""
              case Left(_)        => "not a year"
          )
        )
      )

    div(
      cls := "tool-pane edit-pane",
      onMountCallback(_ => load()),
      child <-- draft.signal.map(_.isEmpty).map { loading =>
        if loading then p(cls := "muted", "Loading…") else emptyNode
      },
      form(
        cls := "edit-form",
        hidden <-- draft.signal.map(_.isEmpty),
        onSubmit.preventDefault --> (_ => if Auth.writableNow then save()),
        p(cls := "muted", "Editing ", SourceLink(ck), " — only what you change is sent."),
        field("title", Field.Title, textIn(_.title, (d, v) => d.modify(_.title).setTo(v))),
        div(
          cls := "form-field",
          label("authors"),
          chips(Facet.Field.Authors, _.authors, (d, v) => d.modify(_.authors).setTo(v), "add an author…"),
          errorFor(Field.Authors)
        ),
        div(
          cls := "form-pair",
          field("year", Field.Year, textIn(_.year, (d, v) => d.modify(_.year).setTo(v), "e.g. 2007")),
          field(
            "status",
            Field.Status,
            select(
              cls := "field",
              controlled(
                value <-- draft.signal.map(_.fold("")(_.status.wire)),
                onChange.mapToValue --> (v => edit(_.modify(_.status).setTo(Source.Status.fromString(v))))
              ),
              Source.Status.values.toList.map(s => option(value := s.wire, Wording.status(s)))
            )
          )
        ),
        field("venue", Field.Venue, textIn(_.venue, (d, v) => d.modify(_.venue).setTo(v))),
        field("DOI", Field.Doi, textIn(_.doi, (d, v) => d.modify(_.doi).setTo(v), "10.…")),
        div(
          cls := "form-field",
          label("topics"),
          chips(Facet.Field.Topics, _.topics, (d, v) => d.modify(_.topics).setTo(v), "add a topic…"),
          errorFor(Field.Topics)
        ),
        div(
          cls := "form-field",
          label("regions"),
          chips(Facet.Field.Places, _.regions, (d, v) => d.modify(_.regions).setTo(v), "add a region…"),
          errorFor(Field.Regions)
        ),
        div(
          cls := "form-field",
          label("tags"),
          chips(Facet.Field.Tags, _.tags, (d, v) => d.modify(_.tags).setTo(v), "add a tag…"),
          errorFor(Field.Tags)
        ),
        div(
          cls := "form-field",
          span(cls := "field-label", "the period it is about ", span(cls := "muted", "(BCE years: 1300 BCE or -1300)")),
          div(
            cls := "form-pair",
            label(
              "from",
              textIn(_.periodStart, (d, v) => d.modify(_.periodStart).setTo(v), "e.g. 1300 BCE"),
              yearEcho(_.periodStart)
            ),
            label(
              "to",
              textIn(_.periodEnd, (d, v) => d.modify(_.periodEnd).setTo(v), "e.g. 1100 BCE"),
              yearEcho(_.periodEnd)
            )
          ),
          errorFor(Field.Period)
        ),
        field(
          "summary",
          Field.Summary,
          textArea(
            cls := "field",
            rows := 5,
            controlled(
              value <-- draft.signal.map(_.fold("")(_.summary)),
              onInput.mapToValue --> (v => edit(_.modify(_.summary).setTo(v)))
            )
          )
        ),
        errorFor(Field.Form),
        child.maybe <-- notice.signal.map(_.map(n => p(cls := "form-ok", role := "status", n))),
        div(
          cls := "form-actions",
          child <-- Auth.writable.map { w =>
            if !w then Auth.keyNeeded("save changes")
            else
              button(
                typ := "submit",
                cls := "btn btn--primary",
                disabled <-- saving.signal,
                child.text <-- saving.signal.map(s => if s then "saving…" else "save")
              )
          },
          button(
            typ := "button",
            cls := "btn",
            "revert",
            onClick --> { _ =>
              orig.now().foreach(o => draft.set(Some(Draft.of(o))))
              errors.set(Nil)
              notice.set(None)
            }
          )
        )
      )
    )
