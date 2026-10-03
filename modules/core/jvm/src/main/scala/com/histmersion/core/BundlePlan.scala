package com.histmersion.core

/** What an import does with each line of a bundle — milestone M2, WITHOUT matching
  * (docs/research-merge-import.md §7.3): a foreign row is the local row with the same uid, or no row.
  * Pure; the importer gathers the local state and applies the decisions.
  *
  * The rules are those of a two-way merge with the ledger as common ancestor (§3):
  *
  *  - same rev on both sides → nothing to do;
  *  - the bundle is this instance's own → ours is the newer state of every row in it; keep ours;
  *  - the foreign rev is the one we merged last time → it has not changed over there; keep ours;
  *  - our rev is the one the last merge left → it has not changed here; take theirs (fast-forward);
  *  - otherwise both changed (or we never merged it): a CONFLICT, left as it is for the matcher and
  *    the resolution file of M3 — never decided by timestamp.
  *
  * A row deleted here (a tombstone) is not brought back; a tombstone in the bundle deletes the row
  * here only when the row is still what the last merge left, and is recorded either way, so the
  * deletion travels on. */
object BundlePlan:

  enum Decision:
    case Insert, FastForward, Unchanged, KeepLocal, Conflict, SkipDeleted, Delete, RecordTombstone

    /** Does it write the row (or remove it)? */
    def writes: Boolean = this match
      case Insert | FastForward | Delete => true
      case _                             => false

  /** The ledger's memory of a uid: the foreign rev merged and the local rev it left. */
  final case class Ledger(foreignRev: String, localRevAfter: Option[String])

  /** A uid as this instance knows it: the live row's rev, whether it is tombstoned, the ledger. */
  final case class Local(rev: Option[String], tombstoned: Boolean, ledger: Option[Ledger])

  object Local:
    val absent: Local = Local(None, tombstoned = false, None)

  def entity(foreignRev: String, local: Local, ownBundle: Boolean): Decision =
    local.rev match
      case Some(r) if r == foreignRev                                        => Decision.Unchanged
      case Some(_) if ownBundle                                              => Decision.KeepLocal
      case Some(_) if local.ledger.exists(_.foreignRev == foreignRev)        => Decision.KeepLocal
      case Some(r) if local.ledger.exists(_.localRevAfter.contains(r))       => Decision.FastForward
      case Some(_)                                                           => Decision.Conflict
      case None if local.tombstoned                                          => Decision.SkipDeleted
      case None                                                              => Decision.Insert

  def tombstone(local: Local, ownBundle: Boolean): Decision =
    local.rev match
      case Some(_) if ownBundle                                              => Decision.KeepLocal
      case Some(r) if local.ledger.exists(_.localRevAfter.contains(r))       => Decision.Delete
      case Some(_)                                                           => Decision.Conflict
      case None if local.tombstoned                                          => Decision.Unchanged
      case None                                                              => Decision.RecordTombstone

  /** The per-kind tally of the report. A recorded tombstone of a row that never existed here changes
    * nothing visible, so it counts as unchanged. */
  def counts(kind: BundleKind, ds: Iterable[Decision]): BundleKindCounts =
    ds.foldLeft(BundleKindCounts(kind)) { (c, d) =>
      d match
        case Decision.Insert          => c.copy(inserted = c.inserted + 1)
        case Decision.FastForward     => c.copy(updated = c.updated + 1)
        case Decision.Unchanged       => c.copy(unchanged = c.unchanged + 1)
        case Decision.RecordTombstone => c.copy(unchanged = c.unchanged + 1)
        case Decision.KeepLocal       => c.copy(keptLocal = c.keptLocal + 1)
        case Decision.Conflict        => c.copy(conflicts = c.conflicts + 1)
        case Decision.SkipDeleted     => c.copy(skippedDeleted = c.skippedDeleted + 1)
        case Decision.Delete          => c.copy(deleted = c.deleted + 1)
    }

  /** The ledger row a decision leaves, when it changes it: the foreign rev merged and the local rev
    * that merge left — the new common ancestor. A row taken or found equal is in sync (both revs the
    * foreign one); a row deleted here by a foreign tombstone, or a tombstone recorded, is gone. A row
    * KEPT is not in sync — ours has changes theirs lacks — so the old ancestor stays, and a later
    * change over there is seen as the conflict it is; a conflict or a skip leaves it as well.
    * For a tombstone `foreignRev` is a fixed marker. */
  def ledgerAfter(d: Decision, foreignRev: String, tombstone: Boolean): Option[Ledger] =
    d match
      case Decision.Conflict | Decision.SkipDeleted | Decision.KeepLocal => None
      case _ if tombstone                                               => Some(Ledger(foreignRev, None))
      case Decision.Insert | Decision.FastForward | Decision.Unchanged  => Some(Ledger(foreignRev, Some(foreignRev)))
      case Decision.Delete | Decision.RecordTombstone                   => Some(Ledger(foreignRev, None))

  /** Lines that cannot be written because a row they need is gone here: whatever writes a line whose
    * REQUIRED reference names a uid that is deleted here (or being deleted) is skipped as deleted too,
    * transitively (an asset link of an asset of a deleted source). Pure. */
  def cascade(decisions: Map[java.util.UUID, Decision], refs: List[BundleEntities.Reference],
      deletedHere: java.util.UUID => Boolean): Map[java.util.UUID, Decision] =
    def gone(ds: Map[java.util.UUID, Decision])(u: java.util.UUID): Boolean =
      ds.get(u) match
        case Some(Decision.SkipDeleted) | Some(Decision.Delete) | Some(Decision.RecordTombstone) => true
        case Some(_)                                                                         => false
        case None                                                                            => deletedHere(u)
    @annotation.tailrec
    def loop(ds: Map[java.util.UUID, Decision]): Map[java.util.UUID, Decision] =
      val skip = refs.filter(r => r.required && ds.get(r.owner).exists(d => d == Decision.Insert || d == Decision.FastForward) &&
        gone(ds)(r.uid)).map(_.owner).toSet
      if skip.isEmpty then ds else loop(ds ++ skip.map(_ -> Decision.SkipDeleted))
    loop(decisions)
