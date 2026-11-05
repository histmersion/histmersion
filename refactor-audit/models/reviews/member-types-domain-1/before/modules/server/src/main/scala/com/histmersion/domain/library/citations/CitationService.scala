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
  def neighbors(citekey: String, kinds: List[String]): List[NeighborRow]

  /** Breadth-first to `depth` (0–4) both ways; a link is kept only when both ends are nodes. */
  def graph(citekey: String, depth: Int): com.histmersion.api.library.Source.Graph

  def contradictions(citekey: Option[String], since: Option[Long]): ContradictionsResult

object CitationService:

  /** The edge kinds that disagree. */
  val Disagreeing: Set[String] = Set("challenges", "refutes", "updates", "superseded_by")

  /** The statuses that disagree on their own. */
  val DisagreeingStatuses: Set[String] = Set("contested", "superseded", "debunked", "retracted")

  def apply(db: Transactor): CitationService = Implementation(db)

  final private class Implementation(db: Transactor) extends CitationService:
    def neighbors(citekey: String, kinds: List[String]): List[NeighborRow] =
      db.read(CitationRepo.neighbors(citekey, kinds)).toList
    def graph(citekey: String, depth: Int): com.histmersion.api.library.Source.Graph =
      db.read(CitationRepo.graph(citekey, depth))
    def contradictions(citekey: Option[String], since: Option[Long]): ContradictionsResult =
      db.read(CitationRepo.contradictions(citekey, since))

  /** The graph over the sources the library holds in memory, as a database holding only the library's rows answers: no
    * article is stored, so a parked edge's target is unresolved.
    */
  final class InMemory(state: AtomicReference[LibraryState]) extends CitationService:

    def neighbors(citekey: String, kinds: List[String]): List[NeighborRow] =
      val st = state.get
      def wanted(kind: String) = kinds.isEmpty || kinds.contains(kind)
      def source(ck: com.histmersion.api.library.Citekey) = st.sources.get(ck.value).map(_.source)
      val out = st.edges.toList.filter(e => e.src == com.histmersion.api.library.Citekey(citekey) && wanted(e.kind)).flatMap { e =>
        source(e.dst).map(s => NeighborRow("out", e.kind, e.dst.value, Some(s.title.value), Some(s.status), NeighborTarget.Source))
      }
      val parked = st.parked.toList
        .filter(e => e.src == com.histmersion.api.library.Citekey(citekey) && wanted(e.kind))
        .map(e => NeighborRow("out", e.kind, e.dst.value, None, None, NeighborTarget.Unresolved))
      val in = st.edges.toList.filter(e => e.dst == com.histmersion.api.library.Citekey(citekey) && wanted(e.kind)).flatMap { e =>
        source(e.src).map(s => NeighborRow("in", e.kind, e.src.value, Some(s.title.value), Some(s.status), NeighborTarget.Source))
      }
      (out ++ parked ++ in).sortBy(r => (r.direction, r.kind, r.citekey))

    def graph(citekey: String, depth: Int): com.histmersion.api.library.Source.Graph =
      val st = state.get
      val maxDepth = depth.max(0).min(4)
      @tailrec def walk(
          d: Int,
          frontier: List[String],
          nodes: Vector[GraphNode],
          links: Set[GraphLink]
      ): (Vector[GraphNode], Set[GraphLink]) =
        if frontier.isEmpty then (nodes, links)
        else
          val found = frontier.distinct.sorted.flatMap(ck =>
            st.sources.get(ck).map(x => GraphNode(ck, Some(x.source.title.value), x.source.year.map(_.value.toInt), Some(x.source.status), d))
          )
          val grown = nodes ++ found
          if d >= maxDepth then (grown, links)
          else
            val touching = st.edges.toList
              .filter(e => frontier.contains(e.src.value) || frontier.contains(e.dst.value))
              .map(e => GraphLink(e.src.value, e.dst.value, e.kind))
            val seen = grown.iterator.map(_.id).toSet
            val next = touching.flatMap(l => List(l.source, l.target)).filterNot(seen).distinct
            walk(d + 1, next, grown, links ++ touching)
      val (nodes, links) = walk(0, List(citekey), Vector.empty, Set.empty)
      val ids = nodes.iterator.map(_.id).toSet
      com.histmersion.api.library.Source.Graph(
        citekey,
        maxDepth,
        nodes.toList,
        links.toList.filter(l => ids(l.source) && ids(l.target)).sortBy(l => (l.source, l.target, l.kind))
      )

    def contradictions(citekey: Option[String], since: Option[Long]): ContradictionsResult =
      val st = state.get
      def row(ck: String, direction: String, kind: String, other: String): Option[ContradictionRow] =
        for s <- st.sources.get(ck).map(_.source)
        yield
          val o = st.sources.get(other).map(_.source)
          ContradictionRow(
            ck, s.title.value, s.year.map(_.value.toInt), s.status, Some(direction), Some(kind),
            o.map(_.citekey.value), o.map(_.title.value), o.flatMap(_.year).map(_.value.toInt),
            Some(o.fold(Source.Status.Unassessed)(_.status))
          )
      val disagreeing = st.edges.toList.filter(e => Disagreeing(e.kind))
      def sides = disagreeing.flatMap(e => List((e.src, "out", e.kind, e.dst), (e.dst, "in", e.kind, e.src)))
      citekey match
        case Some(ck) =>
          val rows = sides
            .filter(_._1 == com.histmersion.api.library.Citekey(ck))
            .flatMap((ck, d, k, o) => row(ck.value, d, k, o.value))
            .sortBy(r => (r.direction, r.kind, r.otherCitekey))
          ContradictionsResult(rows, rows.size)
        case None =>
          val byEdge = sides
            .flatMap((ck, d, k, o) => row(ck.value, d, k, o.value))
            .filter(r => since.forall(y => r.otherYear.forall(_ >= y)))
            .sortBy(r => (r.citekey, r.direction, r.kind, r.otherCitekey))
          val touched = disagreeing.flatMap(e => List(e.src, e.dst)).toSet
          val byStatus = st.sources.values.toList.map(_.source)
            .filter(s => DisagreeingStatuses(s.status.wire) && !touched(s.citekey))
            .sortBy(_.citekey.value.toString)
            .map(s => ContradictionRow(s.citekey.value, s.title.value, s.year.map(_.value.toInt), s.status, None, None, None, None, None, None))
          val all = byEdge ++ byStatus
          ContradictionsResult(all, all.size)
