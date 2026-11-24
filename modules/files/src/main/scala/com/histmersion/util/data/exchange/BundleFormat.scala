package com.histmersion
package util.data.exchange
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.{Json, JsonObject}
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.api.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID
import com.histmersion.api.Sha256
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount

/** The pure half of the `.hmx` bundle (docs/research-merge-import.md §4): canonical JSON, record revisions, the
  * deterministic bundle id, and the checks a manifest must pass. The tar stream is [[BundleTar]]; reading the database
  * is `db.BundleExport`/`db.BundleImport`.
  *
  * ==Canonical JSON==
  * One line per record: keys sorted, no whitespace, absent (`None`) fields left out rather than written as `null`. That
  * is RFC 8785 (JCS) in spirit — the same value always prints to the same bytes — though numbers print as they were
  * read or as the codecs write them, not by JCS's ECMAScript rule; both ends of a bundle are this code, so they agree.
  *
  * ==rev==
  * `rev` is the sha256 of a record's canonical JSON without `rev`, `updatedAt` and `creator`: two records with the same
  * content have the same rev whatever their timestamps and whoever made them (the creator is provenance, not content —
  * ADR 0029), which is what lets an import say "unchanged" and the merge ledger remember what was merged.
  */
object BundleFormat:
  /** The canonical text of a JSON value. */
  def print(json: Json): String = json.canonical.noSpaces

  /** Fields a rev does NOT cover. */
  val Unrevved: Set[String] = Set("rev", "updatedAt", "creator")

  def sha256Hex(bytes: Array[Byte]): Sha256 =
    Sha256(hex(MessageDigest.getInstance("SHA-256").digest(bytes)))

  def sha256Hex(text: String): Sha256 = sha256Hex(text.getBytes(UTF_8))

  def hex(bytes: Array[Byte]): String =
    val sb = StringBuilder(bytes.length * 2)
    bytes.foreach { b =>
      val v = b & 0xff
      sb.append(Character.forDigit(v >>> 4, 16)).append(Character.forDigit(v & 0xf, 16))
    }
    sb.toString

  /** The rev of a record already in JSON form. */
  def revOf(obj: JsonObject): String =
    sha256Hex(print(Json.fromJsonObject(obj.filterKeys(k => !Unrevved(k))))).value

  /** A record as its NDJSON line (no newline) and its rev. The record's own `rev` field, whatever it holds, is replaced
    * by the computed one.
    */
  def stamp[A](a: A)(using JsonValueCodec[A]): Stamped =
    val obj = a.asJson.asObject.getOrElse(JsonObject.empty)
    val rev = revOf(obj)
    Stamped(rev, print(Json.fromJsonObject(obj.updated("rev", Json.fromString(rev)))))

  /** A line that is not a stamped record carries no rev: it is printed as it is. */
  def line[A](a: A)(using JsonValueCodec[A]): String = print(a.asJson)

  final case class Stamped(rev: String, line: String)

  /** Parse one line, found at `at` (`entities/sources.ndjson:3`), and for a stamped kind check that its `rev` is the
    * rev of its content.
    */
  def decodeLine[A](line: String, stamped: Boolean, at: Problem.Where)(using JsonValueCodec[A]): Either[Problem, A] =
    RawJson.parse(line).left.map(e => at.unreadable(s"$e")).flatMap { json =>
      val revOk =
        if !stamped then Right(())
        else
          json.asObject.toRight(at.damaged(Problem.Io.Damage.Undecodable)).flatMap { obj =>
            obj("rev").flatMap(_.asString) match
              case None => Left(Problem.Invalid.Missing(Problem.Field("rev"), Vector(Problem.Field(s"$at"))))
              case Some(r) if r == revOf(obj) => Right(())
              case Some(_)                    => Left(at.damaged(Problem.Io.Damage.Checksum))
          }
      revOk.flatMap(_ => json.as[A].left.map(e => at.unreadable(s"$e")))
    }

  extension (at: Problem.Where)
    /** The JSON at this place is damaged: `damage`. */
    private def damaged(damage: Problem.Io.Damage): Problem = Problem.Io.Corrupt(Problem.Io.Format.Json, at, damage)

    /** The JSON at this place cannot be read: `reported` is what the reader said. */
    private def unreadable(reported: String): Problem =
      at.damaged(Problem.Io.Damage.Unreadable(External.Reported.of(reported)))

  /** The bundle's manifest, where a problem with it is found. */
  private val ManifestAt = Problem.Where(s"${Bundle.ManifestPath}")

  // ── the bundle id ──────────────────────────────────────────────────────────────────────────

  /** A UUID (version 8, RFC 9562 "custom") made from the sha256 of `parts`: the same content from the same instance
    * always gets the same id, so the manifest — and with it the whole file — stays deterministic.
    */
  def contentUuid(parts: Seq[String]): UUID =
    val d = MessageDigest.getInstance("SHA-256").digest(parts.mkString("\n").getBytes(UTF_8))
    d(6) = ((d(6) & 0x0f) | 0x80).toByte // version 8
    d(8) = ((d(8) & 0x3f) | 0x80).toByte // variant 10
    val bb = java.nio.ByteBuffer.wrap(d, 0, 16)
    UUID(bb.getLong, bb.getLong)

  def bundleId(
      instance: UUID,
      since: Option[Bundle.Manifest.Since],
      files: List[Bundle.FileInfo],
      blobs: Seq[Sha256]
  ): UUID =
    contentUuid(
      Seq(Bundle.Format, instance.toString, since.map(_.value).getOrElse("")) ++
        files.map(f => s"${f.path} ${f.sha256}") ++ blobs.map(_.value)
    )

  // ── the manifest ───────────────────────────────────────────────────────────────────────────

  def manifestBytes(m: Bundle.Manifest): Array[Byte] =
    (line(m) + "\n").getBytes(UTF_8)

  def parseManifest(bytes: Array[Byte]): Either[Problem, Bundle.Manifest] =
    RawJson
      .decode[Bundle.Manifest](String(bytes, UTF_8))
      .left
      .map(e => ManifestAt.unreadable(s"$e"))

  /** Can THIS server read `m`? A newer format or a newer schema is refused (the importer only ever upgrades older
    * ones); every reason is returned. `serverSchema` is the Flyway version, numeric.
    */
  def compatible(m: Bundle.Manifest, serverSchema: Option[Bundle.Manifest.SchemaVersion]): List[Problem] =
    def num(v: Bundle.Manifest.SchemaVersion) = v.value.trim.toIntOption
    List(
      Option.when(m.format.value != Bundle.Format)(
        Problem.Invalid.Unknown(
          Problem.TypeName.named[Bundle.Manifest.Format],
          Problem.Raw(s"${m.format}"),
          Vector(Problem.Raw(s"${Bundle.Format}")),
          Vector(Problem.Field("format"))
        )
      ),
      Option.when(m.formatVersion.value > Bundle.FormatVersion)(
        Problem.Io.Corrupt(
          Problem.Io.Format.Bundle,
          ManifestAt,
          Problem.Io.Damage
            .Newer(Problem.Raw(s"${m.formatVersion}"), Problem.Raw.of(Bundle.FormatVersion.toLong))
        )
      ),
      (num(m.schemaVersion), serverSchema.flatMap(num)) match
        case (Some(b), Some(s)) if b > s =>
          Some(
            Problem.Io.Corrupt(
              Problem.Io.Format.Bundle,
              ManifestAt,
              Problem.Io.Damage.Schema(Problem.Count(b.toLong), Problem.Count(s.toLong))
            )
          )
        case (None, _) =>
          Some(
            Problem.Invalid.Unreadable(
              Problem.TypeName.named[Bundle.Manifest.SchemaVersion],
              Problem.Raw(s"${m.schemaVersion}"),
              Vector(Problem.Raw("54")),
              Vector(Problem.Field("schemaVersion"))
            )
          )
        case _ => None
    ).flatten

  /** The manifest's file list against what the tar holds: missing, unexpected and mismatching files. */
  def checkFiles(m: Bundle.Manifest, seen: Map[Bundle.FileInfo.Path, Bundle.FileInfo]): List[Problem] =
    val listed = m.files.map(f => f.path -> f).toMap
    def entry(path: Bundle.FileInfo.Path, fault: Problem.Io.Fault): Problem =
      Problem.Io.Entry(Problem.Io.Format.Bundle, Problem.Io.Path(s"$path"), fault)
    def differs(path: Bundle.FileInfo.Path, measure: Problem.Io.Measure, listed: String, found: String) =
      entry(path, Problem.Io.Fault.Differs(measure, Problem.Raw(s"$listed"), Problem.Raw(s"$found")))
    val missing = m.files
      .filterNot(f => seen.contains(f.path))
      .map(f => entry(f.path, Problem.Io.Fault.Omitted))
    val unexpected =
      seen.keys.toList.sorted.filterNot(listed.contains).map(entry(_, Problem.Io.Fault.Unlisted))
    val differ = m.files.flatMap(f =>
      seen.get(f.path).toList.flatMap { s =>
        List(
          Option.when(s.sha256 != f.sha256)(
            differs(f.path, Problem.Io.Measure.Sha256, s"${f.sha256}", s"${s.sha256}")
          ),
          Option
            .when(s.bytes != f.bytes)(differs(f.path, Problem.Io.Measure.Bytes, s"${f.bytes}", s"${s.bytes}")),
          Option.when(s.lines != f.lines)(differs(f.path, Problem.Io.Measure.Lines, s"${f.lines}", s"${s.lines}"))
        ).flatten
      }
    )
    missing ++ unexpected ++ differ

  /** The NDJSON payload of a list of lines: one per line, each ending in `\n`. */
  def ndjson(lines: Seq[String]): Array[Byte] =
    if lines.isEmpty then Array.emptyByteArray
    else lines.mkString("", "\n", "\n").getBytes(UTF_8)

  def fileInfo(path: Bundle.FileInfo.Path, payload: Array[Byte]): Bundle.FileInfo =
    Bundle.FileInfo(path, lines(payload), ByteCount(payload.length.toLong), sha256Hex(payload))

  def lines(payload: Array[Byte]): Bundle.FileInfo.Lines = Bundle.FileInfo.Lines(payload.count(_ == '\n'.toByte))

  def splitLines(payload: Array[Byte]): List[String] =
    String(payload, UTF_8).split('\n').toList.filter(_.nonEmpty)
