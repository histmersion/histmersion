package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.Title

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Implemented by each domain whose records others point at — a person's bookmarks, notes and read marks, a goal's
  * links, a picture's links. The pointing side stores an [[ItemRef]] and asks here, when it shows the reference, what
  * the record is now: renames, merges and deletions are answered at read time, so no domain has to tell the ones that
  * point at it.
  */
trait Referable:
  def kinds: Set[ItemKind]

  /** The record `key` names now — an alias of a renamed key followed — or why there is none. */
  def resolve(kind: ItemKind, key: ItemKey): Either[com.histmersion.domain.Referable.ResolveResultPart1, ItemRef]

  /** How each record shows wherever it is referenced; a reference to a record this domain never held is left out. One
    * call per kind on a page, batched.
    */
  def describe(refs: Set[ItemRef]): Map[ItemRef, Described]

object Referable:

  /** The records of one kind held in memory (tests), resolved and described by the rules the tables keep: a uid that
    * survives a rename, an alias for the old key, a merge's uid redirect, a deletion's tombstone.
    */
  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class InMemory(kind: ItemKind, records: AtomicReference[Records] = AtomicReference(Records()))
      extends Referable:
    val kinds: Set[ItemKind] = Set(kind)

    def put(key: ItemKey, title: Title, uid: Option[ItemUid]): Unit =
      records.updateAndGet(r => r.modify(_.live).setTo(r.live + (key -> Record(uid, title)))): Unit

    def rename(from: ItemKey, to: ItemKey): Unit =
      records.updateAndGet { r =>
        r.live.get(from).fold(r)(rec => r.copy(live = r.live - from + (to -> rec), aliases = r.aliases + (from -> to)))
      }: Unit

    /** `from` folded into `into`: its uid redirects to the survivor's. */
    def merge(from: ItemKey, into: ItemKey): Unit =
      records.updateAndGet { r =>
        (r.live.get(from), r.live.get(into)) match
          case (Some(gone), Some(kept)) =>
            val redirect = gone.uid.zip(kept.uid).toMap
            r.modify(_.live).setTo(r.live - from).modify(_.redirects).setTo(r.redirects ++ redirect)
          case _ => r
      }: Unit

    def delete(key: ItemKey, at: Instant): Unit =
      records.updateAndGet { r =>
        r.live.get(key).fold(r)(rec => r.copy(live = r.live - key, tombstones = r.tombstones ++ rec.uid.map(_ -> at)))
      }: Unit

    def resolve(kind: ItemKind, key: ItemKey): Either[com.histmersion.domain.Referable.InMemory.ResolveResultPart1, ItemRef] =
      val r = records.get
      r.live
        .get(key)
        .map(rec => ItemRef(kind, key, rec.uid))
        .orElse(r.aliases.get(key).flatMap(r.live.get).map(rec => ItemRef(kind, key, rec.uid)))
        .toRight(s"no ${kind.value} '${key.value}'")

    def describe(refs: Set[ItemRef]): Map[ItemRef, Described] =
      val r = records.get
      refs.flatMap(ref => r.describe(ref).map(ref -> _)).toMap

  object InMemory:
    type ResolveResultPart1 = ResolveResultPart1.T
    object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One record: its uid (where its kind has uids) and its title. */
  final case class Record(uid: Option[ItemUid], title: Title)

  final case class Records(
      live: Map[ItemKey, Record] = Map.empty,
      aliases: Map[ItemKey, ItemKey] = Map.empty,
      redirects: Map[ItemUid, ItemUid] = Map.empty,
      tombstones: Map[ItemUid, Instant] = Map.empty
  ):
    private def byUid(uid: ItemUid): Option[(ItemKey, Record)] = live.find(_._2.uid.contains(uid))

    /** Where the merges since sent `uid`: the last uid of its redirects, if it has any. */
    private def redirected(uid: ItemUid, hops: com.histmersion.domain.Referable.Records.RedirectedHops): Option[ItemUid] =
      @scala.annotation.tailrec
      def last(at: ItemUid, hops: Int): ItemUid = redirects.get(at) match
        case Some(next) if hops < Records.MaxHops => last(next, hops + 1)
        case _                                    => at
      redirects.get(uid).map(last(_, hops + 1))

    def describe(ref: ItemRef): Option[Described] = ref.uid match
      case Some(uid) =>
        byUid(uid) match
          case Some((key, rec)) =>
            Some(Described(key, Some(rec.title), if key == ref.key then RecordState.Live else RecordState.Renamed(key)))
          case None =>
            val merged = redirected(uid, 0).flatMap(to => byUid(to).map((key, rec) => (to, key, rec)))
            merged match
              case Some((to, key, rec)) =>
                Some(Described(key, Some(rec.title), RecordState.MergedInto(ItemRef(ref.kind, key, Some(to)))))
              case None =>
                (redirected(uid, 0).toList :+ uid)
                  .flatMap(tombstones.get)
                  .headOption
                  .map(at => Described(ref.key, None, RecordState.Deleted(at)))
      case None =>
        live
          .get(ref.key)
          .map(rec => Described(ref.key, Some(rec.title), RecordState.Live))
          .orElse(
            aliases
              .get(ref.key)
              .flatMap(k => live.get(k).map(rec => Described(k, Some(rec.title), RecordState.Renamed(k))))
          )

  object Records:
    /** How many merges in a row are followed: a chain longer than this is a loop the merge never writes. */
    type RedirectedHops = RedirectedHops.T
    object RedirectedHops extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val MaxHops = 16
