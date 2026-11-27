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
    yes: AddOptions.Yes = AddOptions.Yes.Ask,
    unsigned: AddOptions.Unsigned = AddOptions.Unsigned.RequireSigned,
    verify: AddOptions.Verify = AddOptions.Verify.Skip,
    sha256: Option[AddOptions.Sha256] = None,
    repos: List[AddOptions.Repos] = Nil
)

object AddOptions:
  enum Yes derives CanEqual:
    case Confirmed, Ask
  object Yes:
    extension (yes: Yes) def confirmed: Boolean = yes == Confirmed
  enum Unsigned derives CanEqual:
    case AllowUnsigned, RequireSigned
  object Unsigned:
    extension (unsigned: Unsigned) def allowsUnsigned: Boolean = unsigned == AllowUnsigned
  enum Verify derives CanEqual:
    case Check, Skip
  object Verify:
    extension (verify: Verify) def checks: Boolean = verify == Check
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Sha256 = api.Sha256
  val Sha256: api.Sha256.type = api.Sha256
  type Repos = Repos.T
  object Repos extends Newtype[String, Pure]
