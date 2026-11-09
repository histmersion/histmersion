package com.histmersion
package util.settings

import io.scalaland.chimney.dsl.*
import com.histmersion.Problem as ValuesProblem
import com.histmersion.api.Problem
import com.histmersion.api.settings.{ConfigEdit, ReloadReport}
import com.histmersion.config.edit.*
import com.histmersion.api.Api

/** `GET|POST /api/settings`, `POST /api/server/restart`, the `config` tool's `get`, `set`, `unset` and `restart`, and
  * `histmersion config get|set|unset|restart` over the server. `knownKeys`: keys of open blocks listed although the
  * file does not set them (each plugin's `enabled` and settings).
  */
final class SettingsControllers(
    settings: SettingsService,
    restarts: RestartService,
    reload: () => ReloadReport,
    knownKeys: () => List[SettingsService.ConfigPath] = () => Nil
):

  def page(prefix: Option[SettingsService.ConfigPath]): Either[Api.Error, ConfigEdit.Page] =
    for
      p <- prefix
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold[Either[ValuesProblem, Option[SettingKey]]](Right(None))(raw =>
          SettingKey.parse(SettingKey.ParseRaw(raw)).map(Some(_))
        )
        .left
        .map(why => Api.Error(why.transformInto[Problem]))
      snap <- settings
        .read(p, knownKeys().flatMap(k => SettingKey.parse(SettingKey.ParseRaw(k.value)).toOption))
        .left
        .map(SettingsViews.error)
    yield SettingsViews.page(snap, restarts.available)

  /** The changes, each value read as its setting's kind; then the reload, when asked for and something changed. */
  def write(w: ConfigEdit.Write): Either[Api.Error, ConfigEdit.Written] =
    for
      edits <- SettingEdit
        .parseAll(
          w.changes.map(c => SettingKey.ParseRaw(c.key.value) -> c.value.map(v => SettingValue.Typed(v.value))),
          settings.schema
        )
        .left
        .map(SettingsViews.error)
      version <- w.version
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold[Either[Api.Error, Option[FileVersion]]](Right(None))(v =>
          FileVersion
            .either(v)
            .map(Some(_))
            .left
            .map(_ => Api.Error(Problem(s"'$v' is not a settings file's version")))
        )
      outcome <- settings
        .write(edits, version, SettingPolicy.LiteralConsent(w.literalConsent.value))
        .left
        .map(SettingsViews.error)
    yield
      val reloaded = Option.when(w.reload.value && outcome.wrote)(reload())
      SettingsViews.written(outcome, reloaded, restarts.available)

  def restart(): Either[Api.Error, ConfigEdit.Restart] =
    restarts
      .restart()
      .fold(
        why => Left(Api.Error(why.transformInto[Problem], Some(Api.Error.Code(ConfigEdit.CodeUnavailable)))),
        msg => Right(ConfigEdit.Restart(accepted = ConfigEdit.Restart.Accepted(true), msg))
      )

  def setupDue: Boolean = settings.setupDue
