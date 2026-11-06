package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** An audit of every linked item: those that disagree with their records, and how many items were checked. */
final case class CollectionAudit(
    findings: List[com.histmersion.api.library.CollectionAudit.Row],
    checked: CollectionAudit.Checked
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionAudit:


  /** One linked item that disagrees with its record: the sources linked to it, the issues (`codes` and their `labels`),
    * the record it was judged against, the item (none when it is missing), and the citekey whose record an update would
    * write over it (`fix`, none when the item is missing).
    */
  type Checked = Checked.T
  object Checked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Row(
      id: Row.Id,
      citekeys: List[Row.Citekeys],
      codes: List[Row.Codes],
      labels: List[Row.Labels],
      record: CollectionRecordView,
      entry: Option[CollectionRecordView],
      fix: Option[Row.Fix]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Row:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Citekeys = Citekeys.T
    object Citekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Codes = Codes.T
    object Codes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Labels = Labels.T
    object Labels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fix = Fix.T
    object Fix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
