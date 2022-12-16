package fin.server.security

final case class CurrentUser(subject: String) {
  override def toString: String = subject
}
