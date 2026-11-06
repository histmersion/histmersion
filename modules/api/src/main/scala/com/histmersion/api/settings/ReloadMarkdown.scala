package com.histmersion.api.settings
import com.histmersion.api.given
object ReloadMarkdown:


  def report(r: ReloadReport): com.histmersion.api.Markdown.Text =
    def list(title: String, xs: List[String]): List[String] =
      if xs.isEmpty then Nil else (s"**$title:**" :: xs.map(x => s"- $x")).map(refactorValue => (refactorValue))
    val head =
      if r.errors.nonEmpty then s"# Config reload failed — generation ${r.generation} stays"
      else if ((r.published) == _root_.com.histmersion.api.settings.ReloadReport.Published(true)) then s"# Config reloaded — generation ${r.generation}"
      else s"# Config unchanged — generation ${r.generation}"
    val file = r.file.map(f => s"File: `$f`").toList
    val none =
      if ((r.published) == _root_.com.histmersion.api.settings.ReloadReport.Published(true)) || r.errors.nonEmpty || r.restartOnly.nonEmpty then Nil
      else List("Nothing in the config differs from what is running.")
    (List(head, "") ++ file ++ none ++
      list(("Errors (nothing was changed)"), r.errors) ++
      list("Applied", r.applied) ++
      list("Changed, takes effect after a restart (not applied)", r.restartOnly) ++
      list("Rebuilt", r.rebuilt) ++
      list("Plugins registered again", r.plugins) ++
      list("Still in use from the previous generation (closes when released)", r.draining) ++
      list("Warnings", r.warnings)).mkString("\n")
