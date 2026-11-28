package com.histmersion
package api.catalogue.eventtaxonomy

import com.histmersion.api.given
import com.histmersion.api.catalogue.EventSummary
import io.github.iltotore.iron.constraint.any.Pure

final case class Kind(kind: EventSummary.Kind, group: Kind.Group, label: Kind.Label, icon: Kind.Icon)

object Kind:
  type Group = Group.T
  object Group extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Icon = Icon.T
  object Icon extends Newtype[String, Pure]
