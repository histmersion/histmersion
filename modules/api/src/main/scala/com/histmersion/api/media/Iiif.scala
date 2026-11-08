package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.ArchiveFile
import com.histmersion.api.Api

/** IIIF Image API Level 1, served at `/iiif/{identifier}/{region}/{size}/{rotation}/{quality}.{format}`. Level 1
  * requires:
  *   - region: `full` or pixel `x,y,w,h`
  *   - size: `max`, `^max`, `w,`, `,h`, `w,h`, `!w,h`
  *   - rotation: `0` only
  *   - quality: `default` only
  *   - format: `jpg`, `png`
  */
object Iiif:
  type Format = Format.T
  object Format extends Newtype[String, Pure]
  type ImageId = ImageId.T
  object ImageId extends Newtype[String, Pure]
  type Quality = Quality.T
  object Quality extends Newtype[String, Pure]
  type RegionText = RegionText.T
  object RegionText extends Newtype[String, Pure]
  type Rotation = Rotation.T
  object Rotation extends Newtype[String, Pure]
  type SizeText = SizeText.T
  object SizeText extends Newtype[String, Pure]

  val ApiContext = "http://iiif.io/api/image/3/context.json"
  val Protocol = "http://iiif.io/api/image"
  val Profile = "level1"

  enum Region derives CanEqual:
    case Full
    case Pixels(x: Pixels.X, y: Pixels.Y, w: Pixels.W, h: Pixels.H)

  object Region:
    type Problem = Problem.T
    object Problem extends Newtype[String, Pure]
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
              .toRight(Problem(s"invalid region coordinates: '$s' (expected x,y,w,h with w,h > 0)"))
          case _ => Left(Problem(s"invalid region: '$s' (expected 'full' or 'x,y,w,h')"))

  enum Size derives CanEqual:
    case Max
    case ScaleUp // ^max
    case Width(w: Width.W) // w,
    case Height(h: Height.H) // ,h
    case Exact(w: Exact.W, h: Exact.H) // w,h
    case BestFit(w: BestFit.W, h: BestFit.H) // !w,h

  object Size:
    type Problem = Problem.T
    object Problem extends Newtype[String, Pure]
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
                .toRight(Problem(s"invalid best-fit size: '$s'"))
            case _ => Left(Problem(s"invalid size: '$s'"))
        case _ if t.endsWith(",") =>
          t.dropRight(1)
            .toIntOption
            .filter(_ > 0)
            .map(w => Width(Width.W(w)))
            .toRight(Problem(s"invalid width size: '$s'"))
        case _ if t.startsWith(",") =>
          t.drop(1)
            .toIntOption
            .filter(_ > 0)
            .map(h => Height(Height.H(h)))
            .toRight(Problem(s"invalid height size: '$s'"))
        case _ =>
          t.split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield Exact(Exact.W(w), Exact.H(h)))
                .toRight(Problem(s"invalid exact size: '$s'"))
            case _ => Left(Problem(s"invalid size: '$s'"))

  def parseFormat(s: ArchiveFile.Format.Wire): Either[ArchiveFile.Format.Problem, Format] =
    (s).value.trim.toLowerCase match
      case "jpg" | "jpeg" => Right(Format("jpg"))
      case "png"          => Right(Format("png"))
      case other          => Left(ArchiveFile.Format.Problem(s"unsupported format: '$other' (use jpg or png)"))

  def contentType(format: Format): Api.ContentType = format.value match
    case "png" => Api.ContentType("image/png")
    case _     => Api.ContentType("image/jpeg")

  /** Built as `Json` by hand: the IIIF document's `@`-prefixed keys are beyond circe derivation. */
  def infoJson(id: ImageId, width: ImageSize.Width, height: ImageSize.Height): io.circe.Json =
    import io.circe.Json
    import io.circe.syntax.*
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
