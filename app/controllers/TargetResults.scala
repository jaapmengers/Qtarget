package controllers

import models.{Models, ViewModels}
import org.joda.time.DateTime
import play.api.libs.ws.WS
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.{ReadPreference}
import reactivemongo.bson.{BSONString, BSONDocument, BSONObjectID}
import reactivemongo.core.commands.Group
import scala.concurrent.duration.DurationInt
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.json._, ImplicitBSONHandlers._
import play.modules.reactivemongo.json.BSONFormats._
import play.api.mvc.{BodyParsers, Action, Controller}
import play.modules.reactivemongo.{MongoController}
import scala.util.Try
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import models.ViewModelFormats._
import models.ModelFormats._

object TargetResults extends Controller with MongoController {

  implicit val timeout = 10.seconds

  def hits: JSONCollection = db.collection[JSONCollection]("hits")
  def misses: JSONCollection = db.collection[JSONCollection]("misses")

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

  def ranking = Action.async { implicit request =>
    def makeResult(t: (Models.Hit, Int)) = {
      val timeInSecondString = "%1.2f" format t._1.time / 1000f
      s"${t._2 + 1}. ${t._1.shooter}".padTo(50, " ").mkString + s"${timeInSecondString}s"
    }

    for {
      x <- hits
            .genericQueryBuilder
            .cursor[Models.Hit](ReadPreference.primary)
            .collect[List]()
      grouped = x.groupBy(_.shooter).map(_._2.head).toList.sortBy(_.time)
    } yield Ok(s"""```
        |Current ranking
        |${grouped.zipWithIndex.map(makeResult).mkString(s"\n")}
        |```""".stripMargin)
  }

  def hit = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[ViewModels.Hit]

    for {
      x <- hits.insert(Models.Hit(BSONObjectID.generate, v.triggeredBy, v.shooter.stripPrefix("@"), v.timeout, v.time, DateTime.now))
    } yield {
      Logger.info(x.message)
      Ok
    }
  }

  def miss = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[ViewModels.Miss]

    for {
      x <- misses.insert(Models.Miss(BSONObjectID.generate, v.triggeredBy, v.shooter.stripPrefix("@"), v.timeout, DateTime.now))
    } yield {
      Logger.info(x.message)
      Ok
    }
  }

}