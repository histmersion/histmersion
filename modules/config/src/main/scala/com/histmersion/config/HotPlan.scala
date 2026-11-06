package com.histmersion.config

/** The graph logic of a config reload, pure: which nodes a set of changed keys touches, what has to be rebuilt with
  * them (every node that depends on one, transitively), and in which order. The server's `hot.HotGraph` builds and
  * swaps the values; this decides what and in which order.
  */
object HotPlan:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Does a changed key concern a node that declares `key`? The same key, a key under it, or a block above it (a whole
    * block replaced by a value, or removed).
    */

  type PlanChanged = _root_.com.histmersion.config.ConfigReload.ChangedKey
  val PlanChanged: _root_.com.histmersion.config.ConfigReload.ChangedKey.type = _root_.com.histmersion.config.ConfigReload.ChangedKey

  def keyMatches(changed: com.histmersion.config.ConfigReload.ChangedKey, key: com.histmersion.config.ConfigReload.Key): Boolean =
    changed == key || changed.startsWith(key + ".") || key.startsWith(changed + ".")

  /** The nodes in an order where every node comes after the nodes it depends on (declaration order among independent
    * ones). Refused: a name used twice, a dependency on no node, a cycle (named).
    */
  def order(nodes: List[HotNode]): Either[com.histmersion.config.HotPlan.Problem, List[com.histmersion.config.HotNode.Name]] =
    val names = nodes.map(_.name)
    val dup = names.diff(names.distinct).distinct
    val known = names.toSet
    val missing = nodes.flatMap(n => n.deps.filterNot(known).map(d => s"${n.name} → $d"))
    if dup.nonEmpty then Left(s"hot graph: node name used twice: ${dup.mkString(", ")}")
    else if missing.nonEmpty then Left(s"hot graph: dependency on no node: ${missing.mkString(", ")}")
    else
      val byName = nodes.map(n => n.name -> n).toMap
      // Kahn's algorithm, always taking the first ready node in declaration order: deterministic
      @annotation.tailrec
      def go(left: List[HotNode], done: Vector[com.histmersion.config.HotNode.Name]): Either[com.histmersion.config.HotPlan.Problem, List[com.histmersion.config.HotNode.Name]] =
        if left.isEmpty then Right(done.toList)
        else
          val doneSet = done.toSet
          left.find(_.deps.forall(doneSet)) match
            case Some(n) => go(left.filterNot(_.name == n.name), done :+ n.name)
            case None    => Left(s"hot graph: dependency cycle ${cycle(left, byName).mkString(" → ")}")
      go(nodes, Vector.empty)

  /** One cycle among `left` (every node of which waits on another of them), as a closed path. */
  private def cycle(left: List[HotNode], byName: Map[com.histmersion.config.HotNode.Name, HotNode]): List[com.histmersion.config.HotPlan.Problem] =
    val waiting = left.map(_.name).toSet
    @annotation.tailrec
    def walk(at: com.histmersion.config.HotNode.Name, path: Vector[com.histmersion.config.HotNode.Name]): List[com.histmersion.config.HotPlan.Problem] =
      val i = path.indexOf(at)
      if i >= 0 then (path.drop(i) :+ at).toList
      else
        byName(at).deps.find(waiting) match
          case Some(next) => walk(next, path :+ at)
          case None       => (path :+ at).toList // cannot happen: every waiting node waits on a waiting one
    walk(left.head.name, Vector.empty)

  /** The nodes a set of changed keys concerns directly. */
  def touched(nodes: List[HotNode], changed: Iterable[com.histmersion.config.ConfigReload.ChangedKey]): Set[com.histmersion.config.HotNode.Name] =
    nodes.filter(n => n.keys.exists(k => changed.exists(c => keyMatches(c, k)))).map(_.name).toSet

  /** `roots` and every node that depends on one of them, transitively. */
  def dependents(nodes: List[HotNode], roots: Set[com.histmersion.config.HotNode.Name]): Set[com.histmersion.config.HotNode.Name] =
    @annotation.tailrec
    def grow(acc: Set[com.histmersion.config.HotNode.Name]): Set[com.histmersion.config.HotNode.Name] =
      val more = nodes.filter(n => !acc(n.name) && n.deps.exists(acc)).map(_.name)
      if more.isEmpty then acc else grow(acc ++ more)
    grow(roots)

  /** What a reload rebuilds, in build order: the nodes the changed keys (plus `forced`) concern and everything
    * depending on them. Nothing changed = nothing to rebuild.
    */
  def plan(
      nodes: List[HotNode],
      changed: Iterable[com.histmersion.config.HotPlan.PlanChanged],
      forced: Set[com.histmersion.config.HotNode.Name] = Set.empty
  ): Either[com.histmersion.config.HotPlan.Problem, List[com.histmersion.config.HotNode.Name]] =
    order(nodes).map { ordered =>
      val rebuild = dependents(nodes, touched(nodes, changed) ++ forced.intersect(nodes.map(_.name).toSet))
      ordered.filter(rebuild)
    }
