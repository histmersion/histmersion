package com.histmersion
package cli

import com.histmersion.config.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
  object Yes extends Newtype[Boolean, Pure]
  type Unsigned = Unsigned.T
  object Unsigned extends Newtype[Boolean, Pure]
  type Verify = Verify.T
  object Verify extends Newtype[Boolean, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Sha256 = api.Sha256
  val Sha256: api.Sha256.type = api.Sha256
  type Repos = Repos.T
  object Repos extends Newtype[String, Pure]
