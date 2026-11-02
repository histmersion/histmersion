package com.histmersion.web

import com.histmersion.domain.library.Citekey

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{Filter, IngestAccepted, IngestParams}

/** Upload a document for OCR ingest (`POST /api/ingest`): pick or drop a file, give it a citekey and the metadata the
  * endpoint takes, watch the upload, then follow the OCR job in the jobs window. The form can start from an item of the
  * acquisition queue — picked here, or by "Upload…" on the item — which fills in what the item names and has the OCR
  * job tick it off when it succeeds.
  */
object UploadView:

  /** The form, as a person fills it in: text as typed, checked by [[validate]] before it is sent. `forItem` is the
    * acquisition item this upload fulfils (picked in the form, or "Upload…" on the item). It travels with the upload as
    * `acquireItem`, and the server's OCR job ticks it off as `ingested as [[citekey]]` when — and only when — the job
    * succeeds.
    */
  final case class Draft(
      fileName: Option[String] = None,
      citekey: String = "",
      title: String = "",
      authors: List[String] = Nil,
      year: String = "",
      doi: String = "",
      quality: Boolean = false,
      force: Boolean = false,
      forItem: Option[Queue.QueueItem] = None
  )

  /** A form field a message belongs next to. */
  enum Field:
    case File, Citekey, Year, Doi, Form

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A citekey suggested from a file name: `Anthony 2007 - The Horse.pdf` → `anthony_2007_the_horse`. Always a valid
    * [[Citekey]] or `None` (the reader types one).
    */
  def suggestCitekey(fileName: String): Option[String] =
    val stem = fileName.reverse.dropWhile(_ != '.').drop(1).reverse match
      case "" => fileName
      case s  => s
    val slug = stem.toLowerCase
      .map(c => if c.isLetterOrDigit && c < 128 then c else '_')
      .replaceAll("_+", "_")
      .stripPrefix("_")
      .stripSuffix("_")
      .dropWhile(!_.isLetter)
      .take(60)
      .stripSuffix("_")
    Option(slug).filter(Citekey.valid)

  /** The parameters to send, or what is wrong, field by field. */
  def validate(d: Draft): Either[List[(Field, String)], IngestParams] =
    val file = Option.when(d.fileName.isEmpty)(Field.File -> "choose a file to upload")
    val ck = Citekey.parse(d.citekey).left.toOption.map(Field.Citekey -> _)
    val year = d.year.trim match
      case "" => Right(None)
      case y  =>
        y.toIntOption
          .filter(v => v > 0 && v < 3000)
          .toRight(Field.Year -> s"'$y' is not a publication year (e.g. 2007)")
          .map(Some(_))
    val doi = d.doi.trim match
      case ""                                                                   => Right(None)
      case s if s.matches("(?i)^(https?://(dx\\.)?doi\\.org/)?10\\.\\S+/\\S+$") =>
        Right(Some(s.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")))
      case s => Left(Field.Doi -> s"'$s' is not a DOI (e.g. 10.1017/S0003598X00000001)")
    val errors = file.toList ++ ck.toList ++ year.left.toOption.toList ++ doi.left.toOption.toList
    if errors.nonEmpty then Left(errors)
    else
      Right(
        IngestParams(
          citekey = d.citekey.trim,
          filename = d.fileName,
          quality = d.quality,
          force = d.force,
          title = Some(d.title.trim).filter(_.nonEmpty),
          authors = d.authors,
          year = year.toOption.flatten,
          doi = doi.toOption.flatten,
          acquireItem = d.forItem.map(_.id)
        )
      )

  /** Pure: the form after picking `item` as the one this upload fulfils. What the item names fills the fields the
    * reader has NOT typed yet; the chosen file and anything typed stay. Picking another item replaces the link, and
    * only the fields the previous pick filled are refilled.
    */
  def withItem(d: Draft, item: Queue.QueueItem): Draft =
    val from = QueueItemRef.uploadDraft(item)
    val prev = d.forItem.map(QueueItemRef.uploadDraft)
    // the citekey suggested from the file's name was not typed either: the item's is better
    val suggested = d.fileName.flatMap(suggestCitekey)
    // a field is the reader's own when it is non-blank and not what a pick or the file name put there
    def keep(get: Draft => String): String =
      val mine = get(d)
      if mine.trim.isEmpty || prev.exists(p => get(p) == mine) || suggested.contains(mine) then get(from)
      else mine
    val authors =
      if d.authors.isEmpty || prev.exists(_.authors == d.authors) then from.authors else d.authors
    d.copy(
      citekey = keep(_.citekey),
      title = keep(_.title),
      authors = authors,
      year = keep(_.year),
      doi = keep(_.doi),
      forItem = Some(item)
    )

  /** The picker's label for an item: its id and the first line of its text, shortened. */
  def itemLabel(item: Queue.QueueItem, max: Int = 110): String =
    val line = item.text.linesIterator.nextOption().getOrElse("").replace("**", "").trim
    s"#${item.id} " + (if line.length > max then line.take(max - 1).trim + "…" else line)

  /** The item a picker entry names (`#42 …`), among the items offered. */
  def pickedItem(typed: String, items: List[Queue.QueueItem]): Option[Queue.QueueItem] =
    """^\s*#(\d+)\b""".r
      .findFirstMatchIn(typed)
      .flatMap(m => m.group(1).toIntOption)
      .flatMap(id => items.find(_.id == id))

  /** The server's refusal, placed next to the field it is about. */
  def fieldOf(f: ApiFailure): Field =
    val m = f.error.message.toLowerCase
    if f.duplicate then Field.File
    else if m.contains("citekey") then Field.Citekey
    else Field.Form

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  /** The window's form — its own model until it is sent (it shadows nothing in [[UiState]]). */
  private val draft = Var(Draft())
  // the chosen file is a browser object, not form text; kept beside the draft, named in it
  private val file = Var(Option.empty[dom.File])
  private val errors = Var(List.empty[(Field, String)])
  private val progress = Var(Option.empty[Double])

  /** The accepted upload, and the acquisition item its job will tick off. */
  private val accepted = Var(Option.empty[(IngestAccepted, Option[Int])])
  private val dragging = Var(false)

  /** The open acquisition items the picker offers; reloaded when the list changes elsewhere. */
  private val openItems = Var(List.empty[Queue.QueueItem])

  /** What is typed in the picker, until it names an item. */
  private val pickText = Var("")
  private val authors = draft.zoomLazy(_.authors)((d, a) => d.copy(authors = a))

  /** Open the window with this form filled in — from an acquisition item's "Upload…". A file chosen for an earlier form
    * is dropped: it belonged to that one.
    */
  def prefill(d: Draft): Unit =
    draft.set(d)
    pickText.set("")
    file.set(None)
    errors.set(Nil)
    accepted.set(None)
    WindowManager.open(WindowKey.Upload)

  private def choose(f: dom.File): Unit =
    file.set(Some(f))
    accepted.set(None)
    errors.update(_.filterNot(_._1 == Field.File))
    draft.update(d =>
      d.copy(
        fileName = Some(f.name),
        citekey = if d.citekey.trim.isEmpty then suggestCitekey(f.name).getOrElse("") else d.citekey
      )
    )

  private def submit(): Unit =
    val sent = draft.now()
    validate(sent) match
      case Left(es)      => errors.set(es)
      case Right(params) =>
        file.now().foreach { f =>
          errors.set(Nil)
          progress.set(Some(0))
          ApiClient.ingest(params, f, p => progress.set(Some(p))).onComplete { r =>
            progress.set(None)
            r.fold(
              {
                case af: ApiFailure if af.unauthorized => errors.set(List(Field.Form -> af.error.message))
                case af: ApiFailure                    => errors.set(List(fieldOf(af) -> af.error.message))
                case e                                 => errors.set(List(Field.Form -> e.getMessage))
              },
              ok =>
                // the item is NOT ticked off here: the OCR job does that when it succeeds
                accepted.set(Some(ok -> sent.forItem.map(_.id)))
                file.set(None)
                draft.set(Draft())
                pickText.set("")
                WindowManager.open(WindowKey.Jobs)
                JobsView.track(ok.jobId)
            )
          }
        }

  private def errorFor(f: Field): Signal[Option[String]] =
    errors.signal.map(_.collectFirst { case (`f`, m) => m })

  private def fieldError(f: Field): Modifier[HtmlElement] =
    child.maybe <-- errorFor(f).map(_.map(m => div(cls := "field-error", role := "alert", m)))

  private def textField(
      label0: String,
      get: Draft => String,
      set: (Draft, String) => Draft,
      hint: String,
      err: Option[Field] = None
  ): HtmlElement =
    div(
      cls := "form-field",
      label(
        label0,
        input(
          typ := "text",
          cls := "field",
          placeholder := hint,
          controlled(value <-- draft.signal.map(get), onInput.mapToValue --> (v => draft.update(set(_, v))))
        )
      ),
      err.map(fieldError)
    )

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  private def loadItems(): Unit =
    ApiClient
      .queueList(Queue.Kind.Acquire.label, false)
      .foreach(l => openItems.set(l.items.filter(_.status == Queue.Open)))

  /** "from the acquisition queue…": type to narrow, pick to fill the form in. */
  private def itemPicker: HtmlElement =
    val optionsId = "upload-acquire-items"
    div(
      cls := "form-field",
      label(
        "from the acquisition queue",
        input(
          typ := "search",
          cls := "field",
          listId := optionsId,
          placeholder := "optional — type to find an item to fulfil…",
          controlled(
            value <-- pickText.signal,
            onInput.mapToValue --> { v =>
              pickedItem(v, openItems.now()) match
                case Some(item) =>
                  draft.update(withItem(_, item))
                  pickText.set("")
                case None => pickText.set(v)
            }
          )
        )
      ),
      htmlTag("datalist")(
        idAttr := optionsId,
        children <-- openItems.signal.map(_.map(i => option(value := itemLabel(i))))
      )
    )

  def pane: HtmlElement =
    div(
      cls := "tool-pane upload-pane",
      onMountCallback(_ => loadItems()),
      QueueView.changed.events.filter(_ == Queue.Kind.Acquire) --> (_ => loadItems()),
      itemPicker,
      // the drop zone doubles as the file picker
      label(
        cls := "drop-zone",
        cls("dragging") <-- dragging.signal,
        onDragOver.preventDefault --> (_ => dragging.set(true)),
        onDragEnter.preventDefault --> (_ => dragging.set(true)),
        onDragLeave --> (_ => dragging.set(false)),
        onDrop.preventDefault --> { e =>
          dragging.set(false)
          Option(e.dataTransfer).flatMap(dt => Option(dt.files)).filter(_.length > 0).foreach(fs => choose(fs(0)))
        },
        Icon(Icon.Upload),
        child.text <-- file.signal.map {
          case Some(f) => s" ${f.name} · ${sizeLabel(f.size)}"
          case None    => " drop a PDF or EPUB here, or click to choose one"
        },
        input(
          typ := "file",
          cls := "file-input",
          accept := ".pdf,.epub,.djvu,.docx,.html,.txt,application/pdf",
          onChange --> { e =>
            val in = e.target.asInstanceOf[dom.HTMLInputElement]
            Option(in.files).filter(_.length > 0).foreach(fs => choose(fs(0)))
            in.value = ""
          }
        )
      ),
      fieldError(Field.File),
      child.maybe <-- draft.signal
        .map(_.forItem)
        .distinct
        .map(
          _.map(item =>
            div(
              cls := "pane-note muted",
              s"For acquisition item ${item.id} — the OCR job ticks it off as ingested when it succeeds. ",
              a(href := "#", "don't link it", onClick.preventDefault --> (_ => draft.update(_.copy(forItem = None))))
            )
          )
        ),
      textField("citekey", _.citekey, (d, v) => d.copy(citekey = v), "e.g. anthony2007_horse", Some(Field.Citekey)),
      textField("title", _.title, (d, v) => d.copy(title = v), "optional — read from the document otherwise"),
      div(
        cls := "form-field",
        label("authors"),
        ChipInput(
          ChipInput.Config(
            field = "authors",
            label = "authors",
            placeholder = "add an author…",
            debounceMs = 150,
            getSuggestions = prefix => ApiClient.facets("authors", Some(prefix), Filter()),
            onChange = vs => draft.update(_.copy(authors = vs))
          ),
          authors.signal
        )
      ),
      div(
        cls := "form-pair",
        textField("year", _.year, (d, v) => d.copy(year = v), "e.g. 2007", Some(Field.Year)),
        textField("DOI", _.doi, (d, v) => d.copy(doi = v), "10.…", Some(Field.Doi))
      ),
      div(
        cls := "form-checks",
        checkbox("slower, higher-quality OCR", _.quality, (d, v) => d.copy(quality = v)),
        checkbox(
          "store it even if the same file is already stored under another citekey",
          _.force,
          (d, v) => d.copy(force = v)
        )
      ),
      fieldError(Field.Form),
      child <-- progress.signal.map {
        case Some(p) =>
          div(
            cls := "upload-progress",
            htmlTag("progress")(
              htmlAttr("max", com.raquo.laminar.codecs.StringAsIsCodec) := "1",
              htmlAttr("value", com.raquo.laminar.codecs.StringAsIsCodec) := p.toString
            ),
            span(cls := "muted", f" uploading ${p * 100}%.0f%%")
          )
        case None => emptyNode
      },
      child.maybe <-- accepted.signal.map(
        _.map((ok, item) =>
          p(
            cls := "form-ok",
            s"Uploaded — OCR job ${ok.jobId.take(8)} is ${ok.status} for ",
            code(ok.citekey),
            ". Follow it in ",
            a(href := "#", "background jobs", onClick.preventDefault --> (_ => WindowManager.open(WindowKey.Jobs))),
            ".",
            item.map(i => s" Acquisition item $i is ticked off when the job succeeds.")
          )
        )
      ),
      div(
        cls := "form-actions",
        child <-- Auth.writable.map { w =>
          if !w then Auth.keyNeeded("upload")
          else
            button(
              typ := "button",
              cls := "btn btn--primary",
              "upload",
              disabled <-- progress.signal.map(_.isDefined),
              onClick --> (_ => submit())
            )
        }
      )
    )

  private def checkbox(text: String, get: Draft => Boolean, set: (Draft, Boolean) => Draft): HtmlElement =
    label(
      cls := "check",
      input(
        typ := "checkbox",
        controlled(checked <-- draft.signal.map(get), onClick.mapToChecked --> (v => draft.update(set(_, v))))
      ),
      text
    )

  /** `1.4 MB`, `820 kB`. */
  def sizeLabel(bytes: Double): String =
    if bytes >= 1e6 then f"${bytes / 1e6}%.1f MB" else f"${math.max(1.0, bytes / 1e3)}%.0f kB"
