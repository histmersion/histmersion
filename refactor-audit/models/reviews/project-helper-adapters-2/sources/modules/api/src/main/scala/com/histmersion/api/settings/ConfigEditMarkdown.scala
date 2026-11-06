package com.histmersion.api.settings

import com.histmersion.api.Plural
import com.histmersion.api.given
object ConfigEditMarkdown:
  type WrittenListTitle = WrittenListTitle.T
  object WrittenListTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrittenListXs = WrittenListXs.T
  object WrittenListXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ShownResult = ShownResult.T
  object ShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldResult = FieldResult.T
  object FieldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrittenResult = WrittenResult.T
  object WrittenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestartResult = RestartResult.T
  object RestartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import ConfigEdit.*

  def shown(f: Field): com.histmersion.api.settings.ConfigEditMarkdown.ShownResult =
    f.credential match
      case Some(c) if !((c.isSet) == _root_.com.histmersion.api.settings.ConfigEdit.Credential.IsSetResult(true)) => _root_.com.histmersion.api.settings.ConfigEditMarkdown.ShownResult("not set")
      case Some(c)             => _root_.com.histmersion.api.settings.ConfigEditMarkdown.ShownResult("set" + c.shown.fold("")(s => s" (`$s`)"))
      case None                => _root_.com.histmersion.api.settings.ConfigEditMarkdown.ShownResult(f.effective.fold("(nothing)")(v => s"`$v`"))

  def field(f: Field): com.histmersion.api.settings.ConfigEditMarkdown.FieldResult =
    val from = Source.parse(f.source).fold(f.source)(_.label)
    val lines = List(
      Some(s"## ${f.relative}"),
      Some(""),
      Option.when(f.doc.nonEmpty)(f.doc + "\n"),
      Some(s"- value: ${shown(f)} — from $from"),
      Option.when(f.credential.isEmpty)(f.value.map(v => s"- in the settings file: `$v`")).flatten,
      Option.when(f.credential.isEmpty)(f.default.map(v => s"- default: `$v`")).flatten,
      f.envVar.map(v => s"- environment variable: `$v`"),
      f.restart.map(w => s"- takes effect after a restart ($w)"),
      f.locked.map(w => s"- **not changed from Settings:** $w")
    )
    lines.flatten.mkString("\n")

  def page(p: Page): com.histmersion.api.settings.ConfigEditMarkdown.PageResult =
    val head = List(
      s"# Settings",
      "",
      s"File: ${p.file.fold("none")(f => s"`$f`" + (if ((p.exists) == _root_.com.histmersion.api.settings.ConfigEdit.Page.Exists(true)) then "" else " (not there yet)"))}"
    )
    val problems = if p.problems.isEmpty then Nil else "" :: "**Problems:**" :: p.problems.map(x => s"- $x")
    val body = p.sections.flatMap { s =>
      "" :: s"## ${s.id}" :: s.fields.map(f =>
        s"- `${f.relative}` = ${shown(f)}" +
          (if f.source == Source.Default.wire then "" else s" (${Source.parse(f.source).fold(f.source)(_.label)})")
      )
    }
    _root_.com.histmersion.api.settings.ConfigEditMarkdown.PageResult((head ++ problems ++ body).mkString("\n"))

  def written(w: Written): com.histmersion.api.settings.ConfigEditMarkdown.WrittenResult =
    def list(title: com.histmersion.api.settings.ConfigEditMarkdown.WrittenListTitle, xs: List[com.histmersion.api.settings.ConfigEditMarkdown.WrittenListXs]) = if xs.isEmpty then Nil else s"**$title:**" :: xs.map(x => s"- $x")
    val head =
      if w.changed.isEmpty then "# Settings unchanged — the file already says so"
      else s"# Settings changed — ${Plural.count(_root_.com.histmersion.api.Plural.CountN((w.changed.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("setting"))}"
    (List(head, "", s"File: `${w.file}`") ++ w.backup.map(b => s"Previous version kept as `$b`").toList ++
      list(_root_.com.histmersion.api.settings.ConfigEditMarkdown.WrittenListTitle("Changed"), w.changed.map(_.stripPrefix("histmersion."))) ++
      list(_root_.com.histmersion.api.settings.ConfigEditMarkdown.WrittenListTitle("Already so"), w.unchanged.map(_.stripPrefix("histmersion."))) ++
      list(_root_.com.histmersion.api.settings.ConfigEditMarkdown.WrittenListTitle("Takes effect after a restart"), w.restart.map(r => s"${r.key.stripPrefix("histmersion.")} — ${r.why}")) ++
      list("Warnings", w.warnings) ++
      w.reload.toList.flatMap(r => List("", ReloadMarkdown.report(r).replaceFirst("^# ", "### ")))).mkString("\n")

  def restart(r: Restart): com.histmersion.api.settings.ConfigEditMarkdown.RestartResult =
    if ((r.accepted) == _root_.com.histmersion.api.settings.ConfigEdit.Restart.Accepted(true)) then _root_.com.histmersion.api.settings.ConfigEditMarkdown.RestartResult(s"# Restarting\n\n${r.message}") else _root_.com.histmersion.api.settings.ConfigEditMarkdown.RestartResult(s"# Not restarted\n\n${r.message}")
