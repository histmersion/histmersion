package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueueImport(text: QueueItem.Text, replace: QueueImport.Replace = QueueImport.Replace.Replace)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QueueImport:
  enum Replace derives CanEqual:
    case Replace, Merge
  object Replace extends TwoStates[Replace]:
    extension (replace: Replace) def replace: Boolean = replace == Replace
