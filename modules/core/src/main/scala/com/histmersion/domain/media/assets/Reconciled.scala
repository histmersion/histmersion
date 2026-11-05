package com.histmersion.domain.media.assets

import scala.annotation.tailrec

/** What replacing a source's figures does to the rows already there (#826), given (name, sha256) of the existing and
  * the incoming set: an incoming name that exists is that row (updated in place); an incoming name that does not exist
  * takes over an existing row with the same sha256 that no name claimed (`renames`, existing → incoming); every
  * existing row left over is `deletes`.
  */
final case class Reconciled(renames: List[(Reconciled.RenamesPart1, Reconciled.RenamesPart2)], deletes: List[Reconciled.Deletes])

object Reconciled:
  type OfExistingPart1 = OfExistingPart1.T
  object OfExistingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfExistingPart2 = OfExistingPart2.T
  object OfExistingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfIncomingPart1 = OfIncomingPart1.T
  object OfIncomingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfIncomingPart2 = OfIncomingPart2.T
  object OfIncomingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RenamesPart1 = RenamesPart1.T
  object RenamesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamesPart2 = RenamesPart2.T
  object RenamesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deletes = Deletes.T
  object Deletes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(existing: List[(com.histmersion.domain.media.assets.Reconciled.OfExistingPart1, com.histmersion.domain.media.assets.Reconciled.OfExistingPart2)], incoming: List[(com.histmersion.domain.media.assets.Reconciled.OfIncomingPart1, com.histmersion.domain.media.assets.Reconciled.OfIncomingPart2)]): Reconciled =
    val incomingNames = incoming.map(_._1).toSet
    val byName = existing.filter((n, _) => incomingNames(n)).map(_._1).toSet
    @tailrec def rename(rest: List[(String, String)], acc: List[(String, String)], used: Set[String]): Reconciled =
      rest match
        case Nil                 => Reconciled(acc.reverse, existing.map(_._1).filterNot(used))
        case (name, sha) :: tail =>
          existing.find((n, s) => s == sha && !used(n)) match
            case Some((from, _)) => rename(tail, (from -> name) :: acc, used + from)
            case None            => rename(tail, acc, used)
    rename(incoming.filterNot((n, _) => byName(n)), Nil, byName)
