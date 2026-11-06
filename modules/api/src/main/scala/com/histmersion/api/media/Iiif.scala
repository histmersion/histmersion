package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageId = ImageId.T
  object ImageId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quality = Quality.T
  object Quality extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionText = RegionText.T
  object RegionText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rotation = Rotation.T
  object Rotation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeText = SizeText.T
  object SizeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseFormatResultPart1 = com.histmersion.api.library.ArchiveFile.Format.Problem
  val ParseFormatResultPart1: _root_.com.histmersion.api.library.ArchiveFile.Format.Problem.type = _root_.com.histmersion.api.library.ArchiveFile.Format.Problem

  val ApiContext = "http://iiif.io/api/image/3/context.json"
  val Protocol = "http://iiif.io/api/image"
  val Profile = "level1"

  enum Region:
    case Full
    case Pixels(x: Region.Pixels.X, y: Region.Pixels.Y, w: Region.Pixels.W, h: Region.Pixels.H)

  object Region:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Pixels:
      type X = X.T
      object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Y = Y.T
      object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type W = W.T
      object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type H = H.T
      object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseResultPart1 = com.histmersion.api.media.Iiif.Region.Problem
    val ParseResultPart1: _root_.com.histmersion.api.media.Iiif.Region.Problem.type = _root_.com.histmersion.api.media.Iiif.Region.Problem

    def parse(s: com.histmersion.api.media.Iiif.Region.Wire): Either[com.histmersion.api.media.Iiif.Region.Problem, Region] = (s).value.trim.toLowerCase match
      case "full" | "square" => Right(Full)
      case other             =>
        other.split(",").toList match
          case List(xs, ys, ws, hs) =>
            (for
              x <- xs.toIntOption
              y <- ys.toIntOption
              w <- ws.toIntOption.filter(_ > 0)
              h <- hs.toIntOption.filter(_ > 0)
            yield Pixels(_root_.com.histmersion.api.media.Iiif.Region.Pixels.X(x), _root_.com.histmersion.api.media.Iiif.Region.Pixels.Y(y), _root_.com.histmersion.api.media.Iiif.Region.Pixels.W(w), _root_.com.histmersion.api.media.Iiif.Region.Pixels.H(h)))
              .toRight(_root_.com.histmersion.api.media.Iiif.Region.Problem(s"invalid region coordinates: '$s' (expected x,y,w,h with w,h > 0)"))
          case _ => Left(_root_.com.histmersion.api.media.Iiif.Region.Problem(s"invalid region: '$s' (expected 'full' or 'x,y,w,h')"))

  enum Size:
    case Max
    case ScaleUp // ^max
    case Width(w: Size.Width.W) // w,
    case Height(h: Size.Height.H) // ,h
    case Exact(w: Size.Exact.W, h: Size.Exact.H) // w,h
    case BestFit(w: Size.BestFit.W, h: Size.BestFit.H) // !w,h

  object Size:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Width:
      type W = W.T
      object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Height:
      type H = H.T
      object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Exact:
      type W = W.T
      object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type H = H.T
      object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object BestFit:
      type W = W.T
      object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type H = H.T
      object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseResultPart1 = com.histmersion.api.media.Iiif.Size.Problem
    val ParseResultPart1: _root_.com.histmersion.api.media.Iiif.Size.Problem.type = _root_.com.histmersion.api.media.Iiif.Size.Problem

    def parse(s: com.histmersion.api.media.Iiif.Size.Wire): Either[com.histmersion.api.media.Iiif.Size.Problem, Size] =
      val t = (s).value.trim
      t match
        case "max"                  => Right(Max)
        case "^max"                 => Right(ScaleUp)
        case _ if t.startsWith("!") =>
          t.drop(1).split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield BestFit(_root_.com.histmersion.api.media.Iiif.Size.BestFit.W(w), _root_.com.histmersion.api.media.Iiif.Size.BestFit.H(h)))
                .toRight(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid best-fit size: '$s'"))
            case _ => Left(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid size: '$s'"))
        case _ if t.endsWith(",") =>
          t.dropRight(1)
            .toIntOption
            .filter(_ > 0)
            .map(Width(_root_.com.histmersion.api.media.Iiif.Size.Width.W(_)))
            .toRight(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid width size: '$s'"))
        case _ if t.startsWith(",") =>
          t.drop(1)
            .toIntOption
            .filter(_ > 0)
            .map(Height(_root_.com.histmersion.api.media.Iiif.Size.Height.H(_)))
            .toRight(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid height size: '$s'"))
        case _ =>
          t.split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield Exact(_root_.com.histmersion.api.media.Iiif.Size.Exact.W(w), _root_.com.histmersion.api.media.Iiif.Size.Exact.H(h)))
                .toRight(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid exact size: '$s'"))
            case _ => Left(_root_.com.histmersion.api.media.Iiif.Size.Problem(s"invalid size: '$s'"))

  def parseFormat(s: com.histmersion.api.library.ArchiveFile.Format.Wire): Either[com.histmersion.api.library.ArchiveFile.Format.Problem, com.histmersion.api.media.Iiif.Format] = (s).value.trim.toLowerCase match
    case "jpg" | "jpeg" => Right(_root_.com.histmersion.api.media.Iiif.Format("jpg"))
    case "png"          => Right(_root_.com.histmersion.api.media.Iiif.Format("png"))
    case other          => Left(_root_.com.histmersion.api.library.ArchiveFile.Format.Problem(s"unsupported format: '$other' (use jpg or png)"))

  def contentType(format: com.histmersion.api.media.Iiif.Format): com.histmersion.api.catalogue.TilesApi.TileAnswer.ContentType.Wire = format match
    case refactorPattern2 if (refactorPattern.value == "png") && (refactorPattern2.value == "png") => _root_.com.histmersion.api.catalogue.TilesApi.TileAnswer.ContentType.Wire("image/png")
    case _     => _root_.com.histmersion.api.catalogue.TilesApi.TileAnswer.ContentType.Wire("image/jpeg")

  /** Built as `Json` by hand: the IIIF document's `@`-prefixed keys are beyond circe derivation. */
  def infoJson(id: com.histmersion.api.media.Iiif.ImageId, width: com.histmersion.api.media.ImageSize.Width, height: com.histmersion.api.media.ImageSize.Height): io.circe.Json =
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
