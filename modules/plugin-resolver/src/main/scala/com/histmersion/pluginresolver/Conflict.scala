package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin needs a module the server ships, at a NEWER version than the server's. One class loader holds one version,
  * and the server's would win, so the plugin cannot be linked.
  */
final case class Conflict(plugin: Conflict.Plugin, key: ModuleKey, wanted: ModuleVersion, shipped: ModuleVersion):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def message: Problem =
    Problem.Plugin
      .Dependency(
        Problem.Plugin.Id(s"$plugin"),
        Problem.Raw(s"$key"),
        Problem.Raw(s"$wanted"),
        Problem.Raw(s"$shipped")
      )

object Conflict:

  export Resolver.FileName as Plugin
