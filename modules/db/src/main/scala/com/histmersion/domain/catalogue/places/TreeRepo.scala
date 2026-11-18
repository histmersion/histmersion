package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.RegionNode

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

/** The gazetteer behind the §1.6 aggregation tree: the places a tree's rows were placed in, their ancestors and the
  * edges between them. The library counts the rows (the search's `AggregateRepo.treeRows`);
  * [[com.histmersion.api.library.Tree]] (pure) buckets and nests.
  */
private[places] object TreeRepo:

  /** A closed [[Period]] from the two bounds SQL returned (`lower`, `upper - 1`); `None` unless both are present — an
    * int8range with an open end is "always", which is what `None` means here.
    */
  private def periodOf(start: Option[DeepTime.SignedYear], end: Option[DeepTime.SignedYear]): Option[Period] =
    (start, end) match
      case (Some(a), Some(b)) =>
        val (lo, hi) = (Year(a.value), Year(b.value))
        Some(if a <= b then Period(lo, hi) else Period(hi, lo))
      case _ => None

  final private[histmersion] case class RegionNodeRow(
      id: RegionNodeRow.Id,
      label: RegionNodeRow.Label,
      kind: RegionNodeRow.Kind,
      periodStart: Option[DeepTime.SignedYear],
      periodEnd: Option[DeepTime.SignedYear],
      contested: RegionNodeRow.Contested,
      withdrawn: RegionNodeRow.Withdrawn
  ) derives DbCodec:
    def toNode: RegionNode =
      RegionNode(
        RegionNode.Id(id.value),
        RegionNode.Label(label.value),
        RegionNode.Kind(kind.value),
        periodOf(periodStart, periodEnd),
        RegionNode.Contested(contested.value),
        RegionNode.Withdrawn(withdrawn.value)
      )

  object RegionNodeRow:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Contested = Contested.T
    object Contested extends Newtype[Boolean, Pure]
    type Withdrawn = Withdrawn.T
    object Withdrawn extends Newtype[Boolean, Pure]

  final private[histmersion] case class RelationRow(
      childId: RelationRow.ChildId,
      parentId: RelationRow.ParentId,
      kind: RelationRow.Kind,
      periodStart: Option[DeepTime.SignedYear],
      periodEnd: Option[DeepTime.SignedYear]
  ) derives DbCodec:
    def toRelation: Place.Relation =
      Place.Relation(
        Place.Relation.ChildId(childId.value),
        Place.Relation.ParentId(parentId.value),
        Place.Relation.Kind(kind.value),
        periodOf(periodStart, periodEnd)
      )

  object RelationRow:
    type ChildId = ChildId.T
    object ChildId extends Newtype[String, Pure]
    type ParentId = ParentId.T
    object ParentId extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

  // ── the gazetteer the rows hang off ──────────────────────────────────────────────────────────

  /** The upward closure of `ids`: every place reachable from them through `part_of`, whatever the validity of the
    * edges. Deliberately UNFILTERED by `asOf` — `by=time,region` reads the hierarchy at a different year in every time
    * bucket, so the as-of predicate belongs in [[Tree]], applied to the (small) closure, not in this query.
    */
  private def closure(ids: List[com.histmersion.domain.catalogue.Place.Id]): Frag =
    sql"""WITH RECURSIVE up(id) AS (
          SELECT p.id FROM places p WHERE p.id = ANY($ids::text[])
          UNION
          SELECT r.parent_id FROM place_relations r JOIN up ON r.child_id = up.id
          WHERE r.kind = '${SqlLiteral(Place.Relation.PartOf)}'
        )"""

  /** The nodes of the closure — id, label, kind, own validity, and whether the shape is contested. */
  /** `withdrawn` is Pleiades's own marker for a superseded record. Some of the export carries it as a property, some
    * only as a `Withdrawn (duplicate): …` label — [[RegionNode.rootEligible]] checks the label too, so both spellings
    * are caught wherever they come from.
    */
  def nodesQuery(ids: List[com.histmersion.domain.catalogue.Place.Id]): Frag =
    sql"${closure(ids)} SELECT p.id, p.label, p.kind, ${SqlLiteral(RangeSql.cols("p.period"))}, coalesce((p.properties->>'contested')::boolean, false), coalesce((p.properties->>'withdrawn')::boolean, false) FROM places p JOIN up ON up.id = p.id"

  /** The `part_of` edges WITHIN the closure, each with its own validity. */
  def relationsQuery(ids: List[com.histmersion.domain.catalogue.Place.Id]): Frag =
    sql"${closure(ids)} SELECT r.child_id, r.parent_id, r.kind, ${SqlLiteral(RangeSql.cols("r.period"))} FROM place_relations r WHERE r.child_id IN (SELECT id FROM up) AND r.parent_id IN (SELECT id FROM up)"

  /** The gazetteer slice a tree's rows need: every place they were materialised into (`placeIds`) plus all its
    * ancestors, and the edges between them. Empty when nothing is located — no query is run then.
    */
  def gazetteer(placeIds: List[com.histmersion.domain.catalogue.Place.Id])(using
      DbCon
  ): (List[RegionNode], List[Place.Relation]) =
    val ids = placeIds.distinct
    if ids.isEmpty then (Nil, Nil)
    else
      val ns = nodesQuery(ids).query[RegionNodeRow].run().map(_.toNode).toList
      val rs = relationsQuery(ids).query[RelationRow].run().map(_.toRelation).toList
      (ns, rs)
