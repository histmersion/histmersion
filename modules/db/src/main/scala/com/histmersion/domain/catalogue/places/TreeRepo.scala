package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.RegionNode

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

/** The gazetteer behind the §1.6 aggregation tree: the places a tree's rows were placed in, their ancestors and the
  * edges between them. The library counts the rows (the search's `AggregateRepo.treeRows`);
  * [[com.histmersion.api.library.Tree]] (pure) buckets and nests.
  */
private[places] object TreeRepo:

  /** A closed [[Period]] from the two bounds SQL returned (`lower`, `upper - 1`); `None` unless both are present — an
    * int8range with an open end is "always", which is what `None` means here.
    */
  type PeriodOfStart = PeriodOfStart.T
  object PeriodOfStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfEnd = PeriodOfEnd.T
  object PeriodOfEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosureResult = ClosureResult.T
  object ClosureResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosureIds = ClosureIds.T
  object ClosureIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosureParamsIds = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  val ClosureParamsIds: _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds.type = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  type NodesQueryIds = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  val NodesQueryIds: _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds.type = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  type RelationsQueryIds = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  val RelationsQueryIds: _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds.type = _root_.com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds
  type GazetteerPlaceIds = GazetteerPlaceIds.T
  object GazetteerPlaceIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def periodOf(start: Option[com.histmersion.domain.catalogue.places.TreeRepo.PeriodOfStart], end: Option[com.histmersion.domain.catalogue.places.TreeRepo.PeriodOfEnd]): Option[Period] =
    (start, end) match
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case _                  => None

  final private[histmersion] case class RegionNodeRow(
      id: RegionNodeRow.Id,
      label: RegionNodeRow.Label,
      kind: RegionNodeRow.Kind,
      periodStart: Option[com.histmersion.Year],
      periodEnd: Option[com.histmersion.Year],
      contested: RegionNodeRow.Contested,
      withdrawn: RegionNodeRow.Withdrawn
  ) derives DbCodec:
    def toNode: RegionNode =
      RegionNode(id, label, kind, periodOf(periodStart, periodEnd), contested, withdrawn)

  object RegionNodeRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Withdrawn = Withdrawn.T
    object Withdrawn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private[histmersion] case class RelationRow(
      childId: RelationRow.ChildId,
      parentId: RelationRow.ParentId,
      kind: RelationRow.Kind,
      periodStart: Option[com.histmersion.Year],
      periodEnd: Option[com.histmersion.Year]
  ) derives DbCodec:
    def toRelation: Place.Relation =
      Place.Relation(childId, parentId, kind, periodOf(periodStart, periodEnd))

  object RelationRow:
    type ChildId = ChildId.T
    object ChildId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParentId = ParentId.T
    object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── the gazetteer the rows hang off ──────────────────────────────────────────────────────────

  /** The upward closure of `ids`: every place reachable from them through `part_of`, whatever the validity of the
    * edges. Deliberately UNFILTERED by `asOf` — `by=time,region` reads the hierarchy at a different year in every time
    * bucket, so the as-of predicate belongs in [[Tree]], applied to the (small) closure, not in this query.
    */
  private def closure(ids: List[com.histmersion.domain.catalogue.places.TreeRepo.ClosureIds]): com.histmersion.domain.catalogue.places.TreeRepo.ClosureResult =
    s"""WITH RECURSIVE up(id) AS (
          SELECT p.id FROM places p WHERE p.id = ANY(?::text[])
          UNION
          SELECT r.parent_id FROM place_relations r JOIN up ON r.child_id = up.id
          WHERE r.kind = '${Place.Relation.PartOf}'
        )"""

  private def closureParams(ids: List[com.histmersion.domain.catalogue.places.TreeRepo.ClosureParamsIds]) = List(Param.A(ids))

  /** The nodes of the closure — id, label, kind, own validity, and whether the shape is contested. */
  /** `withdrawn` is Pleiades's own marker for a superseded record. Some of the export carries it as a property, some
    * only as a `Withdrawn (duplicate): …` label — [[RegionNode.rootEligible]] checks the label too, so both spellings
    * are caught wherever they come from.
    */
  def nodesQuery(ids: List[com.histmersion.domain.catalogue.places.TreeRepo.NodesQueryIds]): Sql =
    Sql(
      closure(ids) +
        s" SELECT p.id, p.label, p.kind, ${RangeSql.cols("p.period")}," +
        " coalesce((p.properties->>'contested')::boolean, false)," +
        " coalesce((p.properties->>'withdrawn')::boolean, false)" +
        " FROM places p JOIN up ON up.id = p.id",
      closureParams(ids)
    )

  /** The `part_of` edges WITHIN the closure, each with its own validity. */
  def relationsQuery(ids: List[com.histmersion.domain.catalogue.places.TreeRepo.RelationsQueryIds]): Sql =
    Sql(
      closure(ids) +
        s" SELECT r.child_id, r.parent_id, r.kind, ${RangeSql.cols("r.period")}" +
        " FROM place_relations r" +
        " WHERE r.child_id IN (SELECT id FROM up) AND r.parent_id IN (SELECT id FROM up)",
      closureParams(ids)
    )

  /** The gazetteer slice a tree's rows need: every place they were materialised into (`placeIds`) plus all its
    * ancestors, and the edges between them. Empty when nothing is located — no query is run then.
    */
  def gazetteer(placeIds: List[com.histmersion.domain.catalogue.places.TreeRepo.GazetteerPlaceIds])(using DbCon): (List[RegionNode], List[Place.Relation]) =
    val ids = placeIds.distinct
    if ids.isEmpty then (Nil, Nil)
    else
      val ns = nodesQuery(ids).frag.query[RegionNodeRow].run().map(_.toNode).toList
      val rs = relationsQuery(ids).frag.query[RelationRow].run().map(_.toRelation).toList
      (ns, rs)
