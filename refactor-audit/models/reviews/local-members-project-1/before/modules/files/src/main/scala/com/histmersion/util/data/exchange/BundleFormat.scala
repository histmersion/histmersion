package com.histmersion.util.data.exchange

import com.histmersion.api.given

import io.circe.{Decoder, Encoder, Json, JsonObject, Printer}
import io.circe.parser

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** The pure half of the `.hmx` bundle (docs/research-merge-import.md §4): canonical JSON, record revisions, the
  * deterministic bundle id, and the checks a manifest must pass. The tar stream is [[BundleTar]]; reading the database
  * is `db.BundleExport`/`db.BundleImport`.
  *
  * ==Canonical JSON==
  * One line per record: keys sorted, no whitespace, absent (`None`) fields left out rather than written as `null`. That
  * is RFC 8785 (JCS) in spirit — the same value always prints to the same bytes — though numbers print as circe prints
  * them, not by JCS's ECMAScript rule; both ends of a bundle are this code, so they agree.
  *
  * ==rev==
  * `rev` is the sha256 of a record's canonical JSON without `rev` and `updatedAt`: two records with the same content
  * have the same rev whatever their timestamps, which is what lets an import say "unchanged" and the merge ledger
  * remember what was merged.
  */
object BundleFormat:

  type Sha256HexBytes = Sha256HexBytes.T
  object Sha256HexBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexBytes = HexBytes.T
  object HexBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManifestBytesResult = ManifestBytesResult.T
  object ManifestBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseManifestBytes = ParseManifestBytes.T
  object ParseManifestBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NdjsonResult = NdjsonResult.T
  object NdjsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileInfoPayload = FileInfoPayload.T
  object FileInfoPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinesPayload = LinesPayload.T
  object LinesPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitLinesPayload = SplitLinesPayload.T
  object SplitLinesPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Unrevved = Unrevved.T
  object Unrevved extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256HexResult = Sha256HexResult.T
  object Sha256HexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256HexText = Sha256HexText.T
  object Sha256HexText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexResult = HexResult.T
  object HexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RevOfResult = RevOfResult.T
  object RevOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeLineResultPart1 = DecodeLineResultPart1.T
  object DecodeLineResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeLineLine = DecodeLineLine.T
  object DecodeLineLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeLineStamped = DecodeLineStamped.T
  object DecodeLineStamped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentUuidParts = ContentUuidParts.T
  object ContentUuidParts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundleIdSince = BundleIdSince.T
  object BundleIdSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundleIdBlobs = BundleIdBlobs.T
  object BundleIdBlobs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseManifestResultPart1 = ParseManifestResultPart1.T
  object ParseManifestResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompatibleResult = CompatibleResult.T
  object CompatibleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompatibleServerSchema = CompatibleServerSchema.T
  object CompatibleServerSchema extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckFilesResult = CheckFilesResult.T
  object CheckFilesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckFilesSeenPart1 = CheckFilesSeenPart1.T
  object CheckFilesSeenPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NdjsonLines = NdjsonLines.T
  object NdjsonLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileInfoPath = FileInfoPath.T
  object FileInfoPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinesResult = LinesResult.T
  object LinesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitLinesResult = SplitLinesResult.T
  object SplitLinesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val printer: Printer = Printer.noSpaces.copy(sortKeys = true, dropNullValues = true)

  /** Fields a rev does NOT cover. */
  val Unrevved: Set[com.histmersion.util.data.exchange.BundleFormat.Unrevved] = Set("rev", "updatedAt")

  def sha256Hex(bytes: com.histmersion.util.data.exchange.BundleFormat.Sha256HexBytes): com.histmersion.util.data.exchange.BundleFormat.Sha256HexResult =
    hex(MessageDigest.getInstance("SHA-256").digest(bytes))

  def sha256Hex(text: com.histmersion.util.data.exchange.BundleFormat.Sha256HexText): com.histmersion.util.data.exchange.BundleFormat.Sha256HexResult = sha256Hex(text.getBytes(UTF_8))

  def hex(bytes: com.histmersion.util.data.exchange.BundleFormat.HexBytes): com.histmersion.util.data.exchange.BundleFormat.HexResult =
    val sb = StringBuilder(bytes.length * 2)
    bytes.foreach { b =>
      val v = b & 0xff
      sb.append(Character.forDigit(v >>> 4, 16)).append(Character.forDigit(v & 0xf, 16))
    }
    sb.toString

  /** The rev of a record already in JSON form. */
  def revOf(obj: JsonObject): com.histmersion.util.data.exchange.BundleFormat.RevOfResult =
    sha256Hex(printer.print(Json.fromJsonObject(obj.filterKeys(k => !Unrevved(k)))))

  /** A record as its NDJSON line (no newline) and its rev. The record's own `rev` field, whatever it holds, is replaced
    * by the computed one.
    */
  def stamp[A](a: A)(using enc: Encoder.AsObject[A]): Stamped =
    val obj = enc.encodeObject(a)
    val rev = revOf(obj)
    Stamped(rev, printer.print(Json.fromJsonObject(obj.add("rev", Json.fromString(rev)))))

  /** A line that is not a stamped record carries no rev: it is printed as it is. */
  def line[A](a: A)(using enc: Encoder.AsObject[A]): com.histmersion.util.data.exchange.BundleFormat.LineResult = printer.print(enc(a))

  final case class Stamped(rev: Stamped.Rev, line: Stamped.Line)

  object Stamped:
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Parse one line, and for a stamped kind check that its `rev` is the rev of its content. */
  def decodeLine[A](line: com.histmersion.util.data.exchange.BundleFormat.DecodeLineLine, stamped: com.histmersion.util.data.exchange.BundleFormat.DecodeLineStamped)(using dec: Decoder[A]): Either[com.histmersion.util.data.exchange.BundleFormat.DecodeLineResultPart1, A] =
    parser.parse(line).left.map(e => s"not JSON: ${e.message}").flatMap { json =>
      val revOk =
        if !stamped then Right(())
        else
          json.asObject.toRight("not a JSON object").flatMap { obj =>
            obj("rev").flatMap(_.asString) match
              case None                       => Left("no rev")
              case Some(r) if r == revOf(obj) => Right(())
              case Some(r)                    => Left(s"rev $r does not match its content (${revOf(obj)})")
          }
      revOk.flatMap(_ => dec.decodeJson(json).left.map(e => s"${e.message} at ${e.pathToRootString.getOrElse("")}"))
    }

  // ── the bundle id ──────────────────────────────────────────────────────────────────────────

  /** A UUID (version 8, RFC 9562 "custom") made from the sha256 of `parts`: the same content from the same instance
    * always gets the same id, so the manifest — and with it the whole file — stays deterministic.
    */
  def contentUuid(parts: Seq[com.histmersion.util.data.exchange.BundleFormat.ContentUuidParts]): UUID =
    val d = MessageDigest.getInstance("SHA-256").digest(parts.mkString("\n").getBytes(UTF_8))
    d(6) = ((d(6) & 0x0f) | 0x80).toByte // version 8
    d(8) = ((d(8) & 0x3f) | 0x80).toByte // variant 10
    val bb = java.nio.ByteBuffer.wrap(d, 0, 16)
    UUID(bb.getLong, bb.getLong)

  def bundleId(instance: UUID, since: Option[com.histmersion.util.data.exchange.BundleFormat.BundleIdSince], files: List[Bundle.FileInfo], blobs: Seq[com.histmersion.util.data.exchange.BundleFormat.BundleIdBlobs]): UUID =
    contentUuid(
      Seq(Bundle.Format, instance.toString, since.getOrElse("")) ++
        files.map(f => s"${f.path} ${f.sha256}") ++ blobs
    )

  // ── the manifest ───────────────────────────────────────────────────────────────────────────

  def manifestBytes(m: Bundle.Manifest): com.histmersion.util.data.exchange.BundleFormat.ManifestBytesResult =
    (printer.print(Encoder.AsObject[Bundle.Manifest].apply(m)) + "\n").getBytes(UTF_8)

  def parseManifest(bytes: com.histmersion.util.data.exchange.BundleFormat.ParseManifestBytes): Either[com.histmersion.util.data.exchange.BundleFormat.ParseManifestResultPart1, Bundle.Manifest] =
    parser.decode[Bundle.Manifest](String(bytes, UTF_8)).left.map(e => s"manifest.json: ${e.getMessage}")

  /** Can THIS server read `m`? A newer format or a newer schema is refused (the importer only ever upgrades older
    * ones); every reason is returned. `serverSchema` is the Flyway version, numeric.
    */
  def compatible(m: Bundle.Manifest, serverSchema: Option[com.histmersion.util.data.exchange.BundleFormat.CompatibleServerSchema]): List[com.histmersion.util.data.exchange.BundleFormat.CompatibleResult] =
    def num(v: String) = v.trim.toIntOption
    List(
      Option.when(m.format != Bundle.Format)(s"not a Histmersion bundle (format '${m.format}')"),
      Option.when(m.formatVersion > Bundle.FormatVersion)(
        s"bundle format ${m.formatVersion} is newer than this server reads (${Bundle.FormatVersion}) — upgrade Histmersion"
      ),
      (num(m.schemaVersion), serverSchema.flatMap(num)) match
        case (Some(b), Some(s)) if b > s =>
          Some(s"bundle schema V$b is newer than this server's V$s — upgrade Histmersion")
        case (None, _) => Some(s"bundle schema version '${m.schemaVersion}' is not a number")
        case _         => None
    ).flatten

  /** The manifest's file list against what the tar holds: missing, unexpected and mismatching files. */
  def checkFiles(m: Bundle.Manifest, seen: Map[com.histmersion.util.data.exchange.BundleFormat.CheckFilesSeenPart1, Bundle.FileInfo]): List[com.histmersion.util.data.exchange.BundleFormat.CheckFilesResult] =
    val listed = m.files.map(f => f.path -> f).toMap
    val missing = m.files
      .filterNot(f => seen.contains(f.path))
      .map(f => s"${f.path}: listed in the manifest, missing from the bundle")
    val unexpected =
      seen.keys.toList.sorted.filterNot(listed.contains).map(p => s"$p: in the bundle, not in the manifest")
    val differ = m.files.flatMap(f =>
      seen.get(f.path).toList.flatMap { s =>
        List(
          Option.when(s.sha256 != f.sha256)(s"${f.path}: sha256 ${s.sha256} ≠ manifest ${f.sha256}"),
          Option.when(s.bytes != f.bytes)(s"${f.path}: ${s.bytes} bytes ≠ manifest ${f.bytes}"),
          Option.when(s.lines != f.lines)(s"${f.path}: ${s.lines} lines ≠ manifest ${f.lines}")
        ).flatten
      }
    )
    missing ++ unexpected ++ differ

  /** The NDJSON payload of a list of lines: one per line, each ending in `\n`. */
  def ndjson(lines: Seq[com.histmersion.util.data.exchange.BundleFormat.NdjsonLines]): com.histmersion.util.data.exchange.BundleFormat.NdjsonResult =
    if lines.isEmpty then Array.emptyByteArray
    else lines.mkString("", "\n", "\n").getBytes(UTF_8)

  def fileInfo(path: com.histmersion.util.data.exchange.BundleFormat.FileInfoPath, payload: com.histmersion.util.data.exchange.BundleFormat.FileInfoPayload): Bundle.FileInfo =
    Bundle.FileInfo(path, lines(payload), payload.length.toLong, sha256Hex(payload))

  def lines(payload: com.histmersion.util.data.exchange.BundleFormat.LinesPayload): com.histmersion.util.data.exchange.BundleFormat.LinesResult = payload.count(_ == '\n'.toByte)

  def splitLines(payload: com.histmersion.util.data.exchange.BundleFormat.SplitLinesPayload): List[com.histmersion.util.data.exchange.BundleFormat.SplitLinesResult] =
    String(payload, UTF_8).split('\n').toList.filter(_.nonEmpty)
