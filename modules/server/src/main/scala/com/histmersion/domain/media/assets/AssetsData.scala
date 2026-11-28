package com.histmersion
package domain.media.assets

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun}

import com.augustnagro.magnum.DbCon
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** The pictures' share of the media's data contribution: the check of their licences. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AssetsData:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checks(owner: DataOwner): List[CheckRule] = List(Licences(owner))

  /** `media-licence`: a picture with no licence, or a provider's with no credit line. */
  final private class Licences(val owner: DataOwner) extends CheckRule:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val rule: Rule = Lint
      .rule(Rule.Name(MediaLicenceRule))
      .getOrElse(Rule(Rule.Name(MediaLicenceRule), Rule.Description(MediaLicenceRule)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(
        rule,
        MediaRepo.licenceGaps().map((id, detail) => LintItem(Citekey(id.value), LintItem.Detail(detail.value))),
        maxItems
      )
