package histdb.db

import histdb.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Flat projections for `assets` (Magnum maps a SELECT's columns positionally). */
private[db] final case class AssetInfoRow(
    name: String, mime: String, bytes: Int, sha256: String, width: Option[Int], height: Option[Int]
)
private[db] final case class AssetBytesRow(mime: String, bytes: Array[Byte])

given DbCodec[AssetInfoRow]  = DbCodec.derived
given DbCodec[AssetBytesRow] = DbCodec.derived

/** The figures that belong with a source's Markdown (plan §1.8).
  *
  * Stored as bytea keyed by `(citekey, name)`, which is exactly the `assets/<name>` link the body
  * carries — so serving one is a primary-key lookup, and re-running a conversion simply replaces the
  * set. Within a source the same picture (a logo on every page) is stored ONCE: writes are
  * deduplicated by sha256, and the later names are dropped. Repos are stateless: the `DataSource` is
  * a parameter, never a field. */
object AssetRepo:

  /** One asset as the caller supplies it — decoded bytes, not base64. */
  final case class Stored(name: String, mime: String, bytes: Array[Byte], width: Option[Int], height: Option[Int])

  object Stored:
    /** Decode a wire [[AssetInput]]; `Left` with a readable reason when the payload is unusable. */
    def of(in: AssetInput, maxBytes: Long): Either[String, Stored] =
      val name = AssetLinks.safeName(in.name)
      scala.util.Try(java.util.Base64.getDecoder.decode(in.base64.trim)).toEither.left
        .map(e => s"asset '$name': not valid base64 (${e.getMessage})")
        .flatMap { raw =>
          if raw.isEmpty then Left(s"asset '$name': empty")
          else if raw.length > maxBytes then
            Left(s"asset '$name': ${raw.length} bytes exceeds the ${maxBytes}-byte limit (limits.asset-bytes)")
          else
            // a client may omit the dimensions (the seed path does); read them off the header
            val size = ImageSize.of(raw)
            Right(Stored(name, in.mime, raw, in.width.orElse(size.map(_._1)), in.height.orElse(size.map(_._2))))
        }

  def sha256(bytes: Array[Byte]): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(b => f"${b & 0xff}%02x").mkString

  /** REPLACE the whole set for one source (what a re-conversion does). Returns how many were kept
    * after sha256 deduplication. */
  def replace(ds: DataSource, citekey: String, assets: List[Stored]): Int =
    transact(ds):
      sql"DELETE FROM assets WHERE citekey = $citekey".update.run()
      insert(citekey, dedupe(assets))

  /** Add to the set, leaving anything already stored under the same name alone (the seed importer's
    * figures, and `POST /api/sources/{ck}/assets`). Returns how many rows were written. */
  def add(ds: DataSource, citekey: String, assets: List[Stored]): Int =
    transact(ds)(insert(citekey, dedupe(assets)))

  /** First writer of a given sha256 wins; later duplicates are dropped (not stored twice). */
  def dedupe(assets: List[Stored]): List[Stored] =
    assets
      .foldLeft((Set.empty[String], Set.empty[String], List.empty[Stored])) {
        case ((shas, names, acc), a) =>
          val sha = sha256(a.bytes)
          if shas.contains(sha) || names.contains(a.name) then (shas, names, acc)
          else (shas + sha, names + a.name, acc :+ a)
      }
      ._3

  private def insert(citekey: String, assets: List[Stored])(using DbCon): Int =
    assets.foreach { a =>
      val sha = sha256(a.bytes)
      sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height)
            VALUES ($citekey, ${a.name}, ${a.mime}, ${a.bytes}, $sha, ${a.width}, ${a.height})
            ON CONFLICT (citekey, name) DO UPDATE SET
              mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256,
              width = EXCLUDED.width, height = EXCLUDED.height""".update.run()
    }
    assets.size

  def list(ds: DataSource, citekey: String): Vector[AssetInfo] =
    connect(ds):
      sql"""SELECT name, mime, length(bytes), sha256, width, height
            FROM assets WHERE citekey = $citekey ORDER BY name"""
        .query[AssetInfoRow].run()
        .map(r => AssetInfo(r.name, r.mime, r.bytes, r.sha256, r.width, r.height))

  /** The bytes to stream, with the mime type to serve them as. */
  def read(ds: DataSource, citekey: String, name: String): Option[(String, Array[Byte])] =
    connect(ds):
      sql"SELECT mime, bytes FROM assets WHERE citekey = $citekey AND name = $name"
        .query[AssetBytesRow].run().headOption.map(r => (r.mime, r.bytes))

  def count(ds: DataSource, citekey: String): Int =
    connect(ds)(sql"SELECT count(*) FROM assets WHERE citekey = $citekey".query[Int].run().headOption.getOrElse(0))

  // ── the uploaded ORIGINAL's digest, on `sources.sha256` (V001 already has the column) ─────────
  /** The citekey that already holds this upload, if any — `POST /api/ingest` refuses a duplicate
    * with it unless `force` (plan §1.8). */
  def citekeyOfSha256(ds: DataSource, sha: String): Option[String] =
    connect(ds):
      sql"SELECT citekey FROM sources WHERE sha256 = $sha ORDER BY citekey LIMIT 1".query[String].run().headOption

  def setSha256(ds: DataSource, citekey: String, sha: String): Unit =
    val _ = transact(ds)(sql"UPDATE sources SET sha256 = $sha WHERE citekey = $citekey".update.run())

  def sha256Of(ds: DataSource, citekey: String): Option[String] =
    connect(ds):
      sql"SELECT sha256 FROM sources WHERE citekey = $citekey".query[Option[String]].run().headOption.flatten
