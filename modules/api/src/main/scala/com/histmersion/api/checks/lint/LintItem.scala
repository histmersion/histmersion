package com.histmersion
package api.checks.lint

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LintItem(citekey: Citekey, detail: LintItem.Detail) derives JsonCodec, KindlingsSchema, api.json.Schema

object LintItem:
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]:
    private val FillerMark = "filler: "

    def filler(matches: List[String]): Detail =
      Detail(s"$FillerMark${matches.take(3).mkString(", ")}${
          if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
        }")

    extension (d: Detail) def fillerMatches: String = d.value.stripPrefix(FillerMark)
