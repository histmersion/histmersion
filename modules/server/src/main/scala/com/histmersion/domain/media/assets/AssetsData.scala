package com.histmersion.domain.media.assets

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun}

import com.augustnagro.magnum.DbCon

/** The pictures' share of the media's data contribution: the check of their licences. */
object AssetsData:
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def checks(owner: DataOwner): List[CheckRule] = List(Licences(owner))

  /** `media-licence`: a picture with no licence, or a provider's with no credit line. */
  final private class Licences(val owner: DataOwner) extends CheckRule:
    val rule: Rule = Lint.rule(MediaLicenceRule).getOrElse(Rule(MediaLicenceRule, MediaLicenceRule))
    def report(context: CheckContext, maxItems: com.histmersion.domain.media.assets.AssetsData.ReportMaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, MediaRepo.licenceGaps().map((id, detail) => LintItem(id, detail)), maxItems)

