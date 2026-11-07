package com.histmersion.web.setup

import com.histmersion.api.catalogue.MapArea
import com.histmersion.api.settings.ConfigEdit

/** The settings and the downloads a setup's choices come to. Pure. */
object SetupPlan:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An e-mail address that can be one (empty: none, which is allowed). */

  def email(s: com.histmersion.web.setup.SetupChoices.Email): Either[com.histmersion.web.setup.SetupPlan.Problem, com.histmersion.web.setup.SetupChoices.Email] =
    val t = s.value.trim
    if t.isEmpty || t.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") then Right(com.histmersion.web.setup.SetupChoices.Email(t))
    else Left(Problem(s"$t does not look like an e-mail address."))

  /** The settings to write: what differs from `before`, and the mark that the setup was done. A change the file already
    * holds is skipped by the server, so writing it again does no harm.
    */
  def changes(c: SetupChoices, before: SetupChoices): List[(ConfigEdit.Change.Key, Option[ConfigEdit.Change.Value])] =
    val finders = c.finders.toList.sortBy(_._1.value).collect {
      case (id, on) if !before.finders.get(id).contains(on) =>
        ConfigEdit.Change.Key(s"plugins.$id.enabled") -> Some(ConfigEdit.Change.Value(on.value.toString))
    }
    val start = if c.start == before.start then Nil
    else com.histmersion.web.settings.StartPlace.changes(c.start).map((k, v) => k -> Some(v))
    List(
      Option.when(c.email.value.trim != before.email.value.trim)(
        ConfigEdit.Change.Key("acquire.contact-email") -> Some(ConfigEdit.Change.Value(c.email.value.trim))
      ),
      c.area.filter(a => !before.area.contains(a)).map(a => ConfigEdit.Change.Key("basemap.bbox") -> Some(ConfigEdit.Change.Value(a.value))),
      Option.when(c.agents != before.agents)(ConfigEdit.Change.Key("agents.enabled") -> Some(ConfigEdit.Change.Value(c.agents.value.toString))),
      Option.when(c.calibre.value.trim != before.calibre.value.trim && c.calibre.value.trim.nonEmpty)(
        ConfigEdit.Change.Key("cli.calibre.library-dir") -> Some(ConfigEdit.Change.Value(c.calibre.value.trim))
      )
    ).flatten ++ finders ++ start :+ (ConfigEdit.Change.Key("setup.completed") -> Some(ConfigEdit.Change.Value("true")))

  def downloads(c: SetupChoices): List[SetupDownload] =
    c.area.map(SetupDownload.Map(_)).toList ++
      Option.when(c.boundaries.value)(SetupDownload.Boundaries) ++
      Option.when(c.events.value)(SetupDownload.Events) ++
      Option.when(c.pictures.value)(SetupDownload.Pictures) ++
      Option.when(c.morePlaces.value)(SetupDownload.MorePlaces)

  /** About how much the chosen map area downloads, in GB. */
  def mapGb(c: SetupChoices, areas: List[MapArea]): com.histmersion.api.catalogue.MapArea.ApproxGb =
    c.area.flatMap(a => areas.find(_.bbox.value == a.value)).fold(MapArea.ApproxGb(0.0))(_.approxGb)
