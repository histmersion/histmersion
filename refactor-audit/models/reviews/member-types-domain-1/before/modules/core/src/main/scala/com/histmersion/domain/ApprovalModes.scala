package com.histmersion.domain

import com.histmersion.domain.automation.Approval
object ApprovalModes:
  /** A value that is not `auto` — a typo included — is the safe one. */
  def configured(raw: String): Approval.Mode = Approval.Mode.parse(raw).getOrElse(Approval.Mode.Manual)
