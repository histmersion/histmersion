package com.histmersion.util.data.exchange

import scala.annotation.tailrec
import com.histmersion.api.given
import com.histmersion.api.exchange.Bundle

/** What an import does with each line of a bundle — milestone M2, WITHOUT matching (docs/research-merge-import.md
  * §7.3): a foreign row is the local row with the same uid, or no row. Pure; the importer gathers the local state and
  * applies the decisions.
  *
  * The rules are those of a two-way merge with the ledger as common ancestor (§3):
  *
  *   - same rev on both sides → nothing to do;
  *   - the bundle is this instance's own → ours is the newer state of every row in it; keep ours;
  *   - the foreign rev is the one we merged last time → it has not changed over there; keep ours;
  *   - our rev is the one the last merge left → it has not changed here; take theirs (fast-forward);
  *   - otherwise both changed (or we never merged it): a CONFLICT, left as it is for the matcher and the resolution
  *     file of M3 — never decided by timestamp.
  *
  * A row deleted here (a tombstone) is not brought back; a tombstone in the bundle deletes the row here only when the
  * row is still what the last merge left, and is recorded either way, so the deletion travels on.
  */
object BundlePlan:

  enum Decision:
    case Insert, FastForward, Unchanged, KeepLocal, Conflict, SkipDeleted, Delete, RecordTombstone

    /** A conflict resolved `keep-left`: ours stays, and the ledger records that it was decided, so the same foreign
      * version does not ask again (M3).
      */
    case Kept

    /** Resolved `keep-left` where the bundle's row has no counterpart here (its label is taken by a different record):
      * not imported (M3).
      */
    case Skip

    /** Belongs to a source that is the same work as one here under another uid: folding the two together is
      * `merge apply` (M5), so the import holds it back (M3).
      */
    case Held

    /** Does it write the row (or remove it)? */
    def writes: Boolean = this match
      case Insert | FastForward | Delete => true
      case _                             => false

  /** The ledger's foreign rev for a tombstone, which has no content to hash. */
  val DeletedRev = "deleted"

  /** The ledger's memory of a uid: the foreign rev merged and the local rev it left. */
  final case class Ledger(foreignRev: String, localRevAfter: Option[String])

  /** A uid as this instance knows it: the live row's rev, whether it is tombstoned, the ledger. */
  final case class Local(rev: Option[String], tombstoned: Boolean, ledger: Option[Ledger])

  object Local:
    val absent: Local = Local(None, tombstoned = false, None)

  def entity(foreignRev: String, local: Local, ownBundle: Boolean): Decision =
    entity(foreignRev, foreignRev, local, ownBundle)

  /** As above, for a line whose uid (and the uids it refers to) were mapped onto this instance's: `foreignRev` is the
    * bundle's rev, as the ledger remembers it; `localizedRev` the rev of the same line in this instance's terms,
    * comparable with the local row's. Without a mapping they are equal.
    */
  def entity(foreignRev: String, localizedRev: String, local: Local, ownBundle: Boolean): Decision =
    local.rev match
      case Some(r) if r == localizedRev                                => Decision.Unchanged
      case Some(_) if ownBundle                                        => Decision.KeepLocal
      case Some(_) if local.ledger.exists(_.foreignRev == foreignRev)  => Decision.KeepLocal
      case Some(r) if local.ledger.exists(_.localRevAfter.contains(r)) => Decision.FastForward
      case Some(_)                                                     => Decision.Conflict
      case None if local.tombstoned                                    => Decision.SkipDeleted
      case None                                                        => Decision.Insert

  def tombstone(local: Local, ownBundle: Boolean): Decision =
    local.rev match
      case Some(_) if ownBundle => Decision.KeepLocal
      // the deletion was seen before and the row kept by decision (`keep-left`)
      case Some(_) if local.ledger.exists(l => l.foreignRev == DeletedRev && l.localRevAfter.isEmpty) =>
        Decision.KeepLocal
      case Some(r) if local.ledger.exists(_.localRevAfter.contains(r)) => Decision.Delete
      case Some(_)                                                     => Decision.Conflict
      case None if local.tombstoned                                    => Decision.Unchanged
      case None                                                        => Decision.RecordTombstone

  /** The per-kind tally of the report. A recorded tombstone of a row that never existed here changes nothing visible,
    * so it counts as unchanged.
    */
  def counts(kind: Bundle.Kind, ds: Iterable[Decision]): com.histmersion.api.exchange.Bundle.KindCounts =
    val decisions = ds.iterator
    @tailrec def loop(
        inserted: Int,
        updated: Int,
        unchanged: Int,
        keptLocal: Int,
        conflicts: Int,
        deleted: Int,
        skippedDeleted: Int,
        held: Int
    ): com.histmersion.api.exchange.Bundle.KindCounts =
      if !decisions.hasNext then
        com.histmersion.api.exchange.Bundle.KindCounts(
          kind,
          inserted,
          updated,
          unchanged,
          keptLocal,
          conflicts,
          deleted,
          skippedDeleted,
          held = held
        )
      else
        decisions.next() match
          case Decision.Insert =>
            loop(inserted + 1, updated, unchanged, keptLocal, conflicts, deleted, skippedDeleted, held)
          case Decision.FastForward =>
            loop(inserted, updated + 1, unchanged, keptLocal, conflicts, deleted, skippedDeleted, held)
          case Decision.Unchanged | Decision.RecordTombstone =>
            loop(inserted, updated, unchanged + 1, keptLocal, conflicts, deleted, skippedDeleted, held)
          case Decision.KeepLocal | Decision.Kept | Decision.Skip =>
            loop(inserted, updated, unchanged, keptLocal + 1, conflicts, deleted, skippedDeleted, held)
          case Decision.Conflict =>
            loop(inserted, updated, unchanged, keptLocal, conflicts + 1, deleted, skippedDeleted, held)
          case Decision.Delete =>
            loop(inserted, updated, unchanged, keptLocal, conflicts, deleted + 1, skippedDeleted, held)
          case Decision.SkipDeleted =>
            loop(inserted, updated, unchanged, keptLocal, conflicts, deleted, skippedDeleted + 1, held)
          case Decision.Held =>
            loop(inserted, updated, unchanged, keptLocal, conflicts, deleted, skippedDeleted, held + 1)
    loop(0, 0, 0, 0, 0, 0, 0, 0)

  /** The ledger row a decision leaves, when it changes it: the foreign rev merged and the local rev that merge left —
    * the new common ancestor. A row taken or found equal is in sync (both revs the foreign one); a row deleted here by
    * a foreign tombstone, or a tombstone recorded, is gone. A row KEPT is not in sync — ours has changes theirs lacks —
    * so the old ancestor stays, and a later change over there is seen as the conflict it is; a conflict or a skip
    * leaves it as well. For a tombstone `foreignRev` is a fixed marker.
    */
  def ledgerAfter(d: Decision, foreignRev: String, tombstone: Boolean): Option[Ledger] =
    ledgerAfter(d, foreignRev, foreignRev, None, tombstone)

  /** As above with the line mapped onto this instance (`localizedRev`, see [[entity]]), and the local rev a `Kept` row
    * has: a conflict decided `keep-left` is in sync by decision — the foreign version was seen and ours chosen — so its
    * ledger row records both.
    */
  def ledgerAfter(
      d: Decision,
      foreignRev: String,
      localizedRev: String,
      localRev: Option[String],
      tombstone: Boolean
  ): Option[Ledger] =
    d match
      case Decision.Conflict | Decision.SkipDeleted | Decision.KeepLocal | Decision.Skip | Decision.Held => None
      case _ if tombstone                                              => Some(Ledger(foreignRev, None))
      case Decision.Kept                                               => Some(Ledger(foreignRev, localRev))
      case Decision.Insert | Decision.FastForward | Decision.Unchanged => Some(Ledger(foreignRev, Some(localizedRev)))
      case Decision.Delete | Decision.RecordTombstone                  => Some(Ledger(foreignRev, None))

  /** Lines that cannot be written because a row they need is gone here: whatever writes a line whose REQUIRED reference
    * names a uid that is deleted here (or being deleted) is skipped as deleted too, transitively (an asset link of an
    * asset of a deleted source). Pure.
    */
  def cascade(
      decisions: Map[java.util.UUID, Decision],
      refs: List[BundleEntities.Reference],
      deletedHere: java.util.UUID => Boolean
  ): Map[java.util.UUID, Decision] =
    def gone(ds: Map[java.util.UUID, Decision])(u: java.util.UUID): Boolean =
      ds.get(u) match
        case Some(Decision.SkipDeleted) | Some(Decision.Delete) | Some(Decision.RecordTombstone) |
            Some(Decision.Skip) =>
          true
        case Some(_) => false
        case None    => deletedHere(u)
    def held(ds: Map[java.util.UUID, Decision])(u: java.util.UUID): Boolean = ds.get(u).contains(Decision.Held)
    def writing(ds: Map[java.util.UUID, Decision])(u: java.util.UUID): Boolean =
      ds.get(u).exists(d => d == Decision.Insert || d == Decision.FastForward)
    // every round turns at least one writing line into a non-writing one, so it ends within
    // `decisions.size` rounds
    @annotation.tailrec
    def loop(ds: Map[java.util.UUID, Decision]): Map[java.util.UUID, Decision] =
      val needing = refs.filter(r => r.required && writing(ds)(r.owner))
      val skip = needing.filter(r => gone(ds)(r.uid)).map(_.owner).toSet
      val hold = needing.filter(r => held(ds)(r.uid)).map(_.owner).toSet -- skip
      if skip.isEmpty && hold.isEmpty then ds
      else loop(ds ++ skip.map(_ -> Decision.SkipDeleted) ++ hold.map(_ -> Decision.Held))
    loop(decisions)
