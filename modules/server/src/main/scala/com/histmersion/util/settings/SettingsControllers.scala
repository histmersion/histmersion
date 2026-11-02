package com.histmersion.util.settings

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
    knownKeys: () => List[String] = () => Nil
):

  def page(prefix: Option[String]): Either[Api.Error, ConfigEdit.Page] =
    for
      p <- prefix
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold(Right(None))(SettingKey.parse(_).map(Some(_)))
        .left
        .map(Api.Error(_))
      snap <- settings.read(p, knownKeys().flatMap(k => SettingKey.parse(k).toOption)).left.map(SettingsViews.error)
    yield SettingsViews.page(snap, restarts.available)

  /** The changes, each value read as its setting's kind; then the reload, when asked for and something changed. */
  def write(w: ConfigEdit.Write): Either[Api.Error, ConfigEdit.Written] =
    for
      edits <- SettingEdit.parseAll(w.changes.map(c => c.key -> c.value), settings.schema).left.map(SettingsViews.error)
      version <- w.version
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold(Right(None))(v =>
          FileVersion.either(v).map(Some(_)).left.map(_ => Api.Error(s"'$v' is not a settings file's version"))
        )
      outcome <- settings.write(edits, version, w.literalConsent).left.map(SettingsViews.error)
    yield
      val reloaded = Option.when(w.reload && outcome.wrote)(reload())
      SettingsViews.written(outcome, reloaded, restarts.available)

  def restart(): Either[Api.Error, ConfigEdit.Restart] =
    restarts
      .restart()
      .fold(
        why => Left(Api.Error(why, Some(ConfigEdit.CodeUnavailable))),
        msg => Right(ConfigEdit.Restart(accepted = true, msg))
      )

  def setupDue: Boolean = settings.setupDue
