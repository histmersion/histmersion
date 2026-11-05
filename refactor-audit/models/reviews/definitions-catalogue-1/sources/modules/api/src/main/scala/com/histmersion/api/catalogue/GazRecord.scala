package com.histmersion.api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given
final case class GazRecord(
    ref: GazRecord.Ref,
    own: Option[ExternalId],
    placeId: com.histmersion.api.catalogue.Place.Id,
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
  object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Properties = Properties.T
  object Properties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemClass = ItemClass.T
  object ItemClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
