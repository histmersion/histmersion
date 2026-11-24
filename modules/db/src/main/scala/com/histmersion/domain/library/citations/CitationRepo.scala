package com.histmersion
package domain.library.citations

import com.histmersion.util.database.Vocab
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.sources.EdgeRow

import com.augustnagro.magnum.*
import com.histmersion.api.library.{
  ContradictionRow,
  ContradictionsResult,
  Edge,
  GraphLink,
  GraphNode,
  NeighborRow,
  NeighborTarget
}
import com.histmersion.api.library.Source
import com.histmersion.api.library.SourceColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.StoredCreator
import com.histmersion.util.database.Jsonb
import com.histmersion.api.automation.Creation

/** The citation graph's reads: a source's typed edges both ways, the graph around it, the disagreements. The edges are
  * written with their source (`sources.SourceRepo`): an edge is stored on the source that makes it.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the citation tables and their row codecs")
private[citations] object CitationRepo:
  type Since = Since.T
  object Since extends Newtype[Long, Pure]

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private given DbCodec[NeighborTarget] = Vocab.codec(
    s => NeighborTarget.values.find(_.toString.equalsIgnoreCase(s.value)),
    e => WireEnumValues.Wire(e.toString.toLowerCase),
    NeighborTarget.Unresolved
  )
  private given DbCodec[Creation] = Jsonb.codec(Creation(Creation.Unreadable))
  private given DbCodec[NeighborRow] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[ContradictionRow] = DbCodec.derived

  /** Every edge touching `citekey`, oriented relative to it, with the far end CLASSIFIED — the one place
    * [[NeighborTarget]] is decided. `edges` holds source→source only (V026), so those rows are `source`; a parked edge
    * (`unresolved_edges`) is an `article` when a compiled article has that slug, else `unresolved`. Incoming edges are
    * always from stored sources. `kinds` narrows by edge kind; empty means all.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def neighbors(citekey: Citekey, kinds: List[domain.library.Edge.Kind.Wire] = Nil)(using
      DbCon
  ): Vector[NeighborRow] =
    val all = kinds.isEmpty
    sql"""SELECT 'out' AS direction, e.kind, e.dst AS citekey, s.title, s.status, 'source' AS target,
                   e.creator::text, ${StoredCreator.at(sql"e.created_at")}
            FROM edges e JOIN sources s ON s.citekey = e.dst
            WHERE e.src = $citekey AND ($all OR e.kind = ANY($kinds::text[]))
            UNION ALL
            SELECT 'out', u.kind, u.dst, a.title, NULL,
                   CASE WHEN a.slug IS NULL THEN 'unresolved' ELSE 'article' END, u.creator::text, NULL
            FROM unresolved_edges u LEFT JOIN articles a ON a.slug = u.dst
            WHERE u.src = $citekey AND ($all OR u.kind = ANY($kinds::text[]))
            UNION ALL
            SELECT 'in', e.kind, e.src, s.title, s.status, 'source', e.creator::text,
                   ${StoredCreator.at(sql"e.created_at")}
            FROM edges e JOIN sources s ON s.citekey = e.src
            WHERE e.dst = $citekey AND ($all OR e.kind = ANY($kinds::text[]))
            ORDER BY 1, 2, 3""".query[NeighborRow].run()

  /** The citation graph around `citekey`, breadth-first to `maxDepth` (capped at 4) in both directions. Every link
    * joins two stored sources (V026's foreign keys), and a link is kept only when BOTH ends are nodes of the result —
    * the depth cut-off never leaves a dangling line.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def graph(citekey: Citekey, maxDepth: Source.Graph.Depth)(using DbCon): Source.Graph =
    val depth = maxDepth.value.max(0).min(4)
    @annotation.tailrec
    def walk(
        d: Int,
        frontier: List[com.histmersion.api.library.Citekey],
        nodes: Vector[GraphNode],
        links: Set[GraphLink]
    ): (Vector[GraphNode], Set[GraphLink]) =
      if frontier.isEmpty then (nodes, links)
      else
        val found = sql"""SELECT citekey, title, year, status FROM sources
                            WHERE citekey = ANY($frontier::text[]) ORDER BY citekey"""
          .query[(String, Option[String], Option[Int], Option[Source.Status])]
          .run()
          .map((ck, t, y, st) =>
            GraphNode(
              com.histmersion.api.library.Citekey(ck),
              t.map(x => Title(x)),
              y.map(x => Year(x.toLong)),
              st,
              Source.Graph.Depth(d)
            )
          )
        val grown = nodes ++ found
        if d >= depth then (grown, links)
        else
          val touching = sql"""SELECT src, dst, kind FROM edges
                                 WHERE src = ANY($frontier::text[]) OR dst = ANY($frontier::text[])"""
            .query[EdgeRow]
            .run()
            .flatMap(e =>
              Edge.Kind
                .fromWire(Edge.Kind.Wire(e.kind.value))
                .map(k =>
                  GraphLink(
                    com.histmersion.api.library.Citekey(e.src.value),
                    com.histmersion.api.library.Citekey(e.dst.value),
                    k
                  )
                )
            )
          val seen = grown.iterator.map(_.id).toSet
          val next = touching.iterator.flatMap(l => List(l.source, l.target)).filterNot(seen).toList.distinct
          walk(d + 1, next, grown, links ++ touching)
    val (nodes, links) = walk(0, List(com.histmersion.api.library.Citekey(citekey.value)), Vector.empty, Set.empty)
    val ids = nodes.iterator.map(_.id).toSet
    Source.Graph(
      Source.Graph.Root(citekey.value),
      Source.Graph.Depth(depth),
      nodes.toList,
      links.toList
        .filter(l => ids(l.source) && ids(l.target))
        .sortBy(l => (l.source.value, l.target.value, Edge.Kind.wireOf(l.kind).value))
    )

  def contradictions(citekey: Option[Citekey], since: Option[Since])(using DbCon): ContradictionsResult =
    citekey match
      case Some(ck) => contradictionsFor(ck)
      case None     => contradictionsAll(since)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def contradictionsFor(ck: Citekey)(using DbCon): ContradictionsResult =
    val rows = sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                            e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                     FROM (
                       SELECT 'out' AS direction, e.kind, e.dst AS other_ck
                       FROM edges e WHERE e.src = $ck AND e.kind IN ('challenges','refutes','updates','superseded_by')
                       UNION ALL
                       SELECT 'in' AS direction, e.kind, e.src AS other_ck
                       FROM edges e WHERE e.dst = $ck AND e.kind IN ('challenges','refutes','updates','superseded_by')
                     ) e
                     JOIN sources s ON s.citekey = $ck
                     LEFT JOIN sources o ON o.citekey = e.other_ck
                     ORDER BY e.direction, e.kind, o.citekey"""
      .query[ContradictionRow]
      .run()
    ContradictionsResult(rows.toList, ContradictionsResult.Total(rows.size))

  private def contradictionsAll(since: Option[Since])(using DbCon): ContradictionsResult =
    val byEdge =
      since match
        case Some(y) =>
          sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                       e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                FROM (
                  SELECT src AS citekey, 'out' AS direction, kind, dst AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                  UNION ALL
                  SELECT dst AS citekey, 'in' AS direction, kind, src AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                ) e
                JOIN sources s ON s.citekey = e.citekey
                LEFT JOIN sources o ON o.citekey = e.other_ck
                WHERE o.year IS NULL OR o.year >= $y
                ORDER BY s.citekey, e.direction, e.kind, o.citekey"""
            .query[ContradictionRow]
            .run()
        case None =>
          sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                       e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                FROM (
                  SELECT src AS citekey, 'out' AS direction, kind, dst AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                  UNION ALL
                  SELECT dst AS citekey, 'in' AS direction, kind, src AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                ) e
                JOIN sources s ON s.citekey = e.citekey
                LEFT JOIN sources o ON o.citekey = e.other_ck
                ORDER BY s.citekey, e.direction, e.kind, o.citekey"""
            .query[ContradictionRow]
            .run()
    val byStatus = sql"""SELECT s.citekey, s.title, s.year, s.status,
                                NULL::text, NULL::text, NULL::text, NULL::text, NULL::int, NULL::text
                         FROM sources s
                         WHERE lower(s.status) IN ('contested','superseded','debunked','retracted')
                           AND NOT EXISTS (
                             SELECT 1 FROM edges e
                             WHERE (e.src = s.citekey OR e.dst = s.citekey)
                               AND e.kind IN ('challenges','refutes','updates','superseded_by'))"""
      .query[ContradictionRow]
      .run()
    val all = (byEdge ++ byStatus).toList
    ContradictionsResult(all, ContradictionsResult.Total(all.size))
