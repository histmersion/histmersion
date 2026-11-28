package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Longitude
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.catalogue.Geocode.Placeholder

final case class Candidate(
    id: Place.Id,
    label: Place.Label,
    kind: Candidate.Kind,
    byLabel: Candidate.ByLabel,
    from: Option[Year],
    to: Option[Year],
    dimension: Candidate.Dimension,
    area: Candidate.Area,
    shared: Approximation.Rough.Places,
    lon: Longitude,
    lat: Latitude,
    tier: Gazetteer.Rank = Gazetteer.Rank(0),
    gridCell: Candidate.GridCell = Candidate.GridCell.Exact
):
  def undated: Boolean = (from.isEmpty && to.isEmpty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def span: Candidate.Span = Candidate.Span((for a <- from; b <- to
  yield (b).value - (a).value).getOrElse((Candidate.Span(Long.MaxValue / 4)).value))
  def overlaps(p: Period): Boolean = from.forall(_ <= p.end) && to.forall(_ >= p.start)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def gap(p: Period): Candidate.Gap =
    if overlaps(p) then Candidate.Gap(0L)
    else
      to.filter(_ < p.start)
        .map(t => Candidate.Gap(p.start.value - t.value))
        .orElse(from.filter(_ > p.end).map(f => Candidate.Gap(f.value - p.end.value)))
        .getOrElse(Candidate.Gap(0L))

object Candidate:
  type Gap = Gap.T
  object Gap extends Newtype[Long, Pure]
  type Span = Span.T
  object Span extends Newtype[Long, Pure]

  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  enum ByLabel derives CanEqual:
    case ByLabel, ByPosition
  object ByLabel extends TwoStates[ByLabel]:
    extension (byLabel: ByLabel) def byLabel: Boolean = byLabel == ByLabel
  type Dimension = Dimension.T
  object Dimension extends Newtype[Int, Pure]
  type Area = Area.T
  object Area extends Newtype[Double, Pure]
  enum GridCell derives CanEqual:
    case GridCell, Exact
  object GridCell extends TwoStates[GridCell]:
    extension (gridCell: GridCell) def gridCell: Boolean = gridCell == GridCell

  extension (c: Candidate)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def isPlaceholder(placeholderMin: Placeholder.Min): Boolean =
      c.dimension.value == 0 && c.shared.value >= placeholderMin.value
