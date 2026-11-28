package com.histmersion
package server.markdownlint

import com.histmersion.api.checks.FormatIssue

final case class Offence(issue: FormatIssue, edits: List[Edit])
