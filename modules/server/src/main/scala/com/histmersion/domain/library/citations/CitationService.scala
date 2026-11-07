package com.histmersion.domain.library.citations

import com.histmersion.domain.library.LibraryState
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.library.{
  ContradictionRow,
  ContradictionsResult,
  GraphLink,
  GraphNode,
  NeighborRow,
  NeighborTarget
}
import com.histmersion.api.library.Source

/** The citation graph: a source's typed edges both ways (each far end classified), the graph around it, the
  * disagreements — edges that challenge, refute, update or supersede, and the statuses that say so.
  */
trait CitationService:
  def neighbors(citekey: com.histmersion.domain.library.Citekey, kinds: List[com.histmersion.domain.library.Edge.Kind.Wire]): List[NeighborRow]

  /** Breadth-first to `depth` (0–4) both ways; a link is kept only when both ends are nodes. */
  def graph(citekey: com.histmersion.domain.library.Citekey, depth: CitationService.Depth): com.histmersion.api.library.Source.Graph

  def contradictions(citekey: Option[com.histmersion.domain.library.Citekey], since: Option[com.histmersion.domain.library.citations.CitationService.Since]): ContradictionsResult

object CitationService:
  type Depth = Source.Graph.Depth
  val Depth: Source.Graph.DepthCount.type = Source.Graph.DepthCount
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The edge kinds that disagree. */
  val Disagreeing: Set[com.histmersion.api.library.Edge.Kind] =
    Set(
      com.histmersion.api.library.Edge.Kind.Challenges,
      com.histmersion.api.library.Edge.Kind.Refutes,
      com.histmersion.api.library.Edge.Kind.Updates,
      com.histmersion.api.library.Edge.Kind.SupersededBy
    )

  /** The statuses that disagree on their own. */
  val DisagreeingStatuses: Set[Source.Status] =
    Set(Source.Status.Contested, Source.Status.Superseded, Source.Status.Debunked, Source.Status.Retracted)

  def apply(db: Transactor): CitationService = Implementation(db)

  final private class Implementation(db: Transactor) extends CitationService:
    def neighbors(citekey: com.histmersion.domain.library.Citekey, kinds: List[com.histmersion.domain.library.Edge.Kind.Wire]): List[NeighborRow] =
      db.read(CitationRepo.neighbors(citekey, kinds)).toList
    def graph(citekey: com.histmersion.domain.library.Citekey, depth: CitationService.Depth): com.histmersion.api.library.Source.Graph =
      db.read(CitationRepo.graph(citekey, depth))
    def contradictions(citekey: Option[com.histmersion.domain.library.Citekey], since: Option[com.histmersion.domain.library.citations.CitationService.Since]): ContradictionsResult =
      db.read(CitationRepo.contradictions(citekey, since.map(y => CitationRepo.Since(y.value))))

  /** The graph over the sources the library holds in memory, as a database holding only the library's rows answers: no
    * article is stored, so a parked edge's target is unresolved.
    */
  final class InMemory(state: AtomicReference[LibraryState]) extends CitationService:
    private def wire(kind: com.histmersion.api.library.Edge.Kind) = com.histmersion.api.library.Edge.Kind.wireOf(kind).value
    private def stored(st: LibraryState, ck: com.histmersion.api.library.Citekey) = st.sources.get(LibraryState.SourcesKey(ck.value)).map(_.source)

    def neighbors(citekey: com.histmersion.domain.library.Citekey, kinds: List[com.histmersion.domain.library.Edge.Kind.Wire]): List[NeighborRow] =
      val st = state.get
      val self = com.histmersion.api.library.Citekey(citekey.value)
      def wanted(kind: com.histmersion.api.library.Edge.Kind) = kinds.isEmpty || kinds.exists(_.value == wire(kind))
      def source(ck: com.histmersion.api.library.Citekey) = stored(st, ck)
      val out = st.edges.toList.filter(e => e.src == self && wanted(e.kind)).flatMap { e =>
        source(e.dst).map(s => NeighborRow(NeighborRow.Direction("out"), e.kind, e.dst, Some(s.title), Some(s.status), NeighborTarget.Source))
      }
      val parked = st.parked.toList
        .filter(e => e.src == self && wanted(e.kind))
        .map(e => NeighborRow(NeighborRow.Direction("out"), e.kind, e.dst, None, None, NeighborTarget.Unresolved))
      val in = st.edges.toList.filter(e => e.dst == self && wanted(e.kind)).flatMap { e =>
        source(e.src).map(s => NeighborRow(NeighborRow.Direction("in"), e.kind, e.src, Some(s.title), Some(s.status), NeighborTarget.Source))
      }
      (out ++ parked ++ in).sortBy(r => (r.direction.value, wire(r.kind), r.citekey.value))

    def graph(citekey: com.histmersion.domain.library.Citekey, depth: CitationService.Depth): com.histmersion.api.library.Source.Graph =
      val st = state.get
      val maxDepth = depth.value.max(0).min(4)
      @tailrec def walk(
          d: Int,
          frontier: List[com.histmersion.api.library.Citekey],
          nodes: Vector[GraphNode],
          links: Set[GraphLink]
      ): (Vector[GraphNode], Set[GraphLink]) =
        if frontier.isEmpty then (nodes, links)
        else
          val found = frontier.distinct.sortBy(_.value).flatMap(ck =>
            stored(st, ck).map(x => GraphNode(ck, Some(x.title), x.year, Some(x.status), Source.Graph.DepthCount(d)))
          )
          val grown = nodes ++ found
          if d >= maxDepth then (grown, links)
          else
            val touching = st.edges.toList
              .filter(e => frontier.contains(e.src) || frontier.contains(e.dst))
              .map(e => GraphLink(e.src, e.dst, e.kind))
            val seen = grown.iterator.map(_.id).toSet
            val next = touching.flatMap(l => List(l.source, l.target)).filterNot(seen).distinct
            walk(d + 1, next, grown, links ++ touching)
      val root = com.histmersion.api.library.Citekey(citekey.value)
      val (nodes, links) = walk(0, List(root), Vector.empty, Set.empty)
      val ids = nodes.iterator.map(_.id).toSet
      com.histmersion.api.library.Source.Graph(
        root,
        Source.Graph.DepthCount(maxDepth),
        nodes.toList,
        links.toList.filter(l => ids(l.source) && ids(l.target)).sortBy(l => (l.source.value, l.target.value, wire(l.kind)))
      )

    def contradictions(citekey: Option[com.histmersion.domain.library.Citekey], since: Option[com.histmersion.domain.library.citations.CitationService.Since]): ContradictionsResult =
      val st = state.get
      def row(ck: com.histmersion.api.library.Citekey, direction: ContradictionRow.Direction.Wire, kind: com.histmersion.api.library.Edge.Kind, other: com.histmersion.api.library.Citekey): Option[ContradictionRow] =
        for s <- stored(st, ck)
        yield
          val o = stored(st, other)
          ContradictionRow(
            ck, s.title, s.year, s.status, Some(direction), Some(kind),
            o.map(_.citekey), o.map(_.title), o.flatMap(_.year),
            Some(o.fold(Source.Status.Unassessed)(_.status))
          )
      val disagreeing = st.edges.toList.filter(e => Disagreeing(e.kind))
      val out = ContradictionRow.Direction.Wire("out")
      val in = ContradictionRow.Direction.Wire("in")
      def sides = disagreeing.flatMap(e => List((e.src, out, e.kind, e.dst), (e.dst, in, e.kind, e.src)))
      citekey match
        case Some(ck) =>
          val self = com.histmersion.api.library.Citekey(ck.value)
          val rows = sides
            .filter(_._1 == self)
            .flatMap((ck, d, k, o) => row(ck, d, k, o))
            .sortBy(r => (r.direction.map(_.value), r.kind.map(wire), r.otherCitekey.map(_.value)))
          ContradictionsResult(rows, ContradictionsResult.Total(rows.size))
        case None =>
          val byEdge = sides
            .flatMap((ck, d, k, o) => row(ck, d, k, o))
            .filter(r => since.forall(y => r.otherYear.forall(_.value >= y.value)))
            .sortBy(r => (r.citekey.value, r.direction.map(_.value), r.kind.map(wire), r.otherCitekey.map(_.value)))
          val touched = disagreeing.flatMap(e => List(e.src, e.dst)).toSet
          val byStatus = st.sources.values.toList.map(_.source)
            .filter(s => DisagreeingStatuses(s.status) && !touched(s.citekey))
            .sortBy(_.citekey.value)
            .map(s => ContradictionRow(s.citekey, s.title, s.year, s.status, None, None, None, None, None, None))
          val all = byEdge ++ byStatus
          ContradictionsResult(all, ContradictionsResult.Total(all.size))
