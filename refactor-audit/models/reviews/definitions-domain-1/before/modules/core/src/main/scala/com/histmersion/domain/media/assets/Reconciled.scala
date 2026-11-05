package com.histmersion.domain.media.assets

import scala.annotation.tailrec

/** What replacing a source's figures does to the rows already there (#826), given (name, sha256) of the existing and
  * the incoming set: an incoming name that exists is that row (updated in place); an incoming name that does not exist
  * takes over an existing row with the same sha256 that no name claimed (`renames`, existing → incoming); every
  * existing row left over is `deletes`.
  */
final case class Reconciled(renames: List[(String, String)], deletes: List[String])

object Reconciled:
  def of(existing: List[(String, String)], incoming: List[(String, String)]): Reconciled =
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
