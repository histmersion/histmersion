package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What `plugin add` is told. `repos`: repositories named for this install (`--repo`), as URLs, looked up before the
  * configured ones — for the plugin itself and for its dependencies.
  */
final case class AddOptions(
    yes: AddOptions.Yes = AddOptions.Yes(false),
    unsigned: AddOptions.Unsigned = AddOptions.Unsigned(false),
    verify: AddOptions.Verify = AddOptions.Verify(false),
    sha256: Option[AddOptions.Sha256] = None,
    repos: List[AddOptions.Repos] = Nil
)

object AddOptions:
  type Yes = Yes.T
  object Yes extends RefinedType[Boolean, Pure]
  type Unsigned = Unsigned.T
  object Unsigned extends RefinedType[Boolean, Pure]
  type Verify = Verify.T
  object Verify extends RefinedType[Boolean, Pure]
  type Sha256 = com.histmersion.api.Sha256
  val Sha256: com.histmersion.api.Sha256.type = com.histmersion.api.Sha256
  type Repos = Repos.T
  object Repos extends RefinedType[String, Pure]
