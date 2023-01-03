package fin.server.service

import com.typesafe.config.{Config, ConfigFactory}
import fin.server.UnitSpec

class ServiceModeSpec extends UnitSpec {
  "A ServiceMode" should "support loading modes from config" in {
    ServiceMode(config.getConfig("dev")) should be(ServiceMode.Development(resourcesPath = "/a/b/c"))
    ServiceMode(config.getConfig("development")) should be(ServiceMode.Development(resourcesPath = "/x/y/z"))
    ServiceMode(config.getConfig("prod")) should be(ServiceMode.Production)
    ServiceMode(config.getConfig("production")) should be(ServiceMode.Production)
    an[IllegalArgumentException] should be thrownBy ServiceMode(config.getConfig("other"))
  }

  it should "render itself as a string" in {
    ServiceMode.Development(resourcesPath = "/test/a/b/c").toString should be("Development(resourcesPath=/test/a/b/c)")
    ServiceMode.Production.toString should be("Production")
  }

  private val config: Config = ConfigFactory.load().getConfig("fin.test.server.service.modes")
}
