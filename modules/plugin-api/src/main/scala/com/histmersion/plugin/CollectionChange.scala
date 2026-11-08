package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One field a collection changes (or would change) in an item: its name in the collection, what it holds now and what
  * it is written to.
  */
final case class CollectionChange(field: CollectionChange.Field, from: CollectionChange.From, to: CollectionChange.To)

object CollectionChange:
  type Field = Field.T
  object Field extends RefinedType[String, Pure]
  type From = From.T
  object From extends RefinedType[String, Pure]
  type To = To.T
  object To extends RefinedType[String, Pure]
