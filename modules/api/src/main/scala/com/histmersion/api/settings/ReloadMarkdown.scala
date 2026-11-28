package com.histmersion
package api.settings
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
object ReloadMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def report(r: ReloadReport): String =
    def list(title: String, xs: List[String]): List[String] =
      if xs.isEmpty then Nil else (s"**$title:**" :: xs.map(x => s"- $x")).map(refactorValue => (refactorValue))
    val head =
      if r.errors.nonEmpty then s"# Config reload failed — generation ${r.generation} stays"
      else if (r.published) == ReloadReport.Published.Published then s"# Config reloaded — generation ${r.generation}"
      else s"# Config unchanged — generation ${r.generation}"
    val file = r.file.map(f => s"File: `$f`").toList
    val none =
      if ((r.published) == ReloadReport.Published.Published) || r.errors.nonEmpty || r.restartOnly.nonEmpty then Nil
      else List("Nothing in the config differs from what is running.")
    (List(head, "") ++ file ++ none ++
      list(("Errors (nothing was changed)"), r.errors.map(_.value)) ++
      list("Applied", r.applied.map(_.value)) ++
      list("Changed, takes effect after a restart (not applied)", r.restartOnly.map(_.value)) ++
      list("Rebuilt", r.rebuilt.map(_.value)) ++
      list("Plugins registered again", r.plugins.map(_.value)) ++
      list("Still in use from the previous generation (closes when released)", r.draining.map(_.value)) ++
      list("Warnings", r.warnings.map(_.value))).mkString("\n")
