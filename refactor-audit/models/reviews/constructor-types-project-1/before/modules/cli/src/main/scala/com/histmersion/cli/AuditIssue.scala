package com.histmersion.cli

/** What can be wrong with a linked Calibre entry, worst first. */
enum AuditIssue(val label: String):
  case Missing extends AuditIssue("no such Calibre entry")
  case Junk extends AuditIssue("Calibre's title or author is blank, Unknown or scraped")
  case Title extends AuditIssue("titles differ")
  case Authors extends AuditIssue("no author in common")
  case Year extends AuditIssue("years differ")
