package com.histmersion
package domain.library.sources.referrers

/** What a referrer rewrote on a rename: the columns it changed, and the records whose text links moved (with how many
  * links moved in each).
  */
import com.histmersion.api.library.Rename
import io.github.iltotore.iron.constraint.any.Pure
final case class RenamedReferences(columns: List[Rename.Count] = Nil, texts: List[(RenamedReferences.TextsPart1, RenamedReferences.TextsPart2)] = Nil)

object RenamedReferences:
  type TextsPart1 = TextsPart1.T
  object TextsPart1 extends Newtype[String, Pure]
  type TextsPart2 = TextsPart2.T
  object TextsPart2 extends Newtype[Int, Pure]
