package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates
final case class DataCredit(
    name: DataCredit.Name,
    usedFor: DataCredit.UsedFor,
    licence: DataCredit.Licence,
    licenceUrl: Option[DataCredit.LicenceUrl],
    credit: MediaItem.Credit,
    url: DataCredit.Url,
    onMap: DataCredit.OnMap = DataCredit.OnMap.OnMap
)

object DataCredit:
  type Name = Name.T
  object Name extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type UsedFor = UsedFor.T
  object UsedFor extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type LicenceUrl = LicenceUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenceUrl extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Url = Url.T
  object Url extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  enum OnMap derives CanEqual:
    case OnMap, OffMap
  object OnMap extends TwoStates[OnMap]:
    extension (onMap: OnMap) def onMap: Boolean = onMap == OnMap
