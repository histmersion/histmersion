package com.histmersion.cli

/** What can be wrong with a linked Calibre entry, worst first. */
enum AuditIssue(val label: AuditIssue.Label):
  case Missing extends AuditIssue(AuditIssue.Label("no such Calibre entry"))
  case Junk extends AuditIssue(AuditIssue.Label("Calibre's title or author is blank, Unknown or scraped"))
  case Title extends AuditIssue(AuditIssue.Label("titles differ"))
  case Authors extends AuditIssue(AuditIssue.Label("no author in common"))
  case Year extends AuditIssue(AuditIssue.Label("years differ"))

object AuditIssue:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
