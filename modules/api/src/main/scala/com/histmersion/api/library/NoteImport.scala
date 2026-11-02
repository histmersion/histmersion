package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.AssetInput
import com.histmersion.api.given

final case class NoteImport(
    text: String,
    citekey: Option[String] = None,
    body: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    assets: List[AssetInput] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object NoteImport:
  given Schema[NoteImport] = summon[KindlingsSchema[NoteImport]].schema
