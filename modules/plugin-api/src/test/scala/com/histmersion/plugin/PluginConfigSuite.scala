package com.histmersion.plugin

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{ConfigFactory, ConfigValueFactory}

object PluginConfigSuite:
  final case class Settings(title: Settings.Title, retries: Settings.Retries, enabled: Settings.Enabled, weight: Settings.Weight, aliases: List[Settings.Title], delay: Option[Settings.Retries]) derives ConfigReader
  object Settings:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Retries = Retries.T
    object Retries extends _root_.io.github.iltotore.iron.RefinedType[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Weight = Weight.T
    object Weight extends _root_.io.github.iltotore.iron.RefinedType[Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

class PluginConfigSuite extends munit.FunSuite:
  import PluginConfigSuite.Settings

  private val config = ConfigFactory.parseString("title = \"\", retries = \"3\", enabled = \"true\", weight = \"1.25\", aliases = [\"one\", \"two\"], delay = null")

  test("newtypes derive from base readers, including lenient environment strings and nested containers"):
    assertEquals(
      ConfigReader[Settings].from(config.root),
      Right(Settings(Settings.Title(""), Settings.Retries(3), Settings.Enabled(true), Settings.Weight(1.25), List(Settings.Title("one"), Settings.Title("two")), None))
    )
    val delayed = config.withValue("delay", ConfigValueFactory.fromAnyRef("5"))
    assertEquals(ConfigReader[Settings].from(delayed.root).map(_.delay), Right(Some(Settings.Retries(5))))

  test("base-reader failures remain failures"):
    assert(ConfigReader[Settings].from(config.withValue("retries", ConfigValueFactory.fromAnyRef("three")).root).isLeft)
    assert(ConfigReader[Settings].from(config.withValue("enabled", ConfigValueFactory.fromAnyRef("truthy")).root).isLeft)
    assert(ConfigReader[Settings].from(config.withValue("weight", ConfigValueFactory.fromAnyRef("heavy")).root).isLeft)

  test("an explicit reader takes precedence over the generic newtype reader"):
    given ConfigReader[Settings.Title] = ConfigReader.stringReader.map(raw => Settings.Title("custom:" + raw))
    assertEquals(ConfigReader[Settings.Title].from(ConfigValueFactory.fromAnyRef("raw")), Right(Settings.Title("custom:raw")))
