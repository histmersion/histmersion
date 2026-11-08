package com.histmersion
package api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
final case class GazRecord(
    ref: GazRecord.Ref,
    own: Option[ExternalId],
    placeId: Place.Id,
    label: GazRecord.Label,
    kind: GazRecord.Kind,
    geometry: Option[GazGeometry],
    span: Span,
    names: List[GazName],
    links: List[GazLink],
    source: Option[GazRecord.Source],
    properties: Option[GazRecord.Properties],
    descriptions: List[GazDescription] = Nil,
    itemClass: Option[GazRecord.ItemClass] = None
):
  def identifiers: List[ExternalId] = (own.toList ++ links.map(_.id)).distinct

  def placeLinks: List[GazLink] =
    (own.map(GazLink(_, LinkMatch.Exact)).toList ++ links.filterNot(l => own.contains(l.id))).distinctBy(_.id)

object GazRecord:
  type Ref = Ref.T
  object Ref extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Properties = Properties.T
  object Properties extends Newtype[String, Pure]
  type ItemClass = ItemClass.T
  object ItemClass extends Newtype[String, Pure]
