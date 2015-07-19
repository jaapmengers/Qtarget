package models

import models.ViewModels._
import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID

object ViewModels {
  case class Hit(triggeredBy: String, shooter: String, timeout: Long, time: Double)
  case class Miss(triggeredBy: String, shooter: String, timeout: Long)
}

object ViewModelFormats {
  import play.api.libs.json.Json
  import play.modules.reactivemongo.json.BSONFormats._

  implicit val vmHitFormat = Json.format[Hit]
  implicit val vmMissFormat = Json.format[Miss]

}
