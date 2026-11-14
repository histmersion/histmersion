package com.histmersion
package config
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.problem.{Problem, Problems}

/** The graph logic of a config reload, pure: which nodes a set of changed keys touches, what has to be rebuilt with
  * them (every node that depends on one, transitively), and in which order. The server's `hot.HotGraph` builds and
  * swaps the values; this decides what and in which order.
  */
object HotPlan:

  /** Does a changed key concern a node that declares `key`? The same key, a key under it, or a block above it (a whole
    * block replaced by a value, or removed).
    */
  def keyMatches(changed: ConfigReload.Key, key: ConfigReload.Key): Boolean =
    changed == key || changed.value.startsWith(key.value + ".") || key.value.startsWith(changed.value + ".")

  private def depNames(n: HotNode): List[HotNode.Name] = n.deps.map(d => HotNode.Name(d.value))

  private def keyNames(n: HotNode): List[ConfigReload.Key] = n.keys.map(k => ConfigReload.Key(k.value))

  /** The nodes in an order where every node comes after the nodes it depends on (declaration order among independent
    * ones). Refused: a name used twice, a dependency on no node, a cycle (named).
    */
  def order(nodes: List[HotNode]): Either[Problems, List[HotNode.Name]] =
    val names = nodes.map(_.name)
    val known = names.toSet
    val name = Problem.TypeName.of(HotNode.Name)
    val dup = names
      .diff(names.distinct)
      .distinct
      .map(d => Problem.Conflict.AlreadyExists(name, Problem.Raw(s"$d")))
    val missing = nodes.flatMap(n =>
      depNames(n)
        .filterNot(known)
        .map(d => Problem.Invalid.Unknown(name, Problem.Raw(s"$d"), Vector.empty, Vector(Problem.Field(s"${n.name}"))))
    )
    NonEmptyVector.from((dup ++ missing).toVector) match
      case Some(faults) => Left(faults)
      case None         =>
        val byName = nodes.map(n => n.name -> n).toMap
        // Kahn's algorithm, always taking the first ready node in declaration order: deterministic
        @annotation.tailrec
        def go(left: List[HotNode], done: Vector[HotNode.Name]): Either[Problems, List[HotNode.Name]] =
          if left.isEmpty then Right(done.toList)
          else
            val doneSet = done.toSet
            left.find(depNames(_).forall(doneSet)) match
              case Some(n) => go(left.filterNot(_.name == n.name), done :+ n.name)
              case None    =>
                val path = cycle(left, byName).map(n => Problem.Config.Scope(s"$n"))
                Left(Problem.Config.Cycle(NonEmptyVector.of(path.head, path.tail*)))
        go(nodes, Vector.empty)

  /** One cycle among `left` (every node of which waits on another of them), as a closed path. */
  private def cycle(left: List[HotNode], byName: Map[HotNode.Name, HotNode]): List[HotNode.Name] =
    val waiting = left.map(_.name).toSet
    @annotation.tailrec
    def walk(at: HotNode.Name, path: Vector[HotNode.Name]): List[HotNode.Name] =
      val i = path.indexOf(at)
      if i >= 0 then (path.drop(i) :+ at).toList
      else
        depNames(byName(at)).find(waiting) match
          case Some(next) => walk(next, path :+ at)
          case None       => (path :+ at).toList // cannot happen: every waiting node waits on a waiting one
    walk(left.head.name, Vector.empty)

  /** The nodes a set of changed keys concerns directly. */
  def touched(nodes: List[HotNode], changed: Iterable[ConfigReload.Key]): Set[HotNode.Name] =
    nodes.filter(n => keyNames(n).exists(k => changed.exists(c => keyMatches(c, k)))).map(_.name).toSet

  /** `roots` and every node that depends on one of them, transitively. */
  def dependents(nodes: List[HotNode], roots: Set[HotNode.Name]): Set[HotNode.Name] =
    @annotation.tailrec
    def grow(acc: Set[HotNode.Name]): Set[HotNode.Name] =
      val more = nodes.filter(n => !acc(n.name) && depNames(n).exists(acc)).map(_.name)
      if more.isEmpty then acc else grow(acc ++ more)
    grow(roots)

  /** What a reload rebuilds, in build order: the nodes the changed keys (plus `forced`) concern and everything
    * depending on them. Nothing changed = nothing to rebuild.
    */
  def plan(
      nodes: List[HotNode],
      changed: Iterable[ConfigReload.Key],
      forced: Set[HotNode.Name] = Set.empty
  ): Either[Problems, List[HotNode.Name]] =
    order(nodes).map { ordered =>
      val rebuild = dependents(nodes, touched(nodes, changed) ++ forced.intersect(nodes.map(_.name).toSet))
      ordered.filter(rebuild)
    }
