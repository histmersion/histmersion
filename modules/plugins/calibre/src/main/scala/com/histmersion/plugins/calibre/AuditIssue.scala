package com.histmersion.plugins.calibre

/** What can be wrong with a linked Calibre entry, worst first. */
enum AuditIssue(val label: AuditIssue.Label):
  case Missing extends AuditIssue(_root_.com.histmersion.plugins.calibre.AuditIssue.Label("no such Calibre entry"))
  case Junk extends AuditIssue(_root_.com.histmersion.plugins.calibre.AuditIssue.Label("Calibre's title or author is blank, Unknown or scraped"))
  case Title extends AuditIssue(_root_.com.histmersion.plugins.calibre.AuditIssue.Label("titles differ"))
  case Authors extends AuditIssue(_root_.com.histmersion.plugins.calibre.AuditIssue.Label("no author in common"))
  case Year extends AuditIssue(_root_.com.histmersion.plugins.calibre.AuditIssue.Label("years differ"))

object AuditIssue:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
