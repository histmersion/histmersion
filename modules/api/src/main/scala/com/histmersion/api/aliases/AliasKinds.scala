package com.histmersion
package api.aliases

import com.histmersion.api.automation.activity.Entry

/** The kinds of record whose old identifiers stay aliases (#1799), as the record references of every domain name them:
  * what each is called and what its identifier is called, and which bundle kind carries each. A closed vocabulary of
  * the identity area, as `BundleKind` is of the bundle: a lower domain names a higher one's kind (an attestation's
  * target, an asset link's) without naming that domain. Each owner registers the table its aliases are kept in.
  */
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.automation.Activity
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
object AliasKinds:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def kind(noun: String, idWord: String): AliasKind = AliasKind(RecordNoun((noun)), KeyWord((idWord)))

  val SourceRecords: AliasKind = kind(("source"), ("citekey"))
  val ArticleRecords: AliasKind = kind(("article"), ("slug"))
  val EventRecords: AliasKind = kind(("event"), ("id"))
  val GoalRecords: AliasKind = kind(("goal"), ("slug"))
  val PlaceRecords: AliasKind = kind(("place"), ("id"))

  /** Every kind, in the order the alias tables are listed. */
  val all: List[AliasKind] = List(SourceRecords, ArticleRecords, EventRecords, GoalRecords, PlaceRecords)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def of(b: Bundle.Kind): Option[AliasKind] = b match
    case Bundle.Kind.Article => Some(ArticleRecords)
    case Bundle.Kind.Event   => Some(EventRecords)
    case Bundle.Kind.Goal    => Some(GoalRecords)
    case Bundle.Kind.Place   => Some(PlaceRecords)
    case _                   => None

  /** The kind a reference's target type names (`event`, `place`, `source`, `article`; any case, surrounding space
    * ignored), when it is one whose old identifiers are followed.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ofTarget(targetType: Entry.TargetKind): Option[AliasKind] =
    (targetType).value.trim.toLowerCase(java.util.Locale.ROOT) match
      case "event"   => Some(EventRecords)
      case "place"   => Some(PlaceRecords)
      case "source"  => Some(SourceRecords)
      case "article" => Some(ArticleRecords)
      case _         => None
