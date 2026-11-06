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

  type ParseFormatResultPart1 = ParseFormatResultPart1.T
  object ParseFormatResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFormatResultPart2 = ParseFormatResultPart2.T
  object ParseFormatResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFormatS = ParseFormatS.T
  object ParseFormatS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentTypeResult = ContentTypeResult.T
  object ContentTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentTypeFormat = ContentTypeFormat.T
  object ContentTypeFormat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoJsonId = InfoJsonId.T
  object InfoJsonId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoJsonWidth = InfoJsonWidth.T
  object InfoJsonWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoJsonHeight = InfoJsonHeight.T
  object InfoJsonHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ApiContext = "http://iiif.io/api/image/3/context.json"
  val Protocol = "http://iiif.io/api/image"
  val Profile = "level1"

  enum Region:
    case Full
    case Pixels(x: Region.Pixels.X, y: Region.Pixels.Y, w: Region.Pixels.W, h: Region.Pixels.H)

  object Region:
    object Pixels:
      type X = X.T
      object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Y = Y.T
      object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type W = W.T
      object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type H = H.T
      object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.media.Iiif.Region.ParseS): Either[com.histmersion.api.media.Iiif.Region.ParseResultPart1, Region] = (s).value.trim.toLowerCase match
      case "full" | "square" => Right(Full)
      case other             =>
        other.split(",").toList match
          case List(xs, ys, ws, hs) =>
            (for
              x <- xs.toIntOption
              y <- ys.toIntOption
              w <- ws.toIntOption.filter(_ > 0)
              h <- hs.toIntOption.filter(_ > 0)
            yield Pixels(x, y, w, h))
              .toRight(_root_.com.histmersion.api.media.Iiif.Region.ParseResultPart1(s"invalid region coordinates: '$s' (expected x,y,w,h with w,h > 0)"))
          case _ => Left(_root_.com.histmersion.api.media.Iiif.Region.ParseResultPart1(s"invalid region: '$s' (expected 'full' or 'x,y,w,h')"))

  enum Size:
    case Max
    case ScaleUp // ^max
    case Width(w: Size.Width.W) // w,
    case Height(h: Size.Height.H) // ,h
    case Exact(w: Size.Exact.W, h: Size.Exact.H) // w,h
    case BestFit(w: Size.BestFit.W, h: Size.BestFit.H) // !w,h

  object Size:
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

    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.media.Iiif.Size.ParseS): Either[com.histmersion.api.media.Iiif.Size.ParseResultPart1, Size] =
      val t = (s).value.trim
      t match
        case "max"                  => Right(Max)
        case "^max"                 => Right(ScaleUp)
        case _ if t.startsWith("!") =>
          t.drop(1).split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield BestFit(w, h))
                .toRight(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid best-fit size: '$s'"))
            case _ => Left(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid size: '$s'"))
        case _ if t.endsWith(",") =>
          t.dropRight(1)
            .toIntOption
            .filter(_ > 0)
            .map(Width(_))
            .toRight(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid width size: '$s'"))
        case _ if t.startsWith(",") =>
          t.drop(1)
            .toIntOption
            .filter(_ > 0)
            .map(Height(_))
            .toRight(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid height size: '$s'"))
        case _ =>
          t.split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield Exact(w, h))
                .toRight(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid exact size: '$s'"))
            case _ => Left(_root_.com.histmersion.api.media.Iiif.Size.ParseResultPart1(s"invalid size: '$s'"))

  def parseFormat(s: com.histmersion.api.media.Iiif.ParseFormatS): Either[com.histmersion.api.media.Iiif.ParseFormatResultPart1, com.histmersion.api.media.Iiif.ParseFormatResultPart2] = (s).value.trim.toLowerCase match
    case "jpg" | "jpeg" => Right(_root_.com.histmersion.api.media.Iiif.ParseFormatResultPart2("jpg"))
    case "png"          => Right(_root_.com.histmersion.api.media.Iiif.ParseFormatResultPart2("png"))
    case other          => Left(_root_.com.histmersion.api.media.Iiif.ParseFormatResultPart1(s"unsupported format: '$other' (use jpg or png)"))

  def contentType(format: com.histmersion.api.media.Iiif.ContentTypeFormat): com.histmersion.api.media.Iiif.ContentTypeResult = format match
    case refactorPattern2 if (refactorPattern.value == "png") && (refactorPattern2.value == "png") => _root_.com.histmersion.api.media.Iiif.ContentTypeResult("image/png")
    case _     => _root_.com.histmersion.api.media.Iiif.ContentTypeResult("image/jpeg")

  /** Built as `Json` by hand: the IIIF document's `@`-prefixed keys are beyond circe derivation. */
  def infoJson(id: com.histmersion.api.media.Iiif.InfoJsonId, width: com.histmersion.api.media.Iiif.InfoJsonWidth, height: com.histmersion.api.media.Iiif.InfoJsonHeight): io.circe.Json =
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
