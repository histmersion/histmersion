package com.histmersion
package domain.library.sources

import com.histmersion.domain.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** Citekey shape for a rename. */
object Citekeys:
  type ValidCk = ValidCk.T
  object ValidCk extends Newtype[String, Pure]
  type RenameToNewRaw = RenameToNewRaw.T
  object RenameToNewRaw extends Newtype[String, Pure]

  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: ValidCk): Boolean = Shape.matches(ck.value)

  /** A source's new citekey, or why it cannot be one: an invalid key, the key it has, or a valid key that is not
    * link-shaped (`[[key]]` would resolve to an article, not the source).
    */
  def renameTo(oldCk: Citekey, newRaw: RenameToNewRaw): Either[Problem, Citekey] =
    Citekey.parse(newRaw.value).left.map(Problem.fromValues).flatMap { ck =>
      if ck.value == oldCk.value.trim then Left(Problem(s"'$ck' is already the citekey of that source"))
      else if !WikiLinks.isCitekeyShaped(ck.value) then
        Left(
          Problem(
            s"'$ck' is a valid citekey but not a LINK-shaped one — `[[$ck]]` would resolve to an " +
              "article, not the source. Use lower-case letters, digits and '_' with a four-digit year or an " +
              "underscore (e.g. page1959_history)"
          )
        )
      else Right(ck)
    }
