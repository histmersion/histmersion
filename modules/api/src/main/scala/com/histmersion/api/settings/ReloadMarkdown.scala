package com.histmersion.api.settings
import com.histmersion.api.given
object ReloadMarkdown:
  type ReportListResult = ReportListResult.T
  object ReportListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportListTitle = ReportListTitle.T
  object ReportListTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportListXs = ReportListXs.T
  object ReportListXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReportResult = ReportResult.T
  object ReportResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def report(r: ReloadReport): com.histmersion.api.settings.ReloadMarkdown.ReportResult =
    def list(title: com.histmersion.api.settings.ReloadMarkdown.ReportListTitle, xs: List[com.histmersion.api.settings.ReloadMarkdown.ReportListXs]): List[com.histmersion.api.settings.ReloadMarkdown.ReportListResult] =
      if xs.isEmpty then Nil else s"**$title:**" :: xs.map(x => s"- $x")
    val head =
      if r.errors.nonEmpty then s"# Config reload failed — generation ${r.generation} stays"
      else if r.published then s"# Config reloaded — generation ${r.generation}"
      else s"# Config unchanged — generation ${r.generation}"
    val file = r.file.map(f => s"File: `$f`").toList
    val none =
      if r.published || r.errors.nonEmpty || r.restartOnly.nonEmpty then Nil
      else List("Nothing in the config differs from what is running.")
    (List(head, "") ++ file ++ none ++
      list("Errors (nothing was changed)", r.errors) ++
      list("Applied", r.applied) ++
      list("Changed, takes effect after a restart (not applied)", r.restartOnly) ++
      list("Rebuilt", r.rebuilt) ++
      list("Plugins registered again", r.plugins) ++
      list("Still in use from the previous generation (closes when released)", r.draining) ++
      list("Warnings", r.warnings)).mkString("\n")
