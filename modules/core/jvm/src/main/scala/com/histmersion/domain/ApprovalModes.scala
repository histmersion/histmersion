package com.histmersion.domain

import com.histmersion.values.ApprovalMode

object ApprovalModes:
  /** A value that is not `auto` — a typo included — is the safe one. */
  def configured(raw: String): ApprovalMode = ApprovalMode.parse(raw).getOrElse(ApprovalMode.Manual)
