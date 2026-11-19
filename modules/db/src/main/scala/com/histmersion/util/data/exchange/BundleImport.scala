package com.histmersion
package util.data.exchange
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.util.data.exchange.{
  BundleFormat,
  BundleImporter,
  BundlePlan,
  BundleSql,
  CarriedKinds,
  ImportAbort,
  ImportOptions,
  MergeRepo,
  MergeRuleSet
}
import com.histmersion.util.data.exchange.BundlePlan.Decision
import com.histmersion.util.storage.{ByteSource, ContentStore}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** What `bundle import` and `merge apply` write for each kind of record (docs/research-merge-import.md §7.3): the
  * [[Writer]] whose steps the carried kinds register. The transaction, the lock, the plan's checks, the blob pass and
  * the ledger are [[BundleImporter]]'s.
  *
  * A foreign row is the local row with the same uid, or no row; what happens to it is [[BundlePlan]]'s decision, or the
  * merge plan's. Rows are written in dependency order, with the bundle's own uids, origins and timestamps.
  */
object BundleImport:
  export com.histmersion.util.data.exchange.ImportOptions as Options

  /** The ledger's foreign rev for a tombstone, which has no content to hash. */
  val DeletedRev: BundlePlan.Rev = BundlePlan.Rev(BundlePlan.DeletedRev)

  private def importing(on: MergeRepo.Importing)(using DbCon): Unit = MergeRepo.importing(on)

  // ── the writes ──────────────────────────────────────────────────────────────────────────────

  /** What an import's steps compute once, the first time one asks ([[Writer.once]]): an index of the bundle's lines a
    * kind's steps share.
    */
  final class Once[A](val make: Writer => A)

  /** Applies the decided lines, one step per kind's part. One instance per import; it remembers what the later steps
    * need, and gives every kind's steps what they share: the lines and this collection's rows, which lines are written,
    * what the merge decided besides them, the labels records have here now, and the rows written as this collection's
    * own.
    */
  final class Writer(
      /** The bundle's lines, in this collection's uids and labels. */
      val es: BundleEntities,
      decided: List[MergePlanner.Row],
      decisionOf: Map[UUID, Decision],
      /** Whether the line of a uid is written. */
      val writes: UUID => Boolean,
      /** This collection's rows as lines, before the import. */
      val local: BundleEntities,
      skipAliases: Set[Citekey],
      /** What the merge does besides writing the decided lines. */
      val merged: MergePlanner.Apply,
      /** The import's options: the chunk size, the point tolerance, the referrers a rename runs. */
      val opts: Options,
      kinds: CarriedKinds
  )(using
      /** The import's transaction, which every step writes in. */
      val transaction: DbCon
  ):

    private def decision(u: UUID): Option[Decision] = decisionOf.get(u)

    /** Whether the line of `u` is a new row here. */
    def inserts(u: UUID): Boolean = decision(u).contains(Decision.Insert)

    /** Whether a source alias of the bundle is left out (an alias the merge gave to another work). */
    def skipsAlias(alias: Citekey): Boolean = skipAliases(alias)

    /** citekey / slug of a uid as it stands NOW (after the writes so far). */
    def citekeyOf(u: UUID): Citekey =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[Citekey].run().head

    /** The citekey of a source here, when its uid names one. */
    def citekeyHere(u: UUID): Option[Citekey] =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[Citekey].run().headOption

    /** The label a target has HERE, when its uid names a row here; else the bundle's label. */
    def targetLabel(tt: Writer.TargetType, tid: Writer.TargetId, tuid: Option[UUID]): Writer.TargetId =
      tuid
        .flatMap { u =>
          val q = tt.value match
            case "finding" => Some(sql"SELECT id FROM findings WHERE uid = $u")
            case "source"  => Some(sql"SELECT citekey FROM sources WHERE uid = $u")
            case "article" => Some(sql"SELECT slug FROM articles WHERE uid = $u")
            case "event"   => Some(sql"SELECT id FROM events WHERE uid = $u")
            case _         => None
          q.flatMap(_.query[Writer.TargetId].run().headOption)
        }
        .getOrElse(tid)

    /** `work` written as this collection's own: the change clocks move and a delete leaves a tombstone. */
    def asLocal(work: => Unit): Unit =
      importing(MergeRepo.Importing(false))
      work
      importing(MergeRepo.Importing(true))

    // what the derived step and pass 2 need
    private val sourcesTouched = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val sourcesInserted = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val computed = scala.collection.mutable.Map.empty[Once[?], Any]

    /** A source a kind's step wrote under: its finding places are placed again in the derived step. */
    def touchSource(u: UUID): Unit = sourcesTouched += u

    /** The sources a step wrote under, in the order they were first touched. */
    def touchedSources: List[UUID] = sourcesTouched.toList

    /** A source a step inserted: an article's `[[x]]` may name it now (every article is linked again). */
    def insertSource(u: UUID): Unit = sourcesInserted += u

    /** The sources the import inserted, in the order they were. */
    def insertedSources: List[UUID] = sourcesInserted.toList

    /** What `o` computes of this import, computed the first time a step asks. */
    def once[A](o: Once[A]): A = computed.getOrElseUpdate(o, o.make(this)).asInstanceOf[A]

    /** Tombstones first: a row the other side deleted makes room for one it created in its place. */
    def tombstones(): Unit =
      val tombs = es.tombstones.map(t => t.uid -> t).toMap
      decided
        .filter(d => d.tombstone.value && (d.decision == Decision.Delete || d.decision == Decision.RecordTombstone))
        .foreach { d =>
          val t = tombs(d.uid)
          if d.decision == Decision.Delete then
            val table = kinds.tableOf(t.kind).value
            sql"DELETE FROM ${SqlLiteral(table)} WHERE uid = ${t.uid.toString}::uuid".update
              .run()
          sql"""INSERT INTO tombstones (uid, kind, origin, deleted_at)
                VALUES (${t.uid}, ${t.kind.wire}, ${t.origin}, ${t.deletedAt}::timestamptz)
                ON CONFLICT (uid) DO UPDATE SET deleted_at = EXCLUDED.deleted_at""".update.run()
        }

    /** Rename what the other side renamed (the same uid, a new label), through the ordinary rename path, before the row
      * itself is written (an upsert keeps the label it finds).
      */
    def followRenames[A, L](
        lines: List[A],
        uid: A => UUID,
        label: A => L,
        localLabel: Map[UUID, L],
        what: PlanQuestions.LabelTakenWhat
    )(rename: (L, L) => Either[Api.Error, ?]): Unit =
      lines.filter(a => writes(uid(a))).foreach { a =>
        localLabel.get(uid(a)).filter(old => !old.equals(label(a))).foreach { old =>
          importing(MergeRepo.Importing(false))
          rename(old, label(a)).left
            .foreach(e => throw ImportAbort(NonEmptyVector.toVector(e.problems).toList))
          importing(MergeRepo.Importing(true))
        }
      }

  object Writer:
    type TargetId = TargetId.T
    object TargetId extends Newtype[String, Pure]
    type TargetType = TargetType.T
    object TargetType extends Newtype[String, Pure]
