package com.histmersion
package api.media
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.ArchiveFile
import com.histmersion.api.Api
import com.histmersion.lint.{tolerated, LintRule}

/** IIIF Image API Level 1, served at `/iiif/{identifier}/{region}/{size}/{rotation}/{quality}.{format}`. Level 1
  * requires:
  *   - region: `full` or pixel `x,y,w,h`
  *   - size: `max`, `^max`, `w,`, `,h`, `w,h`, `!w,h`
  *   - rotation: `0` only
  *   - quality: `default` only
  *   - format: `jpg`, `png`
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Iiif:
  type Format = Format.T
  object Format extends Newtype[String, Pure]
  type ImageId = ImageId.T
  object ImageId extends Newtype[String, Pure]
  type Quality = Quality.T
  object Quality extends Newtype[String, Pure]
  type RegionText = RegionText.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object RegionText extends Newtype[String, Pure]
  type Rotation = Rotation.T
  object Rotation extends Newtype[String, Pure]
  type SizeText = SizeText.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SizeText extends Newtype[String, Pure]

  val ApiContext = "http://iiif.io/api/image/3/context.json"
  val Protocol = "http://iiif.io/api/image"
  val Profile = "level1"

  enum Region:
    case Full
    case Pixels(x: Pixels.X, y: Pixels.Y, w: Pixels.W, h: Pixels.H)

  object Region:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    object Pixels:
      type X = X.T
      object X extends Newtype[Int, Pure]
      type Y = Y.T
      object Y extends Newtype[Int, Pure]
      type W = W.T
      object W extends Newtype[Int, Pure]
      type H = H.T
      object H extends Newtype[Int, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Either[Problem, Region] = (s).value.trim.toLowerCase match
      case "full" | "square" => Right(Full)
      case other             =>
        other.split(",").toList match
          case List(xs, ys, ws, hs) =>
            (for
              x <- xs.toIntOption
              y <- ys.toIntOption
              w <- ws.toIntOption.filter(_ > 0)
              h <- hs.toIntOption.filter(_ > 0)
            yield Pixels(Pixels.X(x), Pixels.Y(y), Pixels.W(w), Pixels.H(h)))
              .toRight(
                Invalid.Unreadable.here(TypeName.named[Region], Raw(s"$s"), Vector(Raw("full"), Raw("10,20,300,400")))
              )
          case _ =>
            Left(Invalid.Unreadable.here(TypeName.named[Region], Raw(s"$s"), Vector(Raw("full"), Raw("10,20,300,400"))))

  enum Size:
    case Max
    case ScaleUp // ^max
    case Width(w: Width.W) // w,
    case Height(h: Height.H) // ,h
    case Exact(w: Exact.W, h: Exact.H) // w,h
    case BestFit(w: BestFit.W, h: BestFit.H) // !w,h

  object Size:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    object Width:
      type W = W.T
      object W extends Newtype[Int, Pure]

    object Height:
      type H = H.T
      object H extends Newtype[Int, Pure]

    object Exact:
      type W = W.T
      object W extends Newtype[Int, Pure]
      type H = H.T
      object H extends Newtype[Int, Pure]

    object BestFit:
      type W = W.T
      object W extends Newtype[Int, Pure]
      type H = H.T
      object H extends Newtype[Int, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Either[Problem, Size] =
      val t = (s).value.trim
      t match
        case "max"                  => Right(Max)
        case "^max"                 => Right(ScaleUp)
        case _ if t.startsWith("!") =>
          t.drop(1).split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield BestFit(BestFit.W(w), BestFit.H(h)))
                .toRight(
                  Invalid.Unreadable.here(
                    TypeName.named[Size],
                    Raw(s"$s"),
                    Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
                  )
                )
            case _ =>
              Left(
                Invalid.Unreadable.here(
                  TypeName.named[Size],
                  Raw(s"$s"),
                  Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
                )
              )
        case _ if t.endsWith(",") =>
          t.dropRight(1)
            .toIntOption
            .filter(_ > 0)
            .map(w => Width(Width.W(w)))
            .toRight(
              Invalid.Unreadable.here(
                TypeName.named[Size],
                Raw(s"$s"),
                Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
              )
            )
        case _ if t.startsWith(",") =>
          t.drop(1)
            .toIntOption
            .filter(_ > 0)
            .map(h => Height(Height.H(h)))
            .toRight(
              Invalid.Unreadable.here(
                TypeName.named[Size],
                Raw(s"$s"),
                Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
              )
            )
        case _ =>
          t.split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield Exact(Exact.W(w), Exact.H(h)))
                .toRight(
                  Invalid.Unreadable.here(
                    TypeName.named[Size],
                    Raw(s"$s"),
                    Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
                  )
                )
            case _ =>
              Left(
                Invalid.Unreadable.here(
                  TypeName.named[Size],
                  Raw(s"$s"),
                  Vector(Raw("max"), Raw("300,"), Raw(",200"), Raw("300,200"), Raw("!300,200"))
                )
              )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseFormat(s: ArchiveFile.Format.Wire): Either[Problem, Format] =
    (s).value.trim.toLowerCase match
      case "jpg" | "jpeg" => Right(Format("jpg"))
      case "png"          => Right(Format("png"))
      case other => Left(Invalid.Unreadable.here(TypeName.of(Format), Raw(s"$other"), Vector(Raw("jpg"), Raw("png"))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contentType(format: Format): Api.ContentType = format.value match
    case "png" => Api.ContentType("image/png")
    case _     => Api.ContentType("image/jpeg")

  /** Built as `Json` by hand: the IIIF document's `@`-prefixed keys are beyond derivation. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def infoJson(id: ImageId, width: ImageSize.Width, height: ImageSize.Height): Json =
    Json.obj(
      "@context" -> Json.fromString(ApiContext),
      "id" -> Json.fromString((id).value),
      "type" -> Json.fromString("ImageService3"),
      "protocol" -> Json.fromString(Protocol),
      "width" -> Json.fromInt((width).value),
      "height" -> Json.fromInt((height).value),
      "profile" -> Json.fromString(Profile),
      "extraFormats" -> Json.arr(Json.fromString("png")),
      "extraQualities" -> Json.arr(),
      "extraFeatures" -> Json.arr(
        Json.fromString("regionByPx"),
        Json.fromString("sizeByW"),
        Json.fromString("sizeByH"),
        Json.fromString("sizeByWh"),
        Json.fromString("sizeByForcedWh")
      )
    )
