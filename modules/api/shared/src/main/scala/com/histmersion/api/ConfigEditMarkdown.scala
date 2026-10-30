package com.histmersion.api

/** Settings as Markdown, every surface's (`histmersion config get|set|unset`, the `config` tool). Pure. */
object ConfigEditMarkdown:
  import ConfigEdit.*

  /** A setting's value as it may be shown: a credential's state, never its value. */
  def shown(f: Field): String =
    f.credential match
      case Some(c) if !c.isSet => "not set"
      case Some(c)             => "set" + c.shown.fold("")(s => s" (`$s`)")
      case None                => f.effective.fold("(nothing)")(v => s"`$v`")

  def field(f: Field): String =
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
      f.locked.map(w => s"- **not changed from Settings:** $w"))
    lines.flatten.mkString("\n")

  def page(p: Page): String =
    val head = List(s"# Settings", "", s"File: ${p.file.fold("none")(f => s"`$f`" + (if p.exists then "" else " (not there yet)"))}")
    val problems = if p.problems.isEmpty then Nil else "" :: "**Problems:**" :: p.problems.map(x => s"- $x")
    val body = p.sections.flatMap { s =>
      "" :: s"## ${s.id}" :: s.fields.map(f => s"- `${f.relative}` = ${shown(f)}" +
        (if f.source == Source.Default.wire then "" else s" (${Source.parse(f.source).fold(f.source)(_.label)})"))
    }
    (head ++ problems ++ body).mkString("\n")

  def written(w: Written): String =
    def list(title: String, xs: List[String]) = if xs.isEmpty then Nil else s"**$title:**" :: xs.map(x => s"- $x")
    val head =
      if w.changed.isEmpty then "# Settings unchanged — the file already says so"
      else s"# Settings changed — ${Plural.count(w.changed.size, "setting")}"
    (List(head, "", s"File: `${w.file}`") ++ w.backup.map(b => s"Previous version kept as `$b`").toList ++
      list("Changed", w.changed.map(_.stripPrefix("histmersion."))) ++
      list("Already so", w.unchanged.map(_.stripPrefix("histmersion."))) ++
      list("Takes effect after a restart", w.restart.map(r => s"${r.key.stripPrefix("histmersion.")} — ${r.why}")) ++
      list("Warnings", w.warnings) ++
      w.reload.toList.flatMap(r => List("", ReloadMarkdown.report(r).replaceFirst("^# ", "### ")))).mkString("\n")

  def restart(r: Restart): String = if r.accepted then s"# Restarting\n\n${r.message}" else s"# Not restarted\n\n${r.message}"
