package fin.server.api.requests

import fin.server.model.Period

final case class ApplyCategoryMappings(
  forPeriod: Period,
  overrideExisting: Boolean
)
