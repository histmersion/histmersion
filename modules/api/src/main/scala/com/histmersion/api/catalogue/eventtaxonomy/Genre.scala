package com.histmersion
package api.catalogue.eventtaxonomy

import com.histmersion.api.given
import com.histmersion.api.catalogue.EventSummary
import io.github.iltotore.iron.constraint.any.Pure

final case class Genre(kind: EventSummary.Kind, group: Genre.Group, label: Genre.Label, icon: Genre.Icon)

object Genre:
  type Group = Group.T
  object Group extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Icon = Icon.T
  object Icon extends Newtype[String, Pure]
