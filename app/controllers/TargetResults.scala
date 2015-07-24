package controllers

import achievements._
import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import models.{Models, ViewModels}
import org.joda.time.DateTime
import play.api.libs.concurrent.Akka
import play.api.libs.ws.WS
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.{ReadPreference}
import reactivemongo.bson.{BSONString, BSONDocument, BSONObjectID}
import reactivemongo.core.commands.Group
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.json._, ImplicitBSONHandlers._
import play.modules.reactivemongo.json.BSONFormats._
import play.api.mvc.{Result, BodyParsers, Action, Controller}
import play.modules.reactivemongo.{MongoController}
import scala.util.Try
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import models.ViewModelFormats._
import models.ModelFormats._
import akka.pattern.ask

object TargetResults extends Controller with MongoController {

  implicit val timeout = 10.seconds
  implicit val akka_timeout = Timeout(20 seconds)

  val dispatcher = Akka.system.actorOf(Props[AchievementDispatcher])

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
      "shooter" -> (if(shooter == "") user_name else shooter)
    )

    Logger.info(s"Sending to imp: ${data.toString}")

    for {
      x <- WS.url("https://agent.electricimp.com/Cn2ir4ebkp5f/up").post(data)
    } yield {
      Logger.info(s"Response from imp: ${x.body}")
      Ok
    }
  }

  private def msToS(ms: Long) = {
    "%1.2f" format ms / 1000f
  }

  def stats(text: String) = Action.async { implicit request =>

    def pad(s1: String, s2: String) = s1.padTo(50, " ").mkString + s2

    text.trim match {
      case "" => Future.successful(BadRequest("Geef een naam mee"))
      case shooter: String => for {
        x <- (dispatcher ? StatsRequest(shooter)).mapTo[StatsResponse]
        bestTime = pad("Best time:", if(x.bestTime == 0) "n/a" else msToS(x.bestTime) + "s")
        hits = pad("Hits:", x.hits.toString)
        misses = pad("Misses:", x.misses.toString)
      } yield Ok(
        s"""```
           |Stats for $shooter
           |$bestTime
           |$hits
           |$misses
           |```""".stripMargin
        )
    }
  }

  def ranking = Action.async { implicit request =>
    def makeResult(t: (Models.Hit, Int)) = {
      s"${t._2 + 1}. ${t._1.shooter}".padTo(50, " ").mkString + s"${msToS(t._1.time)}s"
    }

    for {
      h <- hits
            .genericQueryBuilder
            .cursor[Models.Hit](ReadPreference.primary)
            .collect[List]()
      grouped = h
        .groupBy(_.shooter)
        .map(_._2.minBy(_.time))
        .toList.sortBy(_.time)
    } yield Ok(s"""```
        |Current ranking
        |${grouped.zipWithIndex.map(makeResult).mkString(s"\n")}
        |```""".stripMargin)
  }

  def ping = Action { implicit request =>
    dispatcher ! Print("@jaapm")
    Ok
  }

  def boom = Action { implicit request =>
    dispatcher ! Boom("@jaapm")
    Ok
  }

  def hit = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[ViewModels.Hit]

    dispatcher ! achievements.Hit(v.shooter, v.time)

    val shooter = if(v.shooter == "") v.triggeredBy else v.shooter

    for {
      x <- hits.insert(Models.Hit(BSONObjectID.generate, v.triggeredBy, shooter.stripPrefix("@"), v.timeout, v.time, DateTime.now))
    } yield Ok
  }

  def miss = Action.async(BodyParsers.parse.json) { implicit request =>
    val v = (request.body).as[ViewModels.Miss]

    dispatcher ! achievements.Miss(v.shooter)

    val shooter = if(v.shooter == "") v.triggeredBy else v.shooter

    for {
      x <- misses.insert(Models.Miss(BSONObjectID.generate, v.triggeredBy, shooter.stripPrefix("@"), v.timeout, DateTime.now))
    } yield Ok
  }

}