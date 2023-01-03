package fin.server.model

import java.time.Instant

final case class CategoryMapping(
  id: CategoryMapping.Id,
  condition: CategoryMapping.Condition,
  matcher: String,
  category: String,
  created: Instant,
  updated: Instant,
  removed: Option[Instant]
) {
  def matches(value: Option[String]): Boolean =
    value.exists { actualValue =>
      condition match {
        case CategoryMapping.Condition.StartsWith => actualValue.toLowerCase.startsWith(matcher.toLowerCase())
        case CategoryMapping.Condition.EndsWith   => actualValue.toLowerCase.endsWith(matcher.toLowerCase())
        case CategoryMapping.Condition.Contains   => actualValue.toLowerCase.contains(matcher.toLowerCase())
        case CategoryMapping.Condition.Equals     => actualValue.toLowerCase == matcher
        case CategoryMapping.Condition.Matches    => matcher.r.matches(actualValue)
      }
    }
}

object CategoryMapping {
  type Id = Int

  sealed trait Condition
  object Condition {
    case object StartsWith extends Condition {
      override def toString: String = "starts_with"
    }
    case object EndsWith extends Condition {
      override def toString: String = "ends_with"
    }
    case object Contains extends Condition {
      override def toString: String = "contains"
    }
    case object Equals extends Condition {
      override def toString: String = "equals"
    }
    case object Matches extends Condition {
      override def toString: String = "matches"
    }

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(condition: String): Condition =
      condition.trim.toLowerCase match {
        case "starts_with" => StartsWith
        case "ends_with"   => EndsWith
        case "contains"    => Contains
        case "equals"      => Equals
        case "matches"     => Matches
        case _             => throw new IllegalArgumentException(s"Unsupported condition provided: [$condition]")
      }
  }
}
