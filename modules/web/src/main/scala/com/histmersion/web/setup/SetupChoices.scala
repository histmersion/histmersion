package com.histmersion
package web.setup

import com.histmersion.api.instance.MapStartConfig
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

/** What a person chose in the setup. `area`: the background map's box to download (`None`: none); `finders`: each
  * open-access finder on or off.
  */
final case class SetupChoices(
    email: SetupChoices.Email = SetupChoices.Email(""),
    finders: Map[SetupChoices.FindersKey, SetupChoices.FindersValue] = Map.empty,
    area: Option[SetupChoices.Area] = Some(SetupChoices.Area("world")),
    start: MapStartConfig = MapStartConfig(),
    boundaries: SetupChoices.Boundaries = SetupChoices.Boundaries.On,
    events: SetupChoices.Events = SetupChoices.Events.On,
    pictures: SetupChoices.Pictures = SetupChoices.Pictures.Off,
    morePlaces: SetupChoices.MorePlaces = SetupChoices.MorePlaces.Off,
    agents: SetupChoices.Agents = SetupChoices.Agents.Off,
    calibre: SetupChoices.Calibre = SetupChoices.Calibre("")
)

object SetupChoices:
  type Email = Email.T
  object Email extends Newtype[String, Pure]
  type FindersKey = FindersKey.T
  object FindersKey extends Newtype[String, Pure]
  enum FindersValue derives CanEqual:
    case On, Off
  object FindersValue extends TwoStates[FindersValue]:
    extension (findersValue: FindersValue) def on: Boolean = findersValue == On
  type Area = Area.T
  object Area extends Newtype[String, Pure]
  enum Boundaries derives CanEqual:
    case On, Off
  object Boundaries extends TwoStates[Boundaries]:
    extension (boundaries: Boundaries) def on: Boolean = boundaries == On
  enum Events derives CanEqual:
    case On, Off
  object Events extends TwoStates[Events]:
    extension (events: Events) def on: Boolean = events == On
  enum Pictures derives CanEqual:
    case On, Off
  object Pictures extends TwoStates[Pictures]:
    extension (pictures: Pictures) def on: Boolean = pictures == On
  enum MorePlaces derives CanEqual:
    case On, Off
  object MorePlaces extends TwoStates[MorePlaces]:
    extension (morePlaces: MorePlaces) def on: Boolean = morePlaces == On
  enum Agents derives CanEqual:
    case On, Off
  object Agents extends TwoStates[Agents]:
    extension (agents: Agents) def on: Boolean = agents == On
  type Calibre = Calibre.T
  object Calibre extends Newtype[String, Pure]
