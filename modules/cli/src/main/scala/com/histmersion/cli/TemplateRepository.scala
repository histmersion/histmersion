package com.histmersion.cli

import io.github.iltotore.iron.*

/** Where a new plugin project resolves the plugin API, the platform and the testkit from. */
enum TemplateRepository:
  /** The local Maven repository (`~/.m2/repository`), which `histmersion plugin repo --m2` fills from the install's
    * `maven/`. scala-cli names it `m2Local`, so the project names no path of this machine.
    */
  case MavenLocal

  /** A repository named with `--repo`. */
  case Remote(repository: PluginRepository)

  /** As `//> using repository` takes it. */
  def directive: String = this match
    case MavenLocal   => "m2Local"
    case Remote(repo) => repo.value

object TemplateRepository:
