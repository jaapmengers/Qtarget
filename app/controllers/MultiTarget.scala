package controllers

import akka.pattern.ask
import akka.actor.{Actor, Props, ActorRef}
import akka.util.Timeout
import play.api.libs.concurrent.Akka
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.mvc.Results._
import play.mvc.Controller
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import upickle.default._

object ViewModelFormats {
  import play.api.libs.json.Json

  implicit val vmHitFormat = Json.format[Hit]
}

case object Up

sealed trait Result
case class Hit(time: Long) extends Result
case object Miss extends Result

case object Start

case class ForwardedMessage(actorName: String, result: Result)
case class SetUp(actorName: String)

case object TimeUp

object Settings {
  implicit val timeout = Timeout(5 minutes)
}

object MultiTarget extends Controller {

  import ViewModelFormats._

  val targetIds = List("BGIL5EnwW2wn", "KTnx1vmAO8ow", "pwMJfpHSlKDx")

  val targets = targetIds.map(x => x -> Akka.system.actorOf(TargetActor.props(x))).toMap

  val ca = Akka.system.actorOf(CommunicationActor.props(targets))
  val oa = Akka.system.actorOf(OrchestrationActor.props(targets.keys.toList, ca))

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def hit(id: String) = Action(BodyParsers.parse.json) { implicit request =>
    val v = request.body.as[Hit]

    ca ! ForwardedMessage(id, v)
    Ok
  }

  def miss(id: String) = Action { implicit request =>
    ca ! ForwardedMessage(id, Miss)
    Ok
  }

  def socket = WebSocket.acceptWithActor[String, String] { request => out =>
    SocketActor.props(out, oa)
  }
}


object SocketActor {
  def props(out: ActorRef, orchestrationActor: ActorRef) = Props(new SocketActor(out, orchestrationActor))
}

class SocketActor(out: ActorRef, oa: ActorRef) extends Actor {
  override def receive: Receive = {
    case "START" =>
      import Settings._

      for {
        results <- (oa ? Start).mapTo[List[Result]]
        js = write(results)
      } yield {
        println(s"Results: $js")
        out ! js
      }
  }
}

class OrchestrationActor(targets: List[String], communicationActor: ActorRef) extends Actor {

  import Settings._
  import context._

  var currentTargets: List[String] = Nil
  var currentResults: List[Result] = Nil
  var currentSender: Option[ActorRef] = None

  override def receive: Receive = {
    case Start => startTimeBound(sender())
  }

  def startSequential(sender: ActorRef): Unit = {
    if(targets.nonEmpty) {
      currentResults = Nil
      currentSender = Some(sender)
      currentTargets = sendNext(targets)
      become(inProgress)
    } else
      done(currentResults)
  }

  def startTimeBound(sender: ActorRef): Unit = {
    if(targets.nonEmpty){
      currentResults = Nil
      currentSender = Some(sender)

      Akka.system.scheduler.scheduleOnce(30 seconds){
        self ! TimeUp
      }(Akka.system.dispatcher)

      become(inProgressTimeBound)
      sendNextTimeBound()

    } else
      done(currentResults)
  }

  def inProgressTimeBound: Receive = {
    case res: Result =>
      currentResults = currentResults :+ res
      sendNextTimeBound()
    case TimeUp => become(timeUp)
  }

  def timeUp: Receive = {
    case res: Result =>
      currentResults = currentResults :+ res
      done(currentResults)
  }

  def sendNextTimeBound() = {
    val shuffled = Random.shuffle(targets)
    communicationActor ! SetUp(shuffled.head)
  }

  def inProgress: Receive = {
    case res: Result =>
      currentResults = currentResults :+ res

      if(currentTargets.isEmpty) {
        done(currentResults)
        become(receive)
      }
      else
        currentTargets = sendNext(currentTargets)
  }

  def done(results: List[Result]) = {
    currentSender.foreach(x => x ! results)
    become(receive)
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
      targets.get(actorName).foreach(_ ! result)
    case SetUp(actorName) =>
      println(s"Searching for actor $actorName")
      targets.get(actorName).foreach(_ forward Up)
  }
}

object CommunicationActor {
  def props(targets: Map[String, ActorRef]) = Props(new CommunicationActor(targets))
}

class TargetActor(targetid: String) extends Actor {

  import context._

  var originalSender: Option[ActorRef] = None

  override def receive: Receive = {
    case Up =>
      println("Setting actor up")
      originalSender = Some(sender())

      WS.url(s"https://agent.electricimp.com/$targetid/up?text=&user_name=jaapm").get()
      println("Becoming isUp")

      become(isUp)
  }

  def isUp: Receive = {
    case result: Result => originalSender.foreach { s =>
      println(s"Sending result $result to orchestrationactor $s")
      s ! result
      become(receive)
    }
  }
}

object TargetActor {
  def props(targetid: String) = Props(new TargetActor(targetid))
}
