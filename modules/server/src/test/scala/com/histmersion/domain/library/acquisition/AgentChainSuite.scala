package com.histmersion
package domain.library.acquisition

import com.histmersion.api.library.Ingest
import com.histmersion.config.{AcquireConfig, AppConfig}
import com.histmersion.util.problem.Problem
import org.ekrich.config.ConfigFactory

/** An agent's ingest asks the legal chain; asking for more needs a person's setting. */
class AgentChainSuite extends munit.FunSuite:

  private val legal = Ingest.Request.LegalOnly(true)
  private val more = Ingest.Request.LegalOnly(false)
  private val off = AcquireConfig.AgentsMayUseUnauthorised(false)
  private val on = AcquireConfig.AgentsMayUseUnauthorised(true)

  private def legalChain(chain: Either[Problem, Ingest.Request.LegalOnly]): Option[Boolean] =
    chain.toOption.map(_ == legal)

  test("the legal chain always goes ahead"):
    assertEquals(legalChain(AcquisitionControllers.agentChain(legal, off)), Some(true))
    assertEquals(legalChain(AcquisitionControllers.agentChain(legal, on)), Some(true))

  test("asking for more is refused by default, with the typed problem"):
    assertEquals(
      AcquisitionControllers.agentChain(more, off).left.toOption,
      Some(Problem.Refused.Policy(Problem.Refused.Rule.LegalOnly))
    )

  test("a person's setting lets an agent that asks for more have it"):
    assertEquals(legalChain(AcquisitionControllers.agentChain(more, on)), Some(false))

  test("both switches are off in the reference configuration"):
    val reference = AppConfig.load(ConfigFactory.empty)
    assert(reference.acquire.agentsMayUseUnauthorised == off)
    assert(reference.acquire.allowUnauthorised == AcquireConfig.AllowUnauthorised(false))
