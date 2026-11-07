package com.histmersion.domain.media.assets

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun}

import com.augustnagro.magnum.DbCon

/** The pictures' share of the media's data contribution: the check of their licences. */
object AssetsData:
  def checks(owner: DataOwner): List[CheckRule] = List(Licences(owner))

  /** `media-licence`: a picture with no licence, or a provider's with no credit line. */
  final private class Licences(val owner: DataOwner) extends CheckRule:
    val rule: Rule = Lint.rule(Rule.Name(MediaLicenceRule)).getOrElse(Rule(Rule.Name(MediaLicenceRule), Rule.Description(MediaLicenceRule)))
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, MediaRepo.licenceGaps().map((id, detail) => LintItem(com.histmersion.api.library.Citekey(id.value), LintItem.Detail(detail.value))), ItemLimit(maxItems.value))

