package com.histmersion
package server.markdownlint

import com.histmersion.api.checks.FormatIssue

final case class Finding(issue: FormatIssue, edits: List[Edit])
