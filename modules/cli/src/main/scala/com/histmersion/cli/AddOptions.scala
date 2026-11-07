package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import scala.jdk.CollectionConverters.*

/** What `plugin add` is told. `repos`: repositories named for this install (`--repo`), as URLs, looked up before the
  * configured ones — for the plugin itself and for its dependencies.
  */
final case class AddOptions(
    yes: AddOptions.Yes = com.histmersion.cli.AddOptions.Yes(false),
    unsigned: AddOptions.Unsigned = com.histmersion.cli.AddOptions.Unsigned(false),
    verify: AddOptions.Verify = com.histmersion.cli.AddOptions.Verify(false),
    sha256: Option[AddOptions.Sha256] = None,
    repos: List[AddOptions.Repos] = Nil
)

object AddOptions:
  type Yes = Yes.T
  object Yes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unsigned = Unsigned.T
  object Unsigned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Verify = Verify.T
  object Verify extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256 = com.histmersion.api.Sha256
  val Sha256: _root_.com.histmersion.api.Sha256.type = _root_.com.histmersion.api.Sha256
  type Repos = Repos.T
  object Repos extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
