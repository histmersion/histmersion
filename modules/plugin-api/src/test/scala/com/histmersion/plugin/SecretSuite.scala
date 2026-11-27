package com.histmersion
package plugin

import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
final case class WithSecrets(token: Secret, password: Option[Secret], retries: Int, verbose: Boolean)
    derives ConfigReader

final class ExamplePlugin extends Plugin:
  type Config = WithSecrets
  val info = Plugin.Info(Plugin.Info.Id("example"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(config: WithSecrets, ctx: Plugin.Context) = Right(())

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class SecretSuite extends munit.FunSuite:

  test("the three reference forms parse"):
    assertEquals(
      Secret.Ref.parse(Secret.Ref.ParseText("value:hunter2")),
      Right(Secret.Ref.Value(Secret.Ref.Value.Literal("hunter2")))
    )
    assertEquals(
      Secret.Ref.parse(Secret.Ref.ParseText("env:PORTAL_TOKEN")),
      Right(Secret.Ref.Env(Secret.Ref.Env.Name("PORTAL_TOKEN")))
    )
    assertEquals(
      Secret.Ref.parse(Secret.Ref.ParseText(" keepassxc:Histmersion/Portal#password ")),
      Right(Secret.Ref.Vault(Secret.Ref.Vault.Value("keepassxc"), Secret.Ref.Vault.Ref("Histmersion/Portal#password")))
    )
    assertEquals(
      Secret.Ref.parse(Secret.Ref.ParseText("1password:op://Private/Portal/password")),
      Right(Secret.Ref.Vault(Secret.Ref.Vault.Value("1password"), Secret.Ref.Vault.Ref("op://Private/Portal/password")))
    )
    // a literal keeps its colons and its spaces inside
    assertEquals(
      Secret.Ref.parse(Secret.Ref.ParseText("value:a:b c")),
      Right(Secret.Ref.Value(Secret.Ref.Value.Literal("a:b c")))
    )

  test("a bare value, an empty part or a bad name is refused, and the error never echoes a value"):
    val bare = Secret.Ref.parse(Secret.Ref.ParseText("hunter2"))
    assert(bare.left.exists(_.sentence.contains("not a bare value")), bare)
    assert(!bare.left.exists(_.sentence.contains("hunter2")))
    assert(Secret.Ref.parse(Secret.Ref.ParseText("value:")).isLeft)
    assert(Secret.Ref.parse(Secret.Ref.ParseText("env:")).isLeft)
    assert(Secret.Ref.parse(Secret.Ref.ParseText("env:NOT-A-NAME")).isLeft)
    assert(Secret.Ref.parse(Secret.Ref.ParseText("keepassxc:")).isLeft)
    assert(Secret.Ref.parse(Secret.Ref.ParseText("Vault:x")).left.exists(_.sentence.contains("not a vault name")))
    assert(Secret.Ref.parse(Secret.Ref.ParseText(":x")).isLeft)
    assert(Secret.Ref.parse(Secret.Ref.ParseText(null)).isLeft)

  test("toString shows the reference, never a literal value"):
    assertEquals(
      Secret.parse(Secret.Ref.ParseText("env:PORTAL_TOKEN")).map(_.toString),
      Right("Secret(env:PORTAL_TOKEN)")
    )
    assertEquals(
      Secret.parse(Secret.Ref.ParseText("bitwarden:abc#password")).map(_.toString),
      Right("Secret(bitwarden:abc#password)")
    )
    val literal = Secret.parse(Secret.Ref.ParseText("value:hunter2")).toOption.get
    assertEquals(literal.toString, "Secret(value:***)")
    assert(!s"$literal ${Some(literal)} ${List(literal)}".contains("hunter2"))
    assertEquals(Secret.parse(Secret.Ref.ParseText("env:A")), Secret.parse(Secret.Ref.ParseText("env:A")))

  test("the reader parses references inside a derived config; numbers and booleans may be strings"):
    val c =
      ConfigFactory.parseString("token = \"env:PORTAL_TOKEN\"\npassword = null\nretries = \"3\"\nverbose = \"true\"")
    assertEquals(
      ConfigReader[WithSecrets].from(c.root),
      Right(WithSecrets(Secret(Secret.Ref.Env(Secret.Ref.Env.Name("PORTAL_TOKEN"))), None, 3, true))
    )
    val withPw = c.withValue("password", org.ekrich.config.ConfigValueFactory.fromAnyRef("keepassxc:a#b"))
    assertEquals(
      ConfigReader[WithSecrets].from(withPw.root).map(_.password),
      Right(Some(Secret(Secret.Ref.Vault(Secret.Ref.Vault.Value("keepassxc"), Secret.Ref.Vault.Ref("a#b")))))
    )

  test("a bare string where a secret belongs is a decoding error that does not repeat the value"):
    val c = ConfigFactory.parseString("token = hunter2\npassword = null\nretries = 1\nverbose = false")
    val r = ConfigReader[WithSecrets].from(c.root)
    assert(r.isLeft, r)
    assert(!r.left.toOption.get.toString.contains("hunter2"), r)

  test("a plugin's config type and reader are path-dependent members"):
    val p: Plugin = ExamplePlugin()
    val c = ConfigFactory.parseString("token = \"env:T\"\npassword = null\nretries = 2\nverbose = true")
    val typed: Either[?, p.Config] = p.configReader.from(c.root)
    assert(typed.isRight, typed)
    assertEquals(((p.defaultsResource).value: String), "META-INF/histmersion/plugins/example.conf")
