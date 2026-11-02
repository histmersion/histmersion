package com.histmersion.util.identity

import com.histmersion.values.BundleKind

/** The kinds of record whose old identifiers stay aliases (#1799), as the record references of every domain name them:
  * what each is called and what its identifier is called, and which bundle kind carries each. A closed vocabulary of
  * the identity area, as `BundleKind` is of the bundle: a lower domain names a higher one's kind (an attestation's
  * target, an asset link's) without naming that domain. Each owner registers the table its aliases are kept in.
  */
object AliasKinds:
  private def kind(noun: String, idWord: String): AliasKind = AliasKind(RecordNoun(noun), KeyWord(idWord))

  val SourceRecords: AliasKind = kind("source", "citekey")
  val ArticleRecords: AliasKind = kind("article", "slug")
  val EventRecords: AliasKind = kind("event", "id")
  val GoalRecords: AliasKind = kind("goal", "slug")
  val PlaceRecords: AliasKind = kind("place", "id")

  /** Every kind, in the order the alias tables are listed. */
  val all: List[AliasKind] = List(SourceRecords, ArticleRecords, EventRecords, GoalRecords, PlaceRecords)

  def of(b: BundleKind): Option[AliasKind] = b match
    case BundleKind.Article => Some(ArticleRecords)
    case BundleKind.Event   => Some(EventRecords)
    case BundleKind.Goal    => Some(GoalRecords)
    case BundleKind.Place   => Some(PlaceRecords)
    case _                  => None

  /** The kind a reference's target type names (`event`, `place`, `source`, `article`; any case, surrounding space
    * ignored), when it is one whose old identifiers are followed.
    */
  def ofTarget(targetType: String): Option[AliasKind] =
    targetType.trim.toLowerCase(java.util.Locale.ROOT) match
      case "event"   => Some(EventRecords)
      case "place"   => Some(PlaceRecords)
      case "source"  => Some(SourceRecords)
      case "article" => Some(ArticleRecords)
      case _         => None
