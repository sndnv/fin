package fin.server.telemetry

import fin.server.UnitSpec
import fin.server.api.{Metrics => ApiMetrics}
import fin.server.telemetry.mocks.MockApiMetrics

class DefaultTelemetryContextSpec extends UnitSpec {
  "A DefaultTelemetryContext" should "provide metrics" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint()
      )
    )

    noException should be thrownBy context.metrics[ApiMetrics.Endpoint]
  }

  it should "fail if a requested provider is not available" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set()
    )

    an[IllegalStateException] should be thrownBy context.metrics[ApiMetrics.Endpoint]
  }
}
