package com.histmersion
package domain.library.sources.referrers

/** What a referrer rewrote on a rename: the columns it changed, and the records whose text links moved (with how many
  * links moved in each).
  */
import com.histmersion.api.library.Rename
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class RenamedReferences(
    columns: List[Rename.Count] = Nil,
    texts: List[(RenamedReferences.Text.Owner, RenamedReferences.Text.Links)] = Nil
)

object RenamedReferences:
  object Text:
    type Owner = Owner.T
    object Owner extends Newtype[String, Pure]
    type Links = Links.T
    object Links extends Newtype[Int, Pure]
