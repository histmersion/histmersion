package com.histmersion.api.settings

import com.histmersion.api.Plural
import com.histmersion.api.given
object ConfigEditMarkdown:


  import ConfigEdit.*

  def shown(f: Field): String =
    f.credential match
      case Some(c) if !((c.isSet) == (true)) => ("not set")
      case Some(c)             => ("set" + c.shown.fold("")(s => s" (`$s`)"))
      case None                => (f.effective.fold("(nothing)")(v => s"`$v`"))

  def field(f: Field): String =
    val from = Source.parse(f.source).fold(f.source)(_.label)
    val lines = List(
      Some(s"## ${f.relative}"),
      Some(""),
      Option.when(f.doc.value.nonEmpty)(f.doc.value + "\n"),
      Some(s"- value: ${shown(f)} — from $from"),
      Option.when(f.credential.isEmpty)(f.value.map(v => s"- in the settings file: `$v`")).flatten,
      Option.when(f.credential.isEmpty)(f.default.map(v => s"- default: `$v`")).flatten,
      f.envVar.map(v => s"- environment variable: `$v`"),
      f.restart.map(w => s"- takes effect after a restart ($w)"),
      f.locked.map(w => s"- **not changed from Settings:** $w")
    )
    lines.flatten.mkString("\n")

  def page(p: Page): String =
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
    ((head ++ problems ++ body).mkString("\n"))

  def written(w: Written): String =
    def list(title: String, xs: List[String]) = if xs.isEmpty then Nil else s"**$title:**" :: xs.map(x => s"- $x")
    val head =
      if w.changed.isEmpty then "# Settings unchanged — the file already says so"
      else s"# Settings changed — ${Plural.count(_root_.com.histmersion.api.Plural.CountN((w.changed.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("setting"))}"
    (List(head, "", s"File: `${w.file}`") ++ w.backup.map(b => s"Previous version kept as `$b`").toList ++
      list(("Changed"), w.changed.map(_.value.stripPrefix("histmersion."))) ++
      list(("Already so"), w.unchanged.map(_.value.stripPrefix("histmersion."))) ++
      list(("Takes effect after a restart"), w.restart.map(r => s"${r.key.value.stripPrefix("histmersion.")} — ${r.why}")) ++
      list(("Warnings"), w.warnings.map(_.value)) ++
      w.reload.toList.flatMap(r => List("", ReloadMarkdown.report(r).replaceFirst("^# ", "### ")))).mkString("\n")

  def restart(r: Restart): String =
    if ((r.accepted) == _root_.com.histmersion.api.settings.ConfigEdit.Restart.Accepted(true)) then (s"# Restarting\n\n${r.message}") else (s"# Not restarted\n\n${r.message}")
