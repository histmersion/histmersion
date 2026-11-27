package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** An audit of every linked item: those that disagree with their records, and how many items were checked. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionAudit(
    findings: List[CollectionAudit.Row],
    checked: CollectionAudit.Checked
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionAudit:

  /** One linked item that disagrees with its record: the sources linked to it, the issues (`codes` and their `labels`),
    * the record it was judged against, the item (none when it is missing), and the citekey whose record an update would
    * write over it (`fix`, none when the item is missing).
    */
  type Checked = Checked.T
  object Checked extends Newtype[Int, Pure]

  final case class Row(
      id: Row.Id,
      citekeys: List[Row.Citekeys],
      codes: List[Row.Codes],
      labels: List[Row.Labels],
      record: CollectionRecordView,
      entry: Option[CollectionRecordView],
      fix: Option[Row.Fix]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Row:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Citekeys = Citekeys.T
    object Citekeys extends Newtype[String, Pure]
    type Codes = Codes.T
    object Codes extends Newtype[String, Pure]
    type Labels = Labels.T
    object Labels extends Newtype[String, Pure]
    type Fix = Fix.T
    object Fix extends Newtype[String, Pure]
