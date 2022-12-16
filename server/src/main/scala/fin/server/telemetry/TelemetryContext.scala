package fin.server.telemetry

import fin.server.telemetry.metrics.MetricsProvider

import scala.reflect.ClassTag

trait TelemetryContext {
  def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M
}
