package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One linked item that disagrees with its record: the sources linked to it, the issues (`codes` and their `labels`),
  * the record it was judged against, the item (none when it is missing), and the citekey whose record an update would
  * write over it (`fix`, none when the item is missing).
  */
final case class CollectionAuditRow(
    id: String,
    citekeys: List[String],
    codes: List[String],
    labels: List[String],
    record: CollectionRecordView,
    entry: Option[CollectionRecordView],
    fix: Option[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionAuditRow:
  given Schema[CollectionAuditRow] = summon[KindlingsSchema[CollectionAuditRow]].schema
