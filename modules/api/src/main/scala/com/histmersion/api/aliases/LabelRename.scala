package com.histmersion
package api.aliases
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LabelRename(to: LabelRename.To) derives JsonCodec, KindlingsSchema, api.json.Schema

object LabelRename:
  export api.library.Label.Text as To
