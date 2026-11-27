package com.histmersion
package web.settings
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.Wording
import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ErrorLine
import com.histmersion.web.{:=, given}
import com.histmersion.lint.{tolerated, LintRule}

/** Settings › Advanced (all settings): every setting of the settings file, generated from the defaults
  * (reference.conf), grouped by its block with the words that document each — for what the other pages do not show. One
  * Save writes every change made here.
  */
object AdvancedSection:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type SectionName = SectionName.T
  object SectionName extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  /** Does a setting match what was typed in the filter (its key or its words)? Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(f: ConfigEdit.Field, q: Query): Boolean =
    val t = q.value.trim.toLowerCase
    t.isEmpty || f.relative.value.toLowerCase.contains(t) || f.doc.value.toLowerCase.contains(t)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
        case None          => p(cls := "muted", "Loading…")
        case Some(Left(m)) =>
          ErrorLine.text(Problem.Unclassified(Legacy.Text(s"Could not read the settings: ${m.sentence}")))
        case Some(Right(p)) =>
          div(
            cls := "advanced-file muted",
            p.file.fold("This server has no settings file.")(f => shown"Settings file: $f"),
            p.problems.map(x => ErrorLine.text(Problem.Unclassified(Legacy.Text(x.value))))
          )
      },
      div(cls := "advanced-save", SettingsForm.saveBar(draft)),
      children <-- SettingsStore.current
        .map(_.flatMap(_.toOption).toList.flatMap(_.sections))
        .map(_.map(s => block(s, draft, query.signal.map(Query(_)))))
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sectionName(id: ConfigEdit.Section.Id): SectionName = SectionName(
    Names.getOrElse(id.value, Wording.humanize(Wording.Id(id.value)))
  )

  /** One block, folded until opened (or while the filter finds something in it). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def block(s: ConfigEdit.Section, draft: Draft, query: Signal[Query]): HtmlElement =
    val open = Var(false)
    val shown = s.fields.filter(_.credential.isEmpty)
    detailsTag(
      cls := "advanced-block",
      dataAttr("section") := s.id.value,
      htmlAttr("open", com.raquo.laminar.codecs.BooleanAsAttrPresenceCodec) <-- open.signal
        .combineWith(query)
        .map((o, q) => o || q.value.trim.nonEmpty && shown.exists(matches(_, q))),
      summaryTag(
        cls := "advanced-summary",
        onClick.preventDefault --> (_ => open.update(!_)),
        span(cls := "advanced-name", sectionName(s.id)),
        span(cls := "muted", s" · ${s.fields.size}"),
        Option.when(s.doc.value.nonEmpty)(span(cls := "advanced-doc muted", shown" — ${s.doc}"))
      ),
      children <-- open.signal.combineWith(query).map { (o, q) =>
        if !o && q.value.trim.isEmpty then Nil
        else
          shown
            .filter(matches(_, q))
            .map(f =>
              SettingControl(f, draft, Some(SettingsForm.Item.Name(f.relative.value.stripPrefix(shown"${s.id}."))))
            )
      }
    )
