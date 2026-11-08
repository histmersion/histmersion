package com.histmersion
package web.setup

import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{Api, Wording}
import com.histmersion.web.{ApiClient, Auth, Route, RouteLink, Router, SettingsSection}
import com.histmersion.web.settings.{Draft, FindersSection, SettingsStore, StartPlace}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.{EventImportRequest, MapArea}
import com.histmersion.api.media.MediaItem
import com.histmersion.api.instance.{MapStartConfig, UiConfig}
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.ByteCount
import com.histmersion.web.ErrorLine
import com.histmersion.api.BBox

/** The first-run setup (`/setup`): seven steps with safe defaults — finding open papers, the maps, the background data,
  * research agents and Calibre (both optional) — then Ready. Nothing is written or downloaded until it is finished;
  * "Skip the rest" writes what was chosen so far and downloads nothing. It writes the settings file through Settings'
  * own write, and is shown on a first run (no setup done, a settings file of nothing) or from Settings › Run the setup
  * again.
  */
object SetupPage:

  /** This tab left the setup without saving: it is not shown again until the tab is closed. */

  val LeftKey = "histmersion.setupLeft"

  def leave(): Unit = scala.util.Try(dom.window.sessionStorage.setItem(LeftKey, "1"))

  def left: Boolean = scala.util.Try(Option(dom.window.sessionStorage.getItem(LeftKey)).isDefined).getOrElse(false)

  private val currentAttr = htmlAttr("aria-current", StringAsIsCodec)

  /** The choices the settings say now. Pure. */
  def choicesOf(p: ConfigEdit.Page, ui: UiConfig): SetupChoices =
    def text(k: String): Option[String] =
      p.field(ConfigEdit.Field.Key(k)).flatMap(_.effective).map(v => Draft.plain(Some(Draft.Text(v.value))).value)
    SetupChoices(
      email = SetupChoices.Email(text("acquire.contact-email").getOrElse("")),
      finders = FindersSection
        .finders(ui)
        .map(f => SetupChoices.FindersKey(f.id.value) -> SetupChoices.FindersValue(f.enabled.value))
        .toMap,
      area = text("basemap.bbox").orElse(Some("world")).map(SetupChoices.Area(_)),
      start = ui.mapStart,
      agents = SetupChoices.Agents(text("agents.enabled").exists(a => Draft.isOn(Draft.Text(a)))),
      calibre = SetupChoices.Calibre(text("cli.calibre.library-dir").getOrElse(""))
    )

  /** The areas offered (`basemap.setup-areas`). */
  def areasOf(p: ConfigEdit.Page): List[MapArea] =
    p.field(ConfigEdit.Field.Key("basemap.setup-areas")).flatMap(_.effective).toList.flatMap { json =>
      scala.util
        .Try(js.JSON.parse(json.value).asInstanceOf[js.Array[String]].toList)
        .getOrElse(Nil)
        .flatMap(MapArea.parse)
    }

  def element(ui: UiConfig): HtmlElement =
    val step = Var(SetupStep.Welcome)
    val page = Var(Option.empty[Either[Problem, ConfigEdit.Page]])
    val before = Var(SetupChoices())
    val choices = Var(SetupChoices())
    val free = Var(Option.empty[ByteCount])
    val problem = Var(Option.empty[String])
    val working = Var(false)
    val finished = Var(Option.empty[List[(String, Either[String, String])]])
    val start = Var(ui.mapStart)
    start.signal.changes.foreach(s => choices.update(_.modify(_.start).setTo(s)))(using unsafeWindowOwner)

    def load(): Unit =
      ApiClient.settings().onComplete { r =>
        val e = r.toEither.left.map(SettingsStore.message)
        page.set(Some(e))
        e.foreach { p =>
          val c = choicesOf(p, ui)
          before.set(c); choices.set(c); start.set(c.start)
        }
      }
      ApiClient
        .about()
        .foreach(a => free.set(a.storage.volumes.map(_.freeBytes.value.toLong).minOption.map(ByteCount(_))))

    /** Write the choices (and the mark that the setup is done); then, unless skipping, start the downloads. */
    def finish(download: Boolean): Unit =
      val c = choices.now()
      SetupPlan.email(c.email) match
        case Left(why) => problem.set(Some(why.value)); step.set(SetupStep.Papers)
        case Right(_)  =>
          problem.set(None)
          working.set(true)
          val changes = SetupPlan.changes(c, before.now()).map((k, v) => ConfigEdit.Change(k, v))
          ApiClient
            .writeSettings(
              ConfigEdit.Write(
                changes,
                version = page.now().flatMap(_.toOption).map(p => ConfigEdit.Write.Version(p.version.value))
              )
            )
            .onComplete { r =>
              r.toEither.left.map(SettingsStore.message) match
                case Left(why) => working.set(false); problem.set(Some(why.value))
                case Right(_)  =>
                  val wanted = if download then SetupPlan.downloads(c) else Nil
                  Future
                    .sequence(
                      wanted.map(d =>
                        startDownload(d)
                          .transform(t =>
                            scala.util
                              .Success(d.label.value -> t.toEither.left.map(e => SettingsStore.message(e).value))
                          )
                      )
                    )
                    .foreach { results =>
                      working.set(false)
                      finished.set(Some(results))
                      step.set(SetupStep.Ready)
                    }
            }

    div(
      cls := "setup-page",
      onMountCallback(_ => load()),
      div(
        cls := "setup-frame",
        asideTag(
          cls := "setup-aside",
          div(cls := "setup-brand", span(cls := "brand-mark", aria.hidden := true, "H"), "Set up Histmersion"),
          ol(
            cls := "setup-steps",
            aria.label := "Setup steps",
            SetupStep.values.toList.map { s =>
              li(
                cls := "setup-step",
                cls("on") <-- step.signal.map(_ == s),
                cls("done") <-- step.signal.map(_.ordinal > s.ordinal),
                currentAttr <-- step.signal.map(x => if x == s then "step" else "false"),
                span(
                  cls := "setup-n",
                  aria.hidden := true,
                  child.text <-- step.signal.map(x => if x.ordinal > s.ordinal then "✓" else s.number.toString)
                ),
                span(cls := "setup-label", s.label.value),
                Option.when(s.optional.value)(span(cls := "setup-optional", " optional"))
              )
            }
          ),
          p(
            cls := "muted setup-note",
            "Everything you choose here can be changed later in Settings. Nothing is " +
              "downloaded until you finish."
          ),
          p(
            cls := "setup-leave",
            RouteLink(
              Route.Settings(SettingsSection.General),
              "Leave the setup without saving",
              onClick --> (_ => leave())
            )
          )
        ),
        mainTag(
          cls := "setup-main",
          idAttr := "page-main",
          child.maybe <-- Auth.writable.map(w =>
            Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded("save the setup")))
          ),
          child <-- page.signal.combineWith(step.signal, finished.signal).map {
            case (None, _, _)              => p(cls := "muted", "Loading…")
            case (Some(Left(m)), _, _)     => ErrorLine.text(Problem(s"Could not read the settings: $m"))
            case (Some(Right(p)), s, done) =>
              div(
                cls := "setup-body",
                div(cls := "muted setup-count", s"Step ${s.number} of ${SetupStep.values.length}"),
                h1(cls := "setup-title", if s == SetupStep.Welcome then "Welcome to Histmersion" else s.label.value),
                body(s, p, choices, start, free.signal, done, ui)
              )
          },
          child.maybe <-- problem.signal.map(_.map(m => ErrorLine.text(Problem(m)))),
          div(
            cls := "setup-bar",
            button(
              typ := "button",
              cls := "btn setup-back",
              "Back",
              disabled <-- step.signal.combineWith(finished.signal).map((s, d) => s.back.isEmpty || d.isDefined),
              onClick --> (_ => step.update(s => s.back.getOrElse(s)))
            ),
            child <-- finished.signal.map {
              case Some(_) => span()
              case None    =>
                button(
                  typ := "button",
                  cls := "btn btn--bare setup-skip",
                  "Skip the rest, use the defaults",
                  title := "Saves what you chose so far; downloads nothing",
                  disabled <-- working.signal.combineWith(Auth.writable).map((b, w) => b || !w),
                  onClick --> (_ => finish(download = false))
                )
            },
            child <-- step.signal.combineWith(finished.signal).map {
              case (_, Some(_)) =>
                a(
                  cls := "btn btn--primary setup-continue",
                  href := Route.Explore.path,
                  "Open the map",
                  onClick --> { ev => ev.preventDefault(); dom.window.location.assign(Route.Explore.path) }
                )
              case (SetupStep.Ready, None) =>
                button(
                  typ := "button",
                  cls := "btn btn--primary setup-continue setup-finish",
                  child.text <-- working.signal.map(w => if w then "Saving…" else "Finish and start the downloads"),
                  disabled <-- working.signal.combineWith(Auth.writable).map((b, w) => b || !w),
                  onClick --> (_ => finish(download = true))
                )
              case (s, None) =>
                button(
                  typ := "button",
                  cls := "btn btn--primary setup-continue",
                  "Continue",
                  onClick --> { _ =>
                    if s == SetupStep.Papers then
                      SetupPlan
                        .email(choices.now().email)
                        .fold(
                          why => problem.set(Some(why.value)),
                          _ => { problem.set(None); step.set(s.next.getOrElse(s)) }
                        )
                    else step.set(s.next.getOrElse(s))
                  }
                )
            }
          )
        )
      )
    )

  private def startDownload(d: SetupDownload): Future[String] = d match
    case SetupDownload.Map(area) =>
      ApiClient.basemapFetch(Basemap.FetchQuery(bbox = Some(BBox.Text(area.value)))).map(_ => "started")
    case SetupDownload.Boundaries => ApiClient.initData(fetch = Api.Overwrite(true)).map(_ => "started")
    case SetupDownload.Events     =>
      ApiClient
        .eventCatalogues()
        .flatMap(cs =>
          Future
            .sequence(
              cs.map(_.id)
                .filter(id => Set("gvp", "noaa")(id.value))
                .map(id => ApiClient.importEvents(EventImportRequest.Provider(id.value)))
            )
            .map(js => s"${js.size} started")
        )
    case SetupDownload.Pictures   => ApiClient.importMedia(MediaItem.Provider("wikidata")).map(_ => "started")
    case SetupDownload.MorePlaces =>
      Future
        .sequence(List("geonames", "wikidata").map(n => ApiClient.loadPlaces(Api.ExternalPlaces.Source(n))))
        .map(_ => "started")

  private def check(
      text: String,
      detail: String,
      on: Signal[Boolean],
      set: Boolean => Unit,
      mods: Modifier[HtmlElement]*
  ): HtmlElement =
    label(
      cls := "setup-opt",
      mods,
      input(typ := "checkbox", controlled(checked <-- on, onClick.mapToChecked --> (b => set(b)))),
      span(strong(text), span(cls := "muted", detail))
    )

  private def body(
      s: SetupStep,
      page: ConfigEdit.Page,
      choices: Var[SetupChoices],
      start: Var[MapStartConfig],
      free: Signal[Option[ByteCount]],
      done: Option[List[(String, Either[String, String])]],
      ui: UiConfig
  ): HtmlElement =
    s match
      case SetupStep.Welcome =>
        div(
          cls := "setup-text",
          p(
            "Histmersion keeps a library of papers and books on history and myth, and shows what they say on a map " +
              "and a timeline. This takes a few minutes: how to find free copies of papers, which maps to keep on this " +
              "computer, and — if you want them — research agents and your Calibre library."
          ),
          p("The safe choice is already made at every step. You can stop at any time with “Skip the rest”.")
        )
      case SetupStep.Papers =>
        div(
          cls := "setup-text",
          p(
            "When you add a paper by its DOI, Histmersion looks for a free, legal copy: the open-access indexes and " +
              "archives below. Crossref, OpenAlex and Unpaywall ask for an e-mail address so that they can write if " +
              "something goes wrong; Histmersion sends it to those three only, and to nothing else."
          ),
          div(
            cls := "form-field",
            label(forId := "setup-email", "Your e-mail address (optional)"),
            input(
              typ := "email",
              idAttr := "setup-email",
              cls := "field setup-email",
              placeholder := "you@example.org",
              controlled(
                value <-- choices.signal.map(_.email.value),
                onInput.mapToValue --> (v => choices.update(_.modify(_.email).setTo(SetupChoices.Email(v))))
              )
            )
          ),
          fieldSet(
            cls := "setup-group setup-group--grid",
            legend("Where to look"),
            FindersSection
              .finders(ui)
              .map(f =>
                check(
                  Wording.plugin(Wording.PluginId(f.id.value)),
                  "",
                  choices.signal.map(
                    _.finders
                      .getOrElse(SetupChoices.FindersKey(f.id.value), SetupChoices.FindersValue(f.enabled.value))
                      .value
                  ),
                  b =>
                    choices.update(c =>
                      c.modify(_.finders)
                        .using(_ + (SetupChoices.FindersKey(f.id.value) -> SetupChoices.FindersValue(b)))
                    )
                )
              )
          )
        )
      case SetupStep.Maps =>
        val areas = areasOf(page)
        div(
          cls := "setup-text",
          p(
            "Histmersion draws its maps from files on this computer, so they work offline and nobody sees which places " +
              "you look at. Choose how much of the world to keep."
          ),
          fieldSet(
            cls := "setup-group",
            legend("Which area?"),
            (areas.map(a => Option(SetupChoices.Area(a.bbox.value)) -> (a.label.value, a.approxGb)) :+ (Option
              .empty[SetupChoices.Area] -> ("No map download", MapArea.ApproxGb(0.0)))).map { (box, info) =>
              label(
                cls := "setup-opt setup-area",
                cls("sel") <-- choices.signal.map(_.area == box),
                input(
                  typ := "radio",
                  nameAttr := "setup-area",
                  controlled(
                    checked <-- choices.signal.map(_.area == box),
                    onClick.mapTo(box) --> (b => choices.update(_.modify(_.area).setTo(b)))
                  )
                ),
                span(
                  strong(info._1),
                  span(
                    cls := "muted",
                    if box.isEmpty then "Coastlines only. You can download later."
                    else if box.exists(_.value == "world") then
                      "Recommended. Sites and events from every continent appear on the map."
                    else "Smaller. Elsewhere the map shows coastlines only."
                  )
                ),
                span(cls := "setup-size", if box.isEmpty then "Nothing" else MapArea.size(info._2))
              )
            },
            p(cls := "muted", child.text <-- free.map(_.fold("")(b => s"Free on this disk: ${Backup.humanBytes(b)}")))
          ),
          StartPlace.chooser(start, StartPlace.ChooserName("setup-start"))
        )
      case SetupStep.Background =>
        div(
          cls := "setup-text",
          p("Data that puts the sources in time and place. Each is downloaded once and can be updated later."),
          fieldSet(
            cls := "setup-group",
            legend("What to download"),
            check(
              SetupDownload.Boundaries.label.value,
              "Borders of states through time, and the periods of the timeline.",
              choices.signal.map(_.boundaries.value),
              b => choices.update(_.modify(_.boundaries).setTo(SetupChoices.Boundaries(b)))
            ),
            check(
              SetupDownload.Events.label.value,
              "From the Smithsonian's Global Volcanism Program and NOAA's catalogues.",
              choices.signal.map(_.events.value),
              b => choices.update(_.modify(_.events).setTo(SetupChoices.Events(b)))
            ),
            check(
              SetupDownload.Pictures.label.value,
              "Pictures of the places on the map, each with its licence.",
              choices.signal.map(_.pictures.value),
              b => choices.update(_.modify(_.pictures).setTo(SetupChoices.Pictures(b)))
            ),
            check(
              SetupDownload.MorePlaces.label.value,
              "Several gigabytes; more modern places to search for.",
              choices.signal.map(_.morePlaces.value),
              b => choices.update(_.modify(_.morePlaces).setTo(SetupChoices.MorePlaces(b)))
            )
          )
        )
      case SetupStep.Agents =>
        div(
          cls := "setup-text",
          p(
            "Research agents read, enrich and write articles for you with an AI model. They are optional: everything " +
              "else works without them. Which model and which key they use is chosen later, in Settings › AI agents."
          ),
          check(
            "Run research agents on this server",
            "They start when the server is next restarted.",
            choices.signal.map(_.agents.value),
            b => choices.update(_.modify(_.agents).setTo(SetupChoices.Agents(b)))
          )
        )
      case SetupStep.Calibre =>
        div(
          cls := "setup-text",
          p(
            "If you keep books and papers in Calibre, Histmersion can read their originals from your library and add " +
              "the papers it finds to it. Optional; leave it empty if you do not use Calibre."
          ),
          div(
            cls := "form-field",
            label(forId := "setup-calibre", "Your Calibre library folder"),
            input(
              typ := "text",
              idAttr := "setup-calibre",
              cls := "field setup-calibre",
              placeholder := "~/Calibre Library",
              controlled(
                value <-- choices.signal.map(_.calibre.value),
                onInput.mapToValue --> (v => choices.update(_.modify(_.calibre).setTo(SetupChoices.Calibre(v))))
              )
            )
          )
        )
      case SetupStep.Ready =>
        done match
          case Some(results) =>
            div(
              cls := "setup-text setup-done",
              p("Saved. Histmersion is set up."),
              if results.isEmpty then p("Nothing is downloading.")
              else
                ul(
                  cls := "setup-results",
                  results.map((what, r) =>
                    li(what, ": ", r.fold(why => span(cls := "setup-failed", why), m => span(m)))
                  )
                )
              ,
              p(
                "Follow the downloads, and change any of this, in ",
                RouteLink(Route.Settings(SettingsSection.Data), "Settings › Data and downloads"),
                "."
              )
            )
          case None =>
            div(
              cls := "setup-text",
              p("Finishing saves these choices in the settings file and starts the downloads:"),
              child <-- choices.signal.map { c =>
                val ds = SetupPlan.downloads(c)
                ul(
                  cls := "setup-summary",
                  ds.map(d =>
                    li(d.label.value + (d match
                      case SetupDownload.Map(_) => s" (about ${MapArea.size(SetupPlan.mapGb(c, areasOf(page)))})"
                      case _                    => ""))
                  ) ++
                    Option.when(ds.isEmpty)(li("Nothing to download.")).toList
                )
              }
            )
