package com.histmersion.domain.media.assets

import com.histmersion.api.{AssetInput, AssetLinks, ImageSize}

import scala.annotation.tailrec

/** One figure of a source as the caller supplies it — decoded bytes, not base64 — to be stored with the source's
  * Markdown (plan §1.8): keyed by `(citekey, name)`, which is exactly the `assets/<name>` link the body carries.
  */
final case class NewFigure(name: String, mime: String, bytes: Array[Byte], width: Option[Int], height: Option[Int]):

  /** The digest the store deduplicates a source's figures by. */
  def sha256: String = NewFigure.sha256(bytes)

object NewFigure:

  /** Decode a wire [[AssetInput]]; `Left` with a readable reason when the payload is unusable. */
  def of(in: AssetInput, maxBytes: Long): Either[String, NewFigure] =
    val name = AssetLinks.safeName(in.name)
    scala.util
      .Try(java.util.Base64.getDecoder.decode(in.base64.trim))
      .toEither
      .left
      .map(e => s"asset '$name': not valid base64 (${e.getMessage})")
      .flatMap { raw =>
        if raw.isEmpty then Left(s"asset '$name': empty")
        else if raw.length > maxBytes then
          Left(s"asset '$name': ${raw.length} bytes exceeds the $maxBytes-byte limit (limits.asset-bytes)")
        else
          // a client may omit the dimensions (the seed path does); read them off the header
          val size = ImageSize.of(raw)
          Right(NewFigure(name, in.mime, raw, in.width.orElse(size.map(_._1)), in.height.orElse(size.map(_._2))))
      }

  def sha256(bytes: Array[Byte]): String =
    java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(bytes)
      .map(b => f"${b & 0xff}%02x")
      .mkString

  /** First writer of a given sha256 wins; later duplicates are dropped (not stored twice), as are later uses of a name. */
  def dedupe(figures: List[NewFigure]): List[NewFigure] =
    @tailrec def loop(rest: List[NewFigure], shas: Set[String], names: Set[String], acc: List[NewFigure]): List[NewFigure] =
      rest match
        case Nil       => acc.reverse
        case a :: tail =>
          val sha = a.sha256
          if shas.contains(sha) || names.contains(a.name) then loop(tail, shas, names, acc)
          else loop(tail, shas + sha, names + a.name, a :: acc)
    loop(figures, Set.empty, Set.empty, Nil)
