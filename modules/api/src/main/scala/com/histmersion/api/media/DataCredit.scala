package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class DataCredit(
    name: DataCredit.Name,
    usedFor: DataCredit.UsedFor,
    licence: DataCredit.Licence,
    licenceUrl: Option[DataCredit.LicenceUrl],
    credit: MediaItem.Credit,
    url: DataCredit.Url,
    onMap: DataCredit.OnMap = DataCredit.OnMap(true)
)

object DataCredit:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type UsedFor = UsedFor.T
  object UsedFor extends RefinedType[String, Pure]
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]
  type LicenceUrl = LicenceUrl.T
  object LicenceUrl extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type OnMap = OnMap.T
  object OnMap extends RefinedType[Boolean, Pure]
