package histdb.db

import histdb.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Store access for the acquisition & assessment toolset (stream 2.6): the Retraction Watch mirror,
  * stored assessments, the DOI/citekey lookups that turn a reference list into `references` edges,
  * and the Calibre gap report.
  *
  * Stateless, like every repo here — the `DataSource` is a parameter, never a field. */
object AcquireRepo:

  private final case class DoiKey(doi: String, citekey: String)
  private final case class RwRow(nature: Option[String], reason: Option[String], date: Option[String])
  private final case class GapRow(
      citekey: String, title: String, authors: String, doi: Option[String], year: Option[Int],
      venue: Option[String], summary: Option[String], topics: String, provenance: Option[String])
  private final case class AssessmentRow(
      citekey: String, checkedAt: String, retracted: Boolean, citedBy: Option[Int],
      influential: Option[Int], verdict: String)

  private given DbCodec[DoiKey]         = DbCodec.derived
  private given DbCodec[RwRow]          = DbCodec.derived
  private given DbCodec[GapRow]         = DbCodec.derived
  private given DbCodec[AssessmentRow]  = DbCodec.derived

  /** Same array delimiter as [[SourceRepo]] — it cannot occur in the data, so a joined column splits
    * back safely without an array codec. */
  private val Sep = "|~|"
  private def splitArr(s: String): List[String] =
    if s.isEmpty then Nil else s.split(java.util.regex.Pattern.quote(Sep), -1).toList

  // ── Retraction Watch mirror ─────────────────────────────────────────────────────────────────

  /** Replace the whole mirror in ONE transaction: the CSV is a full export, so a partial refresh
    * would leave a mixture of two vintages. Batched — 60 000 single inserts take minutes. */
  def replaceRetractionWatch(ds: DataSource, rows: List[Acquire.RetractionRow]): Int =
    transact(ds):
      sql"DELETE FROM retraction_watch".update.run()
      // the export can list a DOI twice (a retraction and a later reinstatement); the last wins
      val unique = rows.map(r => r.doi -> r).toMap.values.toVector
      if unique.isEmpty then 0
      else
        val _ = batchUpdate(unique)(r =>
          sql"""INSERT INTO retraction_watch (doi, reason, date, nature)
                VALUES (${r.doi}, ${r.reason}, ${Acquire.rwDate(r.date)}::date, ${r.nature})
                ON CONFLICT (doi) DO UPDATE SET
                  reason = EXCLUDED.reason, date = EXCLUDED.date, nature = EXCLUDED.nature""".update)
        unique.size

  def retractionCount(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM retraction_watch".query[Int].run().headOption.getOrElse(0))

  /** Every Retraction Watch notice recorded against this DOI (usually zero or one). */
  def retractionsFor(ds: DataSource, doi: String): List[RetractionNotice] =
    connect(ds):
      sql"""SELECT nature, reason, to_char(date, 'YYYY-MM-DD')
            FROM retraction_watch WHERE doi = ${doi.toLowerCase}""".query[RwRow].run()
        .map(r => RetractionNotice(r.nature, r.reason, r.date)).toList

  // ── stored assessments ──────────────────────────────────────────────────────────────────────

  /** Record the verdict for a stored source. `notices` is the raw JSON of everything we found, so a
    * later reader can see WHY without re-querying Crossref. Returns the `checked_at` stamp. */
  def putAssessment(
      ds: DataSource,
      citekey: String,
      verdict: String,
      retracted: Boolean,
      noticesJson: String,
      citedBy: Option[Int],
      influential: Option[Int]
  ): String =
    transact(ds):
      sql"""INSERT INTO assessments (citekey, checked_at, retracted, notices, cited_by, influential, verdict)
            VALUES ($citekey, now(), $retracted, $noticesJson::jsonb, $citedBy, $influential, $verdict)
            ON CONFLICT (citekey) DO UPDATE SET
              checked_at = now(), retracted = EXCLUDED.retracted, notices = EXCLUDED.notices,
              cited_by = EXCLUDED.cited_by, influential = EXCLUDED.influential,
              verdict = EXCLUDED.verdict""".update.run()
      sql"""SELECT to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
            FROM assessments WHERE citekey = $citekey""".query[String].run().headOption.getOrElse("")

  /** `(verdict, checkedAt)` of the last assessment, when there is one. */
  def assessmentFor(ds: DataSource, citekey: String): Option[(String, String)] =
    connect(ds):
      sql"""SELECT citekey, to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                   retracted, cited_by, influential, verdict
            FROM assessments WHERE citekey = $citekey""".query[AssessmentRow].run().headOption
        .map(r => (r.verdict, r.checkedAt))

  // ── identifier lookups (what turns a reference list into edges) ─────────────────────────────

  /** `lower(doi) -> citekey` for the whole corpus. One query: the corpus is a few hundred sources,
    * and resolving a 60-entry reference list one round-trip at a time would be sixty round trips. */
  def doiIndex(ds: DataSource): Map[String, String] =
    connect(ds):
      sql"SELECT lower(doi), citekey FROM sources WHERE doi IS NOT NULL AND doi <> ''"
        .query[DoiKey].run().map(r => r.doi -> r.citekey).toMap

  def allCitekeys(ds: DataSource): Vector[String] = SourceRepo.citekeys(ds)

  def exists(ds: DataSource, citekey: String): Boolean =
    connect(ds):
      sql"SELECT count(*)::int FROM sources WHERE citekey = $citekey".query[Int].run()
        .headOption.exists(_ > 0)

  /** Add typed edges out of `src`, ignoring ones that already exist. Returns how many were new. */
  def addEdges(ds: DataSource, edges: List[Edge]): Int =
    if edges.isEmpty then 0
    else
      transact(ds):
        edges.distinct.map { e =>
          sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, ${e.dst}, ${e.kind})
                ON CONFLICT DO NOTHING""".update.run()
        }.sum

  /** Patch just the `status` of a source (what `assess --apply` does), without disturbing anything
    * else — [[SourceRepo.patch]] does the read/merge/write in one transaction.
    *
    * `chunkChars` must be the CONFIGURED value even though this patch does not touch the body: the
    * write path re-chunks `ocr_markdown` on every write, and a zero budget would shred a megabyte of
    * text into one-character FTS rows. */
  def setStatus(ds: DataSource, citekey: String, status: SourceStatus, chunkChars: Int)
      : Either[ApiError, String] =
    SourceRepo.patch(ds, citekey,
      SourcePatch(metadata = Some(MetadataPatch(status = Some(status.toString.toLowerCase)))), chunkChars)

  // ── Calibre gaps ────────────────────────────────────────────────────────────────────────────

  /** Sources that are NOT in Calibre yet: provenance is something other than `calibre` (or missing)
    * and no `calibre_id` has been written back. The push itself happens on the host — Calibre is not
    * reachable from the container — so this endpoint only reports what is missing and the metadata
    * `calibredb add` needs. */
  def calibreGaps(ds: DataSource, limit: Option[Int]): List[CalibreGap] =
    connect(ds):
      val cap = limit.filter(_ > 0).getOrElse(Int.MaxValue)
      sql"""SELECT citekey, title, array_to_string(authors, $Sep), doi, year, venue, summary,
                   array_to_string(topics, $Sep), source
            FROM sources
            WHERE (calibre_id IS NULL OR calibre_id = '')
              AND (source IS NULL OR source <> 'calibre')
            ORDER BY citekey
            LIMIT $cap""".query[GapRow].run().toList.map { r =>
        CalibreGap(r.citekey, r.title, splitArr(r.authors), r.doi, r.year, r.venue, r.summary,
          splitArr(r.topics), r.provenance)
      }

  /** The push metadata for ONE source, whether or not it currently counts as a gap — `calibre push
    * <citekey>` needs the authors as a LIST, which the comma-joined `SourceDetail.authors` cannot
    * give back unambiguously ("Stocker, Sharon R." is one author, not two). */
  def calibrePushMeta(ds: DataSource, citekey: String): Option[CalibreGap] =
    connect(ds):
      sql"""SELECT citekey, title, array_to_string(authors, $Sep), doi, year, venue, summary,
                   array_to_string(topics, $Sep), source
            FROM sources WHERE citekey = $citekey""".query[GapRow].run().headOption.map { r =>
        CalibreGap(r.citekey, r.title, splitArr(r.authors), r.doi, r.year, r.venue, r.summary,
          splitArr(r.topics), r.provenance)
      }

  /** Write the Calibre id back after a host-side push, so the source stops showing up as a gap. */
  def setCalibreId(ds: DataSource, citekey: String, calibreId: String): Either[ApiError, Unit] =
    transact(ds):
      val n = sql"UPDATE sources SET calibre_id = $calibreId, updated_at = now() WHERE citekey = $citekey"
        .update.run()
      if n == 0 then Left(Write.notFound(citekey)) else Right(())
