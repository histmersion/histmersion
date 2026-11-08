package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One thing an audit found wrong with a linked item: a short code (`Missing`, `Title`, …) and a sentence for a person.
  */
final case class CollectionIssue(code: CollectionIssue.Code, label: CollectionIssue.Label)

object CollectionIssue:
  type Code = Code.T
  object Code extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
