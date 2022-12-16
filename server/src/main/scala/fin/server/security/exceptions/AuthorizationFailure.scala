package fin.server.security.exceptions

final case class AuthorizationFailure(override val message: String) extends SecurityFailure(message)
