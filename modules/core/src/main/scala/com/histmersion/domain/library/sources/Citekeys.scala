package com.histmersion.domain.library.sources

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey

/** Citekey shape for a rename. */
object Citekeys:
  type ValidCk = ValidCk.T
  object ValidCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameToResultPart1 = RenameToResultPart1.T
  object RenameToResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameToNewRaw = RenameToNewRaw.T
  object RenameToNewRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: com.histmersion.domain.library.sources.Citekeys.ValidCk): Boolean = Shape.matches(ck)

  /** A source's new citekey, or why it cannot be one: an invalid key, the key it has, or a valid key that is not
    * link-shaped (`[[key]]` would resolve to an article, not the source).
    */
  def renameTo(oldCk: com.histmersion.domain.library.Citekey, newRaw: com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw): Either[com.histmersion.domain.library.sources.Citekeys.RenameToResultPart1, Citekey] =
    Citekey.parse(newRaw).flatMap { ck =>
      if ck.value == oldCk.trim then Left(s"'$ck' is already the citekey of that source")
      else if !WikiLinks.isCitekeyShaped(ck.value) then
        Left(
          s"'$ck' is a valid citekey but not a LINK-shaped one — `[[$ck]]` would resolve to an " +
            "article, not the source. Use lower-case letters, digits and '_' with a four-digit year or an " +
            "underscore (e.g. page1959_history)"
        )
      else Right(ck)
    }
