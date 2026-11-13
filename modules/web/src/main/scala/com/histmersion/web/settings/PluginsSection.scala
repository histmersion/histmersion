package com.histmersion
package web.settings

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Wording
import com.histmersion.api.plugins.{PluginSet, PluginState, PluginStatus}
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure

/** Settings › Plugins: every plugin in this server — what it adds, whether it works — each turned on or off in the
  * settings file; and the plugins added to this install (`histmersion plugin add`).
  */
object PluginsSection:
  type Capability = Capability.T
  object Capability extends Newtype[String, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]

  /** What became of a plugin, in words. Pure. */

  def state(p: PluginStatus): State = PluginState.parse(p.state) match
    case Some(PluginState.Registered) => State("Working")
    case Some(PluginState.Disabled)   => State("Turned off")
    case Some(PluginState.Failed)     => State(s"Not working: ${p.error.map(_.sentence).getOrElse("no reason given")}")
    case Some(PluginState.Refused)    => State(s"Not loaded: ${p.error.map(_.sentence).getOrElse("no reason given")}")
    case None                         => State(Wording.humanize(Wording.Id(p.state.value)))

  /** What a plugin adds, in words (`source-fetcher:oa-core` → "Finds papers"). Pure. */
  def provides(p: PluginStatus): List[Capability] =
    p.provides.map(_.value.takeWhile(_ != ':')).distinct.map {
      case "source-fetcher"   => Capability("Finds papers")
      case "media-collection" => Capability("Pictures")
      case "event-catalogue"  => Capability("Events")
      case "ai-provider"      => Capability("An AI provider")
      case "secret-vault"     => Capability("A password manager")
      case other              => Capability(Wording.humanize(Wording.Id(other)))
    }

  def pane(ui: UiConfig): HtmlElement =
    val r = ui.plugins
    div(
      cls := "settings-section",
      SettingsForm(
        SettingsForm.ApplyTitle("Built-in plugins"),
        Some(
          SettingsForm.Intro(
            "Turn a plugin on or off; the server applies it at once. A plugin that needs a key of your own (Europeana, " +
              "Smithsonian) works once the key is in Keys and passwords."
          )
        ),
        r.plugins.map(p =>
          SettingsForm.Item(
            SettingsForm.Item.Key(s"plugins.${p.id}.enabled"),
            Some(SettingsForm.Item.Name(Wording.plugin(Wording.PluginId(p.id.value)))),
            Some(SettingsForm.Item.Help((state(p).value :: provides(p).map(_.value)).mkString(" · "))),
            fallback = Some(SettingsForm.Item.Fallback(p.enabled.toString))
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
          p(PluginSet.describe(r.set).value),
          r.set.toList.flatMap(_.jars).map(j => p(cls := "muted", j.file.value))
        )
      )
    )
