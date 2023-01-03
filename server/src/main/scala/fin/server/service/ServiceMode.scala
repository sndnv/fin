package fin.server.service

sealed trait ServiceMode

object ServiceMode {
  final case class Development(resourcesPath: String) extends ServiceMode {
    override def toString: String = s"Development(resourcesPath=$resourcesPath)"
  }

  case object Production extends ServiceMode {
    override def toString: String = "Production"
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(config: com.typesafe.config.Config): ServiceMode = config.getString("mode").trim.toLowerCase match {
    case "dev" | "development" => Development(resourcesPath = config.getString("development.resources-path"))
    case "prod" | "production" => Production
    case other                 => throw new IllegalArgumentException(s"Unexpected service mode provided: [$other]")
  }
}
