package controllers

import akka.actor.Actor.Receive
import akka.pattern.{ ask }
import akka.actor.{Actor, Props, ActorRef}
import akka.util.Timeout
import play.api.libs.concurrent.Akka
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.mvc.Results._
import play.mvc.Controller
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


object ViewModelFormats {
  import play.api.libs.json.Json

  implicit val vmHitFormat = Json.format[Hit]
}

case object Up
trait Result
case class Hit(time: Long) extends Result
case object Miss extends Result

case class ForwardedMessage(actorName: String, result: Result)

object BrowserTarget extends Controller {

  import ViewModelFormats._

  val targets = Map(
    "een" ->  Akka.system.actorOf(TargetActor.props("77Voox93zEaE"))
  )

  val ca = Akka.system.actorOf(CommunicationActor.props(targets))

  implicit val timeout = Timeout(20 seconds)


  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def up = Action.async { implicit request =>
    val resultF = (targets.head._2 ? Up).mapTo[Result]

    for {
      result <- resultF
    } yield {
      println(s"Result was $result")
      Redirect(routes.BrowserTarget.index)
    }
  }

  def hit = Action(BodyParsers.parse.json) { implicit request =>
    val v = request.body.as[Hit]

    ca ! ForwardedMessage("een", v)
    Ok
  }

  def miss = Action { implicit request =>
    ca ! ForwardedMessage("een", Miss)
    Ok
  }
}


class CommunicationActor(targets: Map[String, ActorRef]) extends Actor {
  override def receive: Receive = {
    case ForwardedMessage(actorName, result) =>
      for {
        target <- targets.get(actorName)
      } yield target ! result
  }
}

object CommunicationActor {
  def props(targets: Map[String, ActorRef]) = Props(new CommunicationActor(targets))
}

class TargetActor(targetid: String) extends Actor {

  var originalSender: Option[ActorRef] = None

  override def receive: Receive = {
    case Up =>
      originalSender = Some(sender())
      for { x <-  WS.url(s"https://agent.electricimp.com/$targetid/up?text=&user_name=jaapm").get() } yield ()
    case result: Result => originalSender match {
      case Some(s: ActorRef) => s ! result
      case _ => //
    }
  }
}

object TargetActor {
  def props(targetid: String) = Props(new TargetActor(targetid))
}
