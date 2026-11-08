package com.histmersion
package pluginresolver

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** What one `resolve` run is asked.
  *
  * @param kit
  *   the relink kit: its `modules` and `excludes` are what the server provides
  * @param lib
  *   the plugins directory's `lib/`, written unless `check`
  * @param histmersionRepo
  *   the install's Maven repository (the plugin API and platform), looked up first
  * @param repositories
  *   the repositories named for this run, looked up before the ones `lib/repositories` keeps from earlier runs (and
  *   kept with them); none named and none kept = Maven Central
  * @param check
  *   resolve and report only: `lib/` is left as it is
  * @param cache
  *   coursier's cache, when not its default
  */
final case class ResolveRequest(
    kit: Path,
    lib: Path,
    histmersionRepo: Option[Path],
    repositories: List[RepositoryUrl],
    check: ResolveRequest.Check,
    jars: List[Path],
    cache: Option[Path] = None
)

object ResolveRequest:
  type Check = Check.T
  object Check extends Newtype[Boolean, Pure]
