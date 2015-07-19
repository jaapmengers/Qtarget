package models

import models.Models._
import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID

object ModelFormats {
  import play.api.libs.json.Json
  import play.modules.reactivemongo.json.BSONFormats._

  implicit val mHitFormat = Json.format[Hit]
  implicit val mMissFormat = Json.format[Miss]
}

object Models {
  sealed trait Result {
    val _id: BSONObjectID
    val triggeredBy: String
    val shooter: String
    val timeout: Long
    val registeredOn: DateTime
  }
  case class Hit(_id: BSONObjectID, triggeredBy: String, shooter: String, timeout: Long, time: Long, registeredOn: DateTime) extends Result
  case class Miss(_id: BSONObjectID, triggeredBy: String, shooter: String, timeout: Long, registeredOn: DateTime) extends Result
}
