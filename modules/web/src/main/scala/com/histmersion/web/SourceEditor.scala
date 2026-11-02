package com.histmersion.web

import com.histmersion.api.{ListPatch, Wording, YearFmt, Years}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.{Period, PeriodInput}
import com.histmersion.api.library.{FacetField, Filter, MetadataPatch, SourceDetail, SourcePatch}
import com.histmersion.domain.library.Source

/** Edit a source's metadata — the [[SourcePatch]] fields a person maintains: title, authors, year, venue, DOI, status,
  * topics, regions, tags, the period it is about, and the summary. Sent as ONE `PATCH` carrying only what changed, with
  * the `updatedAt` the form was loaded from as `ifUpdatedAt`: if someone else wrote in between, the server answers 412
  * and the form reloads.
  */
object SourceEditor:

  /** The form as typed. Years are text so `1300 BCE` can be typed and shown as such. */
  final case class Draft(
      title: String,
      authors: List[String],
      year: String,
      venue: String,
      doi: String,
      status: Source.Status,
      topics: List[String],
      regions: List[String],
      tags: List[String],
      periodStart: String,
      periodEnd: String,
      summary: String
  )

  object Draft:
    def of(s: SourceDetail): Draft = Draft(
      title = s.title,
      authors = s.authors,
      year = s.year.fold("")(_.toString),
      venue = s.venue.getOrElse(""),
      doi = s.doi.getOrElse(""),
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

  private def cannotClear(what: String) =
    s"$what cannot be removed here, only changed — the patch leaves an omitted value alone"

  /** A signed year from what was typed (`1300 BCE`, `-1300`, `530 CE`, `530`), `None` for blank. */
  def year(raw: String): Either[String, Option[Long]] =
    if raw.trim.isEmpty then Right(None)
    else Years.parse(raw).map(Some(_))

  /** The patch that turns `orig` into `d`: only the changed parts, with `orig`'s `updatedAt` as the precondition —
    * `Right(None)` when nothing changed, `Left` with what is wrong, per field.
    */
  def toPatch(orig: SourceDetail, d: Draft): Either[List[(Field, String)], Option[SourcePatch]] =
    val errs = List.newBuilder[(Field, String)]
    def text(v: String, was: Option[String], field: Field, what: String): Option[String] =
      val t = v.trim
      if t.isEmpty then
        if was.exists(_.trim.nonEmpty) then errs += field -> cannotClear(what)
        None
      else Option.when(!was.map(_.trim).contains(t))(t)

    val title =
      if d.title.trim.isEmpty then { errs += Field.Title -> "a source needs a title"; None }
      else Option.when(d.title.trim != orig.title)(d.title.trim)
    val authors = Option.when(d.authors != orig.authors)(d.authors)
    val yr = d.year.trim match
      case "" =>
        if orig.year.isDefined then errs += Field.Year -> cannotClear("the year")
        None
      case y =>
        y.toIntOption.filter(v => v > 0 && v < 3000) match
          case Some(v) => Option.when(!orig.year.contains(v))(v)
          case None    => errs += Field.Year -> s"'$y' is not a publication year (e.g. 2007)"; None
    val venue = text(d.venue, orig.venue, Field.Venue, "the venue")
    val doi = text(d.doi, orig.doi, Field.Doi, "the DOI")
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
        } else Option.when(!(orig.periodStart.contains(a) && orig.periodEnd.contains(b)))(PeriodInput(a, b))
      case _ => errs += Field.Period -> "give both the start and the end of the period"; None

    val tags = Option.when(d.tags.toSet != orig.tags.toSet)(
      ListPatch(add = d.tags.filterNot(orig.tags.contains), remove = orig.tags.filterNot(d.tags.contains))
    )

    val problems = errs.result()
    if problems.nonEmpty then Left(problems)
    else
      val meta = MetadataPatch(title = title, authors = authors, year = yr, venue = venue, doi = doi, status = status)
      val patch = SourcePatch(
        metadata = Option.when(meta != MetadataPatch())(meta),
        summary = summary,
        topics = Option.when(d.topics != orig.topics)(d.topics),
        regions = Option.when(d.regions != orig.regions)(d.regions),
        period = period,
        tags = tags
      )
      if patch == SourcePatch() then Right(None) else Right(Some(patch.copy(ifUpdatedAt = orig.updatedAt)))

  /** Which field a server refusal is about, from its message (`period_start …`, `status=superseded requires …`); a
    * message about none of them is shown above the buttons.
    */
  def fieldOf(message: String): Field =
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

  def apply(ck: String): HtmlElement =
    val orig = Var(Option.empty[SourceDetail])
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
    def textIn(get: Draft => String, set: (Draft, String) => Draft, hint: String = "") =
      input(
        typ := "text",
        cls := "field",
        placeholder := hint,
        controlled(value <-- draft.signal.map(_.fold("")(get)), onInput.mapToValue --> (v => edit(set(_, v))))
      )

    def field(name: String, f: Field, control: HtmlElement, extra: Modifier[HtmlElement]*): HtmlElement =
      div(cls := "form-field", label(name, control), extra, errorFor(f))

    def chips(
        facet: FacetField,
        get: Draft => List[String],
        set: (Draft, List[String]) => Draft,
        hint: String
    ): HtmlElement =
      val zoomed = draft.zoomLazy(_.fold(Nil)(get))((od, v) => od.map(set(_, v)))
      ChipInput(
        ChipInput.Config(
          field = FacetField.label(facet),
          label = FacetField.label(facet),
          placeholder = hint,
          debounceMs = 150,
          getSuggestions = prefix => ApiClient.facets(FacetField.label(facet), Some(prefix), Filter()),
          onChange = vs => zoomed.set(vs)
        ),
        zoomed.signal
      )

    /** What a typed year reads as, next to its box: `= 1300 BCE`, or why it is not a year. */
    def yearEcho(get: Draft => String): HtmlElement =
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
        field("title", Field.Title, textIn(_.title, (d, v) => d.copy(title = v))),
        div(
          cls := "form-field",
          label("authors"),
          chips(FacetField.Authors, _.authors, (d, v) => d.copy(authors = v), "add an author…"),
          errorFor(Field.Authors)
        ),
        div(
          cls := "form-pair",
          field("year", Field.Year, textIn(_.year, (d, v) => d.copy(year = v), "e.g. 2007")),
          field(
            "status",
            Field.Status,
            select(
              cls := "field",
              controlled(
                value <-- draft.signal.map(_.fold("")(_.status.wire)),
                onChange.mapToValue --> (v => edit(_.copy(status = Source.Status.fromString(v))))
              ),
              Source.Status.values.toList.map(s => option(value := s.wire, Wording.status(s)))
            )
          )
        ),
        field("venue", Field.Venue, textIn(_.venue, (d, v) => d.copy(venue = v))),
        field("DOI", Field.Doi, textIn(_.doi, (d, v) => d.copy(doi = v), "10.…")),
        div(
          cls := "form-field",
          label("topics"),
          chips(FacetField.Topics, _.topics, (d, v) => d.copy(topics = v), "add a topic…"),
          errorFor(Field.Topics)
        ),
        div(
          cls := "form-field",
          label("regions"),
          chips(FacetField.Places, _.regions, (d, v) => d.copy(regions = v), "add a region…"),
          errorFor(Field.Regions)
        ),
        div(
          cls := "form-field",
          label("tags"),
          chips(FacetField.Tags, _.tags, (d, v) => d.copy(tags = v), "add a tag…"),
          errorFor(Field.Tags)
        ),
        div(
          cls := "form-field",
          span(cls := "field-label", "the period it is about ", span(cls := "muted", "(BCE years: 1300 BCE or -1300)")),
          div(
            cls := "form-pair",
            label(
              "from",
              textIn(_.periodStart, (d, v) => d.copy(periodStart = v), "e.g. 1300 BCE"),
              yearEcho(_.periodStart)
            ),
            label("to", textIn(_.periodEnd, (d, v) => d.copy(periodEnd = v), "e.g. 1100 BCE"), yearEcho(_.periodEnd))
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
              onInput.mapToValue --> (v => edit(_.copy(summary = v)))
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
