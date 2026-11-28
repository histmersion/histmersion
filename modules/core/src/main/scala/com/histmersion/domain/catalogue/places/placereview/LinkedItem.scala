package com.histmersion
package domain.catalogue.places.placereview

import io.github.iltotore.iron.constraint.any.Pure

final case class LinkedItem(ref: LinkedItem.Ref, itemClass: Option[LinkedItem.ItemClass], period: Option[Period])

object LinkedItem:
  type Ref = Ref.T
  object Ref extends Newtype[String, Pure]
  type ItemClass = ItemClass.T
  object ItemClass extends Newtype[String, Pure]
  type ClassLabel = ClassLabel.T
  object ClassLabel extends Newtype[String, Pure]
