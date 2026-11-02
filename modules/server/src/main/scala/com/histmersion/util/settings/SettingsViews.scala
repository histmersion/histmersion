package com.histmersion.util.settings

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.config.edit.*
import com.histmersion.api.Api

/** Settings as the wire shows them ([[SettingShown]]: a credential's value never crosses). */
object SettingsViews:

  def page(s: SettingsSnapshot, restartable: Boolean): ConfigEdit.Page =
    val fields = s.readings.map(field)
    val sections = fields
      .map(_.section)
      .distinct
      .map(id => ConfigEdit.Section(id, doc = sectionDoc(id), fields.filter(_.section == id)))
    ConfigEdit.Page(s.file, s.exists, s.version.value, sections, s.problems, restartable, s.setupDue)

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
      reload: Option[com.histmersion.api.settings.ReloadReport],
      restartable: Boolean
  ): ConfigEdit.Written =
    ConfigEdit.Written(
      o.file,
      o.changed.map(_.value),
      o.unchanged.map(_.value),
      o.backup,
      o.version.value,
      o.created,
      o.restart.map((k, why) => ConfigEdit.RestartNote(k.value, why)),
      o.warnings,
      reload,
      restartable
    )

  def error(e: SettingError): Api.Error = e match
    case SettingError.Changed                        => Api.Error(e.message, Some(ConfigEdit.CodeChanged))
    case SettingError.NoFile(_) | SettingError.Io(_) => Api.Error(e.message, Some(ConfigEdit.CodeUnavailable))
    case _                                           => Api.Error(e.message, Some(ConfigEdit.CodeRefused))
