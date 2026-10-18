package com.histmersion.core

/** One node of the hot component graph (docs/research-plugins.md §3.6): its name, the nodes its value
  * is built from, and the config keys it is built from — each a HOCON path prefix
  * (`histmersion.db` covers `histmersion.db.url` and `histmersion.db.pool.max`). */
final case class HotNode(name: String, deps: List[String], keys: List[String])

/** The graph logic of a config reload, pure: which nodes a set of changed keys touches, what has to be
  * rebuilt with them (every node that depends on one, transitively), and in which order. The server's
  * `hot.HotGraph` builds and swaps the values; this decides what and in which order. */
object HotPlan:

  /** Does a changed key concern a node that declares `key`? The same key, a key under it, or a block
    * above it (a whole block replaced by a value, or removed). */
  def keyMatches(changed: String, key: String): Boolean =
    changed == key || changed.startsWith(key + ".") || key.startsWith(changed + ".")

  /** The nodes in an order where every node comes after the nodes it depends on (declaration order
    * among independent ones). Refused: a name used twice, a dependency on no node, a cycle (named). */
  def order(nodes: List[HotNode]): Either[String, List[String]] =
    val names = nodes.map(_.name)
    val dup   = names.diff(names.distinct).distinct
    val known = names.toSet
    val missing = nodes.flatMap(n => n.deps.filterNot(known).map(d => s"${n.name} → $d"))
    if dup.nonEmpty then Left(s"hot graph: node name used twice: ${dup.mkString(", ")}")
    else if missing.nonEmpty then Left(s"hot graph: dependency on no node: ${missing.mkString(", ")}")
    else
      val byName = nodes.map(n => n.name -> n).toMap
      // Kahn's algorithm, always taking the first ready node in declaration order: deterministic
      @annotation.tailrec
      def go(left: List[HotNode], done: Vector[String]): Either[String, List[String]] =
        if left.isEmpty then Right(done.toList)
        else
          val doneSet = done.toSet
          left.find(_.deps.forall(doneSet)) match
            case Some(n) => go(left.filterNot(_.name == n.name), done :+ n.name)
            case None    => Left(s"hot graph: dependency cycle ${cycle(left, byName).mkString(" → ")}")
      go(nodes, Vector.empty)

  /** One cycle among `left` (every node of which waits on another of them), as a closed path. */
  private def cycle(left: List[HotNode], byName: Map[String, HotNode]): List[String] =
    val waiting = left.map(_.name).toSet
    @annotation.tailrec
    def walk(at: String, path: Vector[String]): List[String] =
      val i = path.indexOf(at)
      if i >= 0 then (path.drop(i) :+ at).toList
      else byName(at).deps.find(waiting) match
        case Some(next) => walk(next, path :+ at)
        case None       => (path :+ at).toList // cannot happen: every waiting node waits on a waiting one
    walk(left.head.name, Vector.empty)

  /** The nodes a set of changed keys concerns directly. */
  def touched(nodes: List[HotNode], changed: Iterable[String]): Set[String] =
    nodes.filter(n => n.keys.exists(k => changed.exists(c => keyMatches(c, k)))).map(_.name).toSet

  /** `roots` and every node that depends on one of them, transitively. */
  def dependents(nodes: List[HotNode], roots: Set[String]): Set[String] =
    @annotation.tailrec
    def grow(acc: Set[String]): Set[String] =
      val more = nodes.filter(n => !acc(n.name) && n.deps.exists(acc)).map(_.name)
      if more.isEmpty then acc else grow(acc ++ more)
    grow(roots)

  /** What a reload rebuilds, in build order: the nodes the changed keys (plus `forced`) concern and
    * everything depending on them. Nothing changed = nothing to rebuild. */
  def plan(nodes: List[HotNode], changed: Iterable[String], forced: Set[String] = Set.empty): Either[String, List[String]] =
    order(nodes).map { ordered =>
      val rebuild = dependents(nodes, touched(nodes, changed) ++ forced.intersect(nodes.map(_.name).toSet))
      ordered.filter(rebuild)
    }
