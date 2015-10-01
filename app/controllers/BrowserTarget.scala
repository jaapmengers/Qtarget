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
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}


object ViewModelFormats {
  import play.api.libs.json.Json

  implicit val vmHitFormat = Json.format[Hit]
}

case object Up
trait Result
case class Hit(time: Long) extends Result
case object Miss extends Result

case object Start

case class ForwardedMessage(actorName: String, result: Result)
case class SetUp(actorName: String)

object Settings {
  implicit val timeout = Timeout(5 minutes)
}

object BrowserTarget extends Controller {

  import ViewModelFormats._
  import Settings._


  val targets = Map(
    "een" ->  Akka.system.actorOf(TargetActor.props("77Voox93zEaE")),
    "twee" ->  Akka.system.actorOf(TargetActor.props("77Voox93zEaE")),
    "drie" ->  Akka.system.actorOf(TargetActor.props("77Voox93zEaE"))
  )

  val ca = Akka.system.actorOf(CommunicationActor.props(targets))
  val oa = Akka.system.actorOf(OrchestrationActor.props(targets.keys.toList, ca))

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def start = Action.async { implicit request =>
    for {
      results <- (oa ? Start).mapTo[List[Result]]
    } yield {
      println(s"Results: $results")
      Redirect(routes.BrowserTarget.index)
    }
  }

  def hit = Action(BodyParsers.parse.json) { implicit request =>
    val v = request.body.as[Hit]

    // Todo, this should be dynamic based on the code of the sending imp
    ca ! ForwardedMessage("een", v)
    Ok
  }

  def miss = Action { implicit request =>
    ca ! ForwardedMessage("een", Miss)
    Ok
  }
}

class OrchestrationActor(targets: List[String], communicationActor: ActorRef) extends Actor {

  import Settings._

  var currentTargets: List[String] = Nil
  var currentResults: List[Result] = Nil
  var currentSender: Option[ActorRef] = None

  override def receive: Receive = {
    case Start =>
      if(targets.nonEmpty) {
        currentResults = Nil
        currentSender = Some(sender())
        currentTargets = sendNext(targets)
      } else {
        done(currentResults)
      }

    case res: Result =>
      currentResults = currentResults :+ res

      if(currentTargets.isEmpty)
        done(currentResults)
      else
        currentTargets = sendNext(currentTargets)
  }

  def done(results: List[Result]) = {
    currentSender.foreach(x => x ! results)
  }

  def sendNext(ts: List[String]): List[String] = {
    ts.headOption.foreach(t => communicationActor ! SetUp(t))
    ts.tail
  }
}

object OrchestrationActor {
  def props(targets: List[String], communicationActor: ActorRef) = Props(new OrchestrationActor(targets, communicationActor))
}


class CommunicationActor(targets: Map[String, ActorRef]) extends Actor {
  override def receive: Receive = {
    case ForwardedMessage(actorName, result) =>
      for {
        target <- targets.get(actorName)
      } yield target ! result
    case SetUp(actorName) =>
      println(s"Searching for actor $actorName")
      targets.get(actorName).foreach(_ forward Up)
  }
}

object CommunicationActor {
  def props(targets: Map[String, ActorRef]) = Props(new CommunicationActor(targets))
}

class TargetActor(targetid: String) extends Actor {

  var originalSender: Option[ActorRef] = None

  override def receive: Receive = {
    case Up =>
      println("Setting actor up")
      originalSender = Some(sender())
      for { x <-  WS.url(s"https://agent.electricimp.com/$targetid/up?text=&user_name=jaapm").get() } yield ()
    case result: Result => originalSender match {
      case Some(s: ActorRef) => {
        println(s"Sending result $result to orchestrationactor $s")
        s ! result
      }
      case _ => //
    }
  }
}

object TargetActor {
  def props(targetid: String) = Props(new TargetActor(targetid))
}
