package com.histmersion
package domain.media.assets

import com.histmersion.api.media.{AssetInput, AssetLinks, ImageSize}

import scala.annotation.tailrec
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One figure of a source as the caller supplies it — decoded bytes, not base64 — to be stored with the source's
  * Markdown (plan §1.8): keyed by `(citekey, name)`, which is exactly the `assets/<name>` link the body carries.
  */
final case class NewFigure(name: NewFigure.Name, mime: NewFigure.Mime, bytes: NewFigure.Bytes, width: Option[NewFigure.Width], height: Option[NewFigure.Height]):

  /** The digest the store deduplicates a source's figures by. */
  def sha256: Sha256 = NewFigure.sha256(bytes)

object NewFigure:

  /** Why a payload is unusable. */
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  type Bytes = Bytes.T
  object Bytes extends RefinedType[Array[Byte], Pure]

  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Mime = Mime.T
  object Mime extends RefinedType[String, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]

  /** Decode a wire [[AssetInput]]; `Left` with a readable reason when the payload is unusable. */
  def of(in: AssetInput, maxBytes: ByteCount): Either[Problem, NewFigure] =
    val name = AssetLinks.safeName(in.name.value)
    scala.util
      .Try(java.util.Base64.getDecoder.decode(in.base64.value.trim))
      .toEither
      .left
      .map(e => Problem(s"asset '$name': not valid base64 (${e.getMessage})"))
      .flatMap { raw =>
        if raw.isEmpty then Left(Problem(s"asset '$name': empty"))
        else if raw.length > maxBytes.value then
          Left(Problem(s"asset '$name': ${raw.length} bytes exceeds the $maxBytes-byte limit (limits.asset-bytes)"))
        else
          // a client may omit the dimensions (the seed path does); read them off the header
          val size = ImageSize.of(ImageSize.Bytes(raw))
          Right(
            NewFigure(
              Name(name.value),
              Mime(in.mime.value),
              Bytes(raw),
              in.width.orElse(size.map(_._1)).map(w => Width(w.value)),
              in.height.orElse(size.map(_._2)).map(h => Height(h.value))
            )
          )
      }

  def sha256(bytes: Bytes): Sha256 =
    Sha256.assume(
      java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(bytes.value)
        .map(b => f"${b & 0xff}%02x")
        .mkString
    )

  /** First writer of a given sha256 wins; later duplicates are dropped (not stored twice), as are later uses of a name.
    */
  def dedupe(figures: List[NewFigure]): List[NewFigure] =
    @tailrec def loop(
        rest: List[NewFigure],
        shas: Set[Sha256],
        names: Set[Name],
        acc: List[NewFigure]
    ): List[NewFigure] =
      rest match
        case Nil       => acc.reverse
        case a :: tail =>
          val sha = a.sha256
          if shas.contains(sha) || names.contains(a.name) then loop(tail, shas, names, acc)
          else loop(tail, shas + sha, names + a.name, a :: acc)
    loop(figures, Set.empty, Set.empty, Nil)
