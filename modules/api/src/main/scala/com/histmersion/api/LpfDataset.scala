package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Read from a FeatureCollection's own keys or a sidecar `<file>.meta.json`: an LP-TSV file has nowhere else to say it.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@movesTo(FeaturePlace.Kernel)
final case class LpfDataset(
    title: Option[LpfDataset.Title],
    citation: Option[LpfDataset.Citation],
    licence: Option[LpfDataset.Licence],
    span: Span = Span.always
)

object LpfDataset:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
