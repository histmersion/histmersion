package com.histmersion.domain

import com.histmersion.domain.automation.Approval
object ApprovalModes:
  /** A value that is not `auto` — a typo included — is the safe one. */
  type ConfiguredRaw = ConfiguredRaw.T
  object ConfiguredRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def configured(raw: com.histmersion.domain.ApprovalModes.ConfiguredRaw): Approval.Mode = Approval.Mode.parse(raw).getOrElse(Approval.Mode.Manual)
