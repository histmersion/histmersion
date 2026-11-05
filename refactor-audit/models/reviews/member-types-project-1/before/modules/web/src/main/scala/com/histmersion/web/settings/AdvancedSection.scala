package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*

/** Settings › Advanced (all settings): every setting of the settings file, generated from the defaults
  * (reference.conf), grouped by its block with the words that document each — for what the other pages do not show. One
  * Save writes every change made here.
  */
object AdvancedSection:

  /** Does a setting match what was typed in the filter (its key or its words)? Pure. */
  def matches(f: ConfigEdit.Field, q: String): Boolean =
    val t = q.trim.toLowerCase
    t.isEmpty || f.relative.toLowerCase.contains(t) || f.doc.toLowerCase.contains(t)

  def pane: HtmlElement =
    val draft = Draft()
    val query = Var("")
    div(
      cls := "settings-section advanced-section",
      p(
        cls := "settings-lead",
        "Every setting there is, with its default. A setting a credential holds is in Keys " +
          "and passwords. Changing these is rarely needed; the settings file can also be edited by hand."
      ),
      div(
        cls := "advanced-filter",
        label(forId := "advanced-q", cls := "sr-only", "Find a setting"),
        input(
          typ := "search",
          idAttr := "advanced-q",
          cls := "field",
          placeholder := "Find a setting: backups, timeout, map…",
          controlled(value <-- query.signal, onInput.mapToValue --> query.writer)
        )
      ),
      child <-- SettingsStore.current.map {
        case None           => p(cls := "muted", "Loading…")
        case Some(Left(m))  => com.histmersion.web.ErrorLine.text(s"Could not read the settings: $m")
        case Some(Right(p)) =>
          div(
            cls := "advanced-file muted",
            p.file.fold("This server has no settings file.")(f => s"Settings file: $f"),
            p.problems.map(x => com.histmersion.web.ErrorLine.text(x))
          )
      },
      div(cls := "advanced-save", SettingsForm.saveBar(draft)),
      children <-- SettingsStore.current
        .map(_.flatMap(_.toOption).toList.flatMap(_.sections))
        .map(_.map(s => block(s, draft, query.signal)))
    )

  /** The blocks whose names say little as words. */
  private val Names = Map(
    "db" -> "Database",
    "ui" -> "The web page",
    "cli" -> "The command line",
    "init" -> "First-run data",
    "agents" -> "AI agents",
    "acquire" -> "Finding sources",
    "basemap" -> "Background map",
    "config" -> "Reloading the settings",
    "runtime" -> "The bundled database and converter",
    "media" -> "Pictures",
    "places" -> "Places",
    "query" -> "Read-only queries"
  )

  /** A block's name for a person. Pure. */
  def sectionName(id: String): String = Names.getOrElse(id, Wording.humanize(id))

  /** One block, folded until opened (or while the filter finds something in it). */
  private def block(s: ConfigEdit.Section, draft: Draft, query: Signal[String]): HtmlElement =
    val open = Var(false)
    val shown = s.fields.filter(_.credential.isEmpty)
    detailsTag(
      cls := "advanced-block",
      dataAttr("section") := s.id,
      htmlAttr("open", com.raquo.laminar.codecs.BooleanAsAttrPresenceCodec) <-- open.signal
        .combineWith(query)
        .map((o, q) => o || q.trim.nonEmpty && shown.exists(matches(_, q))),
      summaryTag(
        cls := "advanced-summary",
        onClick.preventDefault --> (_ => open.update(!_)),
        span(cls := "advanced-name", sectionName(s.id)),
        span(cls := "muted", s" · ${s.fields.size}"),
        Option.when(s.doc.nonEmpty)(span(cls := "advanced-doc muted", s" — ${s.doc}"))
      ),
      children <-- open.signal.combineWith(query).map { (o, q) =>
        if !o && q.trim.isEmpty then Nil
        else shown.filter(matches(_, q)).map(f => SettingControl(f, draft, Some(f.relative.stripPrefix(s"${s.id}."))))
      }
    )
