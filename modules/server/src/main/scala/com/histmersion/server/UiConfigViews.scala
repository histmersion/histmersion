package com.histmersion.server

import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.instance.UiConfig
import com.histmersion.config.UiSettings
import io.scalaland.chimney.dsl.*

/** What the wire says of the `ui` block: the page's settings as `/api/config` serves them. */
object UiConfigViews:

  /** The page's settings: the `ui` block, plus what the server knows and the block does not hold. */
  type PageWriteKeyRequired = PageWriteKeyRequired.T
  object PageWriteKeyRequired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageAgentsEnabled = PageAgentsEnabled.T
  object PageAgentsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBaseMapSource = PageBaseMapSource.T
  object PageBaseMapSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageMapLanguage = PageMapLanguage.T
  object PageMapLanguage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSetupDue = PageSetupDue.T
  object PageSetupDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def page(
      ui: UiSettings,
      writeKeyRequired: com.histmersion.server.UiConfigViews.PageWriteKeyRequired,
      agentsEnabled: com.histmersion.server.UiConfigViews.PageAgentsEnabled,
      plugins: PluginsReport,
      baseMapSource: com.histmersion.server.UiConfigViews.PageBaseMapSource,
      mapLanguage: com.histmersion.server.UiConfigViews.PageMapLanguage,
      setupDue: com.histmersion.server.UiConfigViews.PageSetupDue = com.histmersion.server.UiConfigViews.PageSetupDue(false)
  ): UiConfig =
    ui.into[UiConfig]
      .withFieldConst(_.setupDue, setupDue)
      .withFieldConst(_.writeKeyRequired, writeKeyRequired)
      .withFieldConst(_.agentsEnabled, agentsEnabled)
      .withFieldConst(_.plugins, plugins)
      .withFieldConst(_.baseMapSource, baseMapSource)
      .withFieldConst(_.mapLanguage, mapLanguage)
      .transform
