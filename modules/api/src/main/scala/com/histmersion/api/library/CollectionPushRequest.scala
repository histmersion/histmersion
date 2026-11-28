package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

/** Add source `citekey`'s stored original to a collection as a new item — or with `replace`, swap the file of the item
  * it is linked to.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionPushRequest(
    citekey: Citekey,
    replace: CollectionPushRequest.Replace = CollectionPushRequest.Replace.Keep
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionPushRequest:
  enum Replace derives CanEqual:
    case Replace, Keep
  object Replace extends TwoStates[Replace]:
    extension (replace: Replace) def replace: Boolean = replace == Replace
