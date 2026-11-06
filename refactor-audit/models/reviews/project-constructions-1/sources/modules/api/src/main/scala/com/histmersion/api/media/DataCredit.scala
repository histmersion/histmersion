package com.histmersion.api.media
import com.histmersion.api.given
final case class DataCredit(
    name: DataCredit.Name,
    usedFor: DataCredit.UsedFor,
    licence: DataCredit.Licence,
    licenceUrl: Option[DataCredit.LicenceUrl],
    credit: com.histmersion.api.media.MediaItem.Credit,
    url: DataCredit.Url,
    onMap: DataCredit.OnMap = _root_.com.histmersion.api.media.DataCredit.OnMap(true)
)

object DataCredit:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UsedFor = UsedFor.T
  object UsedFor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenceUrl = LicenceUrl.T
  object LicenceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnMap = OnMap.T
  object OnMap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
