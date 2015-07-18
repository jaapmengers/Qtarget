package controllers

import controllers.Requests._
import play.api.libs.ws.WS
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration.DurationInt
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.functional.syntax._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{BodyParsers, Action, Controller}
import play.modules.reactivemongo.{MongoController}
import scala.util.Try
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global


object JsonFormats {
  import play.api.libs.json.Json
  import play.modules.reactivemongo.json.BSONFormats._

  implicit val hitFormat = Json.format[Hit]
  implicit val missFormat = Json.format[Miss]
}

object Requests {
  sealed trait Result{
    val triggeredBy: String
    val shooter: String
    val timeout: Long
  }

  case class Hit(_id: Option[BSONObjectID], triggeredBy: String, shooter: String, timeout: Long, time: Double) extends Result
  case class Miss(_id: Option[BSONObjectID], triggeredBy: String, shooter: String, timeout: Long) extends Result
}

object TargetResults extends Controller with MongoController {

  implicit val timeout = 10.seconds
  import JsonFormats._

  def collection: JSONCollection = db.collection[JSONCollection]("results")

  def up(channel_name: String, user_id: String, user_name: String, text: String) = Action.async { implicit request =>

    val (timeout, shooter) = text.split(' ').take(2) match {
      case Array(x: String, y: String) if Try(x.toInt).isSuccess => (x.toInt, y)
      case Array(x: String, y: String) if Try(y.toInt).isSuccess => (y.toInt, x)
      case Array(x: String) if Try(x.toInt).isSuccess => (x.toInt, user_name)
      case Array(x: String) => (10, x)
      case _ => (10, user_name)
    }

    val data = Json.obj(
      "timeout" -> timeout,
      "triggeredBy" -> user_name,
      "shooter" -> shooter
    )

    Logger.info(s"Sending to imp: ${data.toString}")

    for {
      x <- WS.url("https://agent.electricimp.com/Cn2ir4ebkp5f/up").post(data)
    } yield {
      Logger.info(s"Response from imp: ${x.body}")
      Ok
    }
  }

  def hit = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[Requests.Hit]

    for {
      x <- collection.insert(v)
    } yield {
      Logger.info(x.message)
      Ok
    }
  }

  def miss = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[Requests.Miss]

    for {
      x <- collection.insert(v)
    } yield {
      Logger.info(x.message)
      Ok
    }
  }

}