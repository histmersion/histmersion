package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object AssetKind:
  val Figure = "figure"
  val Photo  = "photo"
  val Map    = "map"
  val Plan   = "plan"
  val Scan   = "scan"
  val all: List[String] = List(Figure, Photo, Map, Plan, Scan)

object AssetRelation:
  val Depicts     = "depicts"
  val TakenAt     = "taken_at"
  val Illustrates = "illustrates"
  val PageOf      = "page_of"
  val all: List[String] = List(Depicts, TakenAt, Illustrates, PageOf)

object AssetLinkTarget:
  val Event   = "event"
  val Finding = "finding"
  val Place   = "place"
  val Source  = "source"
  val Article = "article"
  val all: List[String] = List(Event, Finding, Place, Source, Article)

final case class AssetPatch(
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject

object AssetPatch:
  given Schema[AssetPatch] = KindlingsSchema.derived[AssetPatch].schema

final case class AssetDetail(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject

object AssetDetail:
  given Schema[AssetDetail] = KindlingsSchema.derived[AssetDetail].schema

final case class AssetLink(
    id: Long,
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject

object AssetLink:
  given Schema[AssetLink] = KindlingsSchema.derived[AssetLink].schema

final case class AssetLinkPut(
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject

object AssetLinkPut:
  given Schema[AssetLinkPut] = KindlingsSchema.derived[AssetLinkPut].schema

final case class AssetLinkList(
    total: Int,
    links: List[AssetLink]
) derives KindlingsCodecAsObject

object AssetLinkList:
  given Schema[AssetLinkList] = KindlingsSchema.derived[AssetLinkList].schema

final case class AssetLinkDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject

object AssetLinkDeleted:
  given Schema[AssetLinkDeleted] = KindlingsSchema.derived[AssetLinkDeleted].schema
