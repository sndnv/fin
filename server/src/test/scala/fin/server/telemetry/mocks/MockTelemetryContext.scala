package fin.server.telemetry.mocks

import fin.server.telemetry.metrics.MetricsProvider
import fin.server.telemetry.{DefaultTelemetryContext, TelemetryContext}

import scala.reflect.ClassTag

class MockTelemetryContext extends TelemetryContext {
  private lazy val underlying = DefaultTelemetryContext(
    metricsProviders = Set(api.endpoint)
  )

  object api {
    val endpoint: MockApiMetrics.Endpoint = MockApiMetrics.Endpoint()
  }

  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M = underlying.metrics[M](tag)
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext = new MockTelemetryContext()
}
