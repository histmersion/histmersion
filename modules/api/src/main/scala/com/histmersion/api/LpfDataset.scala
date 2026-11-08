package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Title extends RefinedType[String, Pure]
  type Citation = Citation.T
  object Citation extends RefinedType[String, Pure]
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]
