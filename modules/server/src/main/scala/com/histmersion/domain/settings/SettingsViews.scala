package com.histmersion.domain.settings

import com.histmersion.api.{ApiError, ConfigEdit}
import com.histmersion.config.edit.*

/** Settings as the wire shows them ([[SettingShown]]: a credential's value never crosses). */
object SettingsViews:

  def page(s: SettingsSnapshot, restartable: Boolean): ConfigEdit.Page =
    val fields = s.readings.map(field)
    val sections = fields
      .map(_.section)
      .distinct
      .map(id => ConfigEdit.Section(id, doc = sectionDoc(id), fields.filter(_.section == id)))
    ConfigEdit.Page(s.file.map(_.toString), s.exists, s.version.value, sections, s.problems, restartable, s.setupDue)

  private def sectionDoc(id: String): String =
    SettingKey.either(s"${SettingKey.Root}.$id").toOption.fold("")(ConfigSchema.reference.blockDoc)

  def field(r: SettingReading): ConfigEdit.Field =
    val s = SettingShown.of(r)
    ConfigEdit.Field(
      s.key,
      s.section,
      s.kind,
      s.doc,
      s.default,
      s.value,
      s.effective,
      s.source,
      s.envVar,
      s.restart,
      s.credentialForm.map(form =>
        ConfigEdit.Credential(s.credentialRule.getOrElse("reference"), form, s.credentialShown)
      ),
      s.locked
    )

  def written(
      o: WriteOutcome,
      reload: Option[com.histmersion.api.ReloadReport],
      restartable: Boolean
  ): ConfigEdit.Written =
    ConfigEdit.Written(
      o.file.toString,
      o.changed.map(_.value),
      o.unchanged.map(_.value),
      o.backup.map(_.toString),
      o.version.value,
      o.created,
      o.restart.map((k, why) => ConfigEdit.RestartNote(k.value, why)),
      o.warnings,
      reload,
      restartable
    )

  def error(e: SettingError): ApiError = e match
    case SettingError.Changed                        => ApiError(e.message, Some(ConfigEdit.CodeChanged))
    case SettingError.NoFile(_) | SettingError.Io(_) => ApiError(e.message, Some(ConfigEdit.CodeUnavailable))
    case _                                           => ApiError(e.message, Some(ConfigEdit.CodeRefused))
