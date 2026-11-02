package com.histmersion.util.data.exchange
import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.values.{BundleKind, Confidence, SourceStatus}
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.util.storage.ContentStore
import com.histmersion.util.database.Sql
import com.histmersion.util.database.given
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.api.exchange.BundleReference

given DbCodec[InstanceInfo] = DbCodec.derived

/** The bundle's view of this collection itself (docs/research-merge-import.md §4): the instance, the fingerprint of the
  * reference data and the schema version. The owners read their own lines (`<Kind>BundleRepo`).
  */
object BundleRepo:

  // ── this instance ──────────────────────────────────────────────────────────────────────────

  def instance(using DbCon): InstanceInfo =
    Sql.raw(s"SELECT id, label, ${ts("created_at")} FROM instance").frag.query[InstanceInfo].run().head

  /** Name this instance; a blank label clears it. */
  def setLabel(label: Option[String])(using DbCon): InstanceInfo =
    val l = label.map(_.trim).filter(_.nonEmpty)
    sql"UPDATE instance SET label = $l".update.run()
    instance

  // ── reads, one per file ────────────────────────────────────────────────────────────────────

  /** The gazetteer and the named periods, fingerprinted: not carried, compared ([[BundleReference]]). */
  private[histmersion] def reference(using DbCon): BundleReference =
    // the SEED rows only: a place or period of this collection's own travels in the bundle itself
    def digest(table: String): (Int, String) =
      Sql
        .raw(s"""SELECT count(*)::int, coalesce(md5(string_agg(id, ',' ORDER BY id COLLATE "C")), '')
                  FROM $table WHERE dataset = 'seed'""")
        .frag
        .query[(Int, String)]
        .run()
        .head
    val (np, dp) = digest("places")
    val (nq, dq) = digest("periods")
    BundleReference(np, dp, nq, dq)

  // ── the schema ─────────────────────────────────────────────────────────────────────────────

  /** The Flyway version of this database, read in the caller's transaction ("33"). */
  private[histmersion] def schemaVersion(using DbCon): Option[String] =
    sql"""SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
          ORDER BY installed_rank DESC LIMIT 1""".query[String].run().headOption
