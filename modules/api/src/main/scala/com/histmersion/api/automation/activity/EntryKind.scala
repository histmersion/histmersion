package com.histmersion
package api.automation.activity

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum EntryKind(val wire: EntryKind.Wire) derives CanEqual:
  case Run extends EntryKind(EntryKind.Wire("run"))
  case Job extends EntryKind(EntryKind.Wire("job"))
  case Log extends EntryKind(EntryKind.Wire("log"))
  case Decision extends EntryKind(EntryKind.Wire("decision"))

  def wording: String = this match
    case Run      => ("Agent work")
    case Job      => ("Background job")
    case Log      => ("Note")
    case Decision => ("You decided")

object EntryKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[EntryKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  extension (kind: EntryKind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def keyOf(id: Entry.Id): Entry.Key = Entry.Key(s"${kind.wire}:$id")
