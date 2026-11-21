package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

final case class CollectionChange(field: CollectionChange.Field, from: CollectionChange.From, to: CollectionChange.To)

object CollectionChange:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type From = From.T
  object From extends Newtype[String, Pure]
  type To = To.T
  object To extends Newtype[String, Pure]
