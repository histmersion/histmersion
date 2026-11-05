package com.histmersion.api

/** Read from a FeatureCollection's own keys or a sidecar `<file>.meta.json`: an LP-TSV file has nowhere else to say it.
  */
final case class LpfDataset(
    title: Option[LpfDataset.Title],
    citation: Option[LpfDataset.Citation],
    licence: Option[LpfDataset.Licence],
    span: Span = Span.always
)

object LpfDataset:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
