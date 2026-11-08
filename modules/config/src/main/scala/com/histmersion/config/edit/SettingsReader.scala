package com.histmersion
package config.edit

import com.histmersion.config.ConfigLayers
import org.ekrich.config.{Config, ConfigObject, ConfigUtil}

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Every setting as it stands, from the schema, the settings file as read and the merged settings it loaded into
  * (`merged`: environment > file > reference.conf, `histmersion { … }`). The keys a file writes in an open block (a
  * plugin's own settings) are listed too, after reference.conf's, and `extra` keys of open blocks the caller knows of
  * (each plugin's `enabled` and settings). Pure.
  */
object SettingsReader:


  def read(
      schema: ConfigSchema,
      state: ConfigFileState,
      merged: Config,
      env: Config,
      prefix: Option[SettingKey],
      extra: List[SettingKey] = Nil
  ): List[SettingReading] =
    val within = (k: SettingKey) => prefix.forall(p => k.within(p))
    val known = schema.fields.filter(f => within(f.key))
    val open = (openKeys(state.parsed) ++ extra).distinct
      .filter(within)
      .filterNot(k => known.exists(_.key == k))
      .flatMap(schema.field)
    (known ++ open).map(reading(_, state, merged, env))

  def reading(field: SettingField, state: ConfigFileState, merged: Config, env: Config): SettingReading =
    val key = field.key
    val (inFile, locked) = ConfigTextEdit.written(state.parsed, key, state.here) match
      case Written.Here(v)        => (SettingValue.fromConfig(v), None)
      case Written.Nowhere        => (None, None)
      case Written.Substituted(_) => (None, Some(SettingError.Substitution(key)))
      case Written.Elsewhere(f)   =>
        (
          Try(state.parsed.getValue(key.hoconPath.value)).toOption.flatMap(SettingValue.fromConfig),
          Some(SettingError.FromInclude(key, f))
        )
    val fromEnv = field.envVar.exists(v => env.hasPath(v.value))
    val source =
      if fromEnv then SettingSource.Environment
      else if inFile.isDefined || locked.isDefined then SettingSource.File
      else SettingSource.Default
    val effective = Try(merged.getValue(key.hoconPath.value)).toOption.flatMap(SettingValue.fromConfig)
    val text = effective.collect { case SettingValue.Text(t) => t.value }.getOrElse("")
    val credential = Option.when(field.secret != SecretRule.NotSecret)(SecretForm.of(text, field.secret))
    val shown = credential.flatMap(_ => SecretForm.shown(text, field.secret).map(SettingReading.Shown(_)))
    SettingReading(field, inFile, effective, source, credential, shown, locked)

  /** The keys the file writes inside the open blocks (each plugin's own settings, …). */
  def openKeys(parsed: Config): List[SettingKey] =
    ConfigLayers.OpenBlocks
      .flatMap { block =>
        val path = ConfigUtil.joinPath(block.map(_.value).asJava)
        Try(parsed.getValue(path)).toOption.collect { case o: ConfigObject => leaves(o, block) }.getOrElse(Nil)
      }
      .flatMap(ws => SettingKey.either(ws.mkString(".")).toOption)
      .distinct

  private def leaves(o: ConfigObject, prefix: List[SettingKey.Segment]): List[List[SettingKey.Segment]] =
    Try(o.keySet.asScala.toList.sorted).getOrElse(Nil).flatMap { k =>
      Option(o.get(k)) match
        case Some(child: ConfigObject) => leaves(child, prefix :+ SettingKey.Segment(k))
        case Some(_)                   => List(prefix :+ SettingKey.Segment(k))
        case None                      => Nil
    }
