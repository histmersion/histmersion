package com.histmersion.cli

/** What can be wrong with a linked Calibre entry, worst first. */
enum AuditIssue(val label: AuditIssue.Label):
  case Missing extends AuditIssue("no such Calibre entry")
  case Junk extends AuditIssue("Calibre's title or author is blank, Unknown or scraped")
  case Title extends AuditIssue("titles differ")
  case Authors extends AuditIssue("no author in common")
  case Year extends AuditIssue("years differ")

object AuditIssue:
  object Label:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
