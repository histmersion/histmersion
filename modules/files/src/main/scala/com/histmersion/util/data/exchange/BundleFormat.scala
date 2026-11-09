package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.given

import io.circe.{Decoder, Encoder, Json, JsonObject, Printer}
import io.circe.parser

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
  * is RFC 8785 (JCS) in spirit — the same value always prints to the same bytes — though numbers print as circe prints
  * them, not by JCS's ECMAScript rule; both ends of a bundle are this code, so they agree.
  *
  * ==rev==
  * `rev` is the sha256 of a record's canonical JSON without `rev` and `updatedAt`: two records with the same content
  * have the same rev whatever their timestamps, which is what lets an import say "unchanged" and the merge ledger
  * remember what was merged.
  */
object BundleFormat:
  val printer: Printer = Printer.noSpaces.copy(sortKeys = true, dropNullValues = true)

  /** Fields a rev does NOT cover. */
  val Unrevved: Set[String] = Set("rev", "updatedAt")

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
    sha256Hex(printer.print(Json.fromJsonObject(obj.filterKeys(k => !Unrevved(k))))).value

  /** A record as its NDJSON line (no newline) and its rev. The record's own `rev` field, whatever it holds, is replaced
    * by the computed one.
    */
  def stamp[A](a: A)(using enc: Encoder.AsObject[A]): Stamped =
    val obj = enc.encodeObject(a)
    val rev = revOf(obj)
    Stamped(rev, printer.print(Json.fromJsonObject(obj.add("rev", Json.fromString(rev)))))

  /** A line that is not a stamped record carries no rev: it is printed as it is. */
  def line[A](a: A)(using enc: Encoder.AsObject[A]): String = printer.print(enc(a))

  final case class Stamped(rev: String, line: String)

  /** Parse one line, and for a stamped kind check that its `rev` is the rev of its content. */
  def decodeLine[A](line: String, stamped: Boolean)(using dec: Decoder[A]): Either[Problem, A] =
    parser.parse(line).left.map(e => Problem.Unclassified(Legacy.Text(s"not JSON: ${e.message}"))).flatMap { json =>
      val revOk =
        if !stamped then Right(())
        else
          json.asObject.toRight(Problem.Unclassified(Legacy.Text("not a JSON object"))).flatMap { obj =>
            obj("rev").flatMap(_.asString) match
              case None                       => Left(Problem.Unclassified(Legacy.Text("no rev")))
              case Some(r) if r == revOf(obj) => Right(())
              case Some(r)                    =>
                Left(Problem.Unclassified(Legacy.Text(s"rev $r does not match its content (${revOf(obj)})")))
          }
      revOk.flatMap(_ =>
        dec
          .decodeJson(json)
          .left
          .map(e => Problem.Unclassified(Legacy.Text(s"${e.message} at ${e.pathToRootString.getOrElse("")}")))
      )
    }

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
    (printer.print(Encoder.AsObject[Bundle.Manifest].apply(m)) + "\n").getBytes(UTF_8)

  def parseManifest(bytes: Array[Byte]): Either[Problem, Bundle.Manifest] =
    parser
      .decode[Bundle.Manifest](String(bytes, UTF_8))
      .left
      .map(e => Problem.Unclassified(Legacy.Text(s"manifest.json: ${e.getMessage}")))

  /** Can THIS server read `m`? A newer format or a newer schema is refused (the importer only ever upgrades older
    * ones); every reason is returned. `serverSchema` is the Flyway version, numeric.
    */
  def compatible(m: Bundle.Manifest, serverSchema: Option[Bundle.Manifest.SchemaVersion]): List[String] =
    def num(v: Bundle.Manifest.SchemaVersion) = v.value.trim.toIntOption
    List(
      Option.when(m.format.value != Bundle.Format)(s"not a Histmersion bundle (format '${m.format}')"),
      Option.when(m.formatVersion.value > Bundle.FormatVersion)(
        s"bundle format ${m.formatVersion} is newer than this server reads (${Bundle.FormatVersion}) — upgrade Histmersion"
      ),
      (num(m.schemaVersion), serverSchema.flatMap(num)) match
        case (Some(b), Some(s)) if b > s =>
          Some(s"bundle schema V$b is newer than this server's V$s — upgrade Histmersion")
        case (None, _) => Some(s"bundle schema version '${m.schemaVersion}' is not a number")
        case _         => None
    ).flatten

  /** The manifest's file list against what the tar holds: missing, unexpected and mismatching files. */
  def checkFiles(m: Bundle.Manifest, seen: Map[Bundle.FileInfo.Path, Bundle.FileInfo]): List[String] =
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
  def ndjson(lines: Seq[String]): Array[Byte] =
    if lines.isEmpty then Array.emptyByteArray
    else lines.mkString("", "\n", "\n").getBytes(UTF_8)

  def fileInfo(path: Bundle.FileInfo.Path, payload: Array[Byte]): Bundle.FileInfo =
    Bundle.FileInfo(path, lines(payload), ByteCount(payload.length.toLong), sha256Hex(payload))

  def lines(payload: Array[Byte]): Bundle.FileInfo.Lines = Bundle.FileInfo.Lines(payload.count(_ == '\n'.toByte))

  def splitLines(payload: Array[Byte]): List[String] =
    String(payload, UTF_8).split('\n').toList.filter(_.nonEmpty)
