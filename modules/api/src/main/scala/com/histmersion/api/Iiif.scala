package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** IIIF Image API Level 1, served at `/iiif/{identifier}/{region}/{size}/{rotation}/{quality}.{format}`. Level 1
  * requires:
  *   - region: `full` or pixel `x,y,w,h`
  *   - size: `max`, `^max`, `w,`, `,h`, `w,h`, `!w,h`
  *   - rotation: `0` only
  *   - quality: `default` only
  *   - format: `jpg`, `png`
  */
object Iiif:

  val ApiContext = "http://iiif.io/api/image/3/context.json"
  val Protocol = "http://iiif.io/api/image"
  val Profile = "level1"

  enum Region:
    case Full
    case Pixels(x: Int, y: Int, w: Int, h: Int)

  object Region:
    def parse(s: String): Either[String, Region] = s.trim.toLowerCase match
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
              .toRight(s"invalid region coordinates: '$s' (expected x,y,w,h with w,h > 0)")
          case _ => Left(s"invalid region: '$s' (expected 'full' or 'x,y,w,h')")

  enum Size:
    case Max
    case ScaleUp // ^max
    case Width(w: Int) // w,
    case Height(h: Int) // ,h
    case Exact(w: Int, h: Int) // w,h
    case BestFit(w: Int, h: Int) // !w,h

  object Size:
    def parse(s: String): Either[String, Size] =
      val t = s.trim
      t match
        case "max"                  => Right(Max)
        case "^max"                 => Right(ScaleUp)
        case _ if t.startsWith("!") =>
          t.drop(1).split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield BestFit(w, h))
                .toRight(s"invalid best-fit size: '$s'")
            case _ => Left(s"invalid size: '$s'")
        case _ if t.endsWith(",") =>
          t.dropRight(1)
            .toIntOption
            .filter(_ > 0)
            .map(Width(_))
            .toRight(s"invalid width size: '$s'")
        case _ if t.startsWith(",") =>
          t.drop(1)
            .toIntOption
            .filter(_ > 0)
            .map(Height(_))
            .toRight(s"invalid height size: '$s'")
        case _ =>
          t.split(",").toList match
            case List(ws, hs) =>
              (for w <- ws.toIntOption.filter(_ > 0); h <- hs.toIntOption.filter(_ > 0)
              yield Exact(w, h))
                .toRight(s"invalid exact size: '$s'")
            case _ => Left(s"invalid size: '$s'")

  def parseFormat(s: String): Either[String, String] = s.trim.toLowerCase match
    case "jpg" | "jpeg" => Right("jpg")
    case "png"          => Right("png")
    case other          => Left(s"unsupported format: '$other' (use jpg or png)")

  def contentType(format: String): String = format match
    case "png" => "image/png"
    case _     => "image/jpeg"

  /** Built as `Json` by hand: the IIIF document's `@`-prefixed keys are beyond circe derivation. */
  def infoJson(id: String, width: Int, height: Int): io.circe.Json =
    import io.circe.Json
    import io.circe.syntax.*
    Json.obj(
      "@context" -> Json.fromString(ApiContext),
      "id" -> Json.fromString(id),
      "type" -> Json.fromString("ImageService3"),
      "protocol" -> Json.fromString(Protocol),
      "width" -> Json.fromInt(width),
      "height" -> Json.fromInt(height),
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
