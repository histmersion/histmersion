package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.plugins.{PluginSet, PluginState, PluginStatus}
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*

/** Settings › Plugins: every plugin in this server — what it adds, whether it works — each turned on or off in the
  * settings file; and the plugins added to this install (`histmersion plugin add`).
  */
object PluginsSection:
  type Capability = Capability.T
  object Capability extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What became of a plugin, in words. Pure. */

  def state(p: PluginStatus): com.histmersion.web.settings.PluginsSection.State = PluginState.parse(p.state) match
    case Some(PluginState.Registered) => "Working"
    case Some(PluginState.Disabled)   => "Turned off"
    case Some(PluginState.Failed)     => s"Not working: ${p.error.getOrElse("no reason given")}"
    case Some(PluginState.Refused)    => s"Not loaded: ${p.error.getOrElse("no reason given")}"
    case None                         => Wording.humanize(p.state)

  /** What a plugin adds, in words (`source-fetcher:oa-core` → "Finds papers"). Pure. */
  def provides(p: PluginStatus): List[com.histmersion.web.settings.PluginsSection.Capability] =
    p.provides.map(_.takeWhile(_ != ':')).distinct.map {
      case "source-fetcher"   => "Finds papers"
      case "media-collection" => "Pictures"
      case "event-catalogue"  => "Events"
      case "ai-provider"      => "An AI provider"
      case "secret-vault"     => "A password manager"
      case other              => Wording.humanize(other)
    }

  def pane(ui: UiConfig): HtmlElement =
    val r = ui.plugins
    div(
      cls := "settings-section",
      SettingsForm(
        "Built-in plugins",
        Some(
          "Turn a plugin on or off; the server applies it at once. A plugin that needs a key of your own (Europeana, " +
            "Smithsonian) works once the key is in Keys and passwords."
        ),
        r.plugins.map(p =>
          SettingsForm.Item(
            s"plugins.${p.id}.enabled",
            Some(Wording.plugin(p.id)),
            Some((state(p) :: provides(p)).mkString(" · ")),
            fallback = Some(p.enabled.toString)
          )
        )
      ),
      sectionTag(
        cls := "settings-card",
        div(
          cls := "settings-card-head",
          h2(cls := "settings-card-title", "Added to this install"),
          p(
            cls := "settings-card-intro",
            "Plugins of your own are added on the command line (histmersion plugin add), " +
              "which links them into the server."
          )
        ),
        div(
          cls := "settings-card-body",
          p(PluginSet.describe(r.set)),
          r.set.toList.flatMap(_.jars).map(j => p(cls := "muted", j.file))
        )
      )
    )
