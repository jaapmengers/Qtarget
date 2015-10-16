package controllers

import akka.actor.{Cancellable, Actor, Props, ActorRef}
import akka.util.Timeout
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.mvc.Results._
import play.mvc.Controller
import play.api.Play.current
import scala.concurrent.duration._
import scala.util.Random

object ViewModelFormats {
  import play.api.libs.json.Json

  implicit val vmHitFormat = Json.format[Hit]
}

case object Up

sealed trait Result
case class Hit(time: Long) extends Result
case object Miss extends Result

case class Start(shooter: String)

case class ForwardedMessage(actorName: String, result: Result)
case class SetUp(actorName: String)

case object TimeUp

object Settings {
  implicit val timeout = Timeout(1 minute)
}

object MultiTarget extends Controller {

  import ViewModelFormats._

  val targetIds = List("BGIL5EnwW2wn", "KTnx1vmAO8ow", "pwMJfpHSlKDx")

  val targets = targetIds.map(x => x -> Akka.system.actorOf(TargetActor.props(x))).toMap

  val ca = Akka.system.actorOf(CommunicationActor.props(targets))
  val oa = Akka.system.actorOf(OrchestrationActor.props(targets.keys.toList, ca))


  def start(text: String) = Action { implicit request =>
    if(text.isEmpty){
      println("Hallo?")
      BadRequest("You forgot your name. E.g. /target John Doe")
    }
    else {
      oa ! Start(text)
      Ok("Ok!")
    }
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
}

class OrchestrationActor(targets: List[String], communicationActor: ActorRef) extends Actor {

  import context._

  var currentTargets: List[String] = Nil
  var currentResults: List[Result] = Nil
  var currentSender: Option[ActorRef] = None
  var currentShooter: String = ""

  override def receive: Receive = {
    case Start(shooter: String) => startTimeBound(sender(), shooter)
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

  def startTimeBound(sender: ActorRef, shooter: String): Unit = {

    currentShooter = shooter

    if(targets.nonEmpty){
      currentResults = Nil
      currentSender = Some(sender)

      Akka.system.scheduler.scheduleOnce(30 seconds){
        self ! TimeUp
      }

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

  private def getResultObject(results: List[Result]): JsObject = {
    val hits = results.collect{case x: Hit => x}
    if(hits.isEmpty)
      Json.obj("text" -> f"Yo $currentShooter, sadly you didn't hit anything!")
    else {
      val quickest = hits.map(_.time).min / 1000d
      Json.obj("text" -> f"Yo $currentShooter, you hit ${hits.length} targets and your quickest response time is $quickest seconds ! Far out!")
    }
  }

  def done(results: List[Result]) = {
    val data = getResultObject(results)
    println("Sending results to slack")
    for {
      _ <- WS.url("https://hooks.slack.com/services/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").post(data)
    } yield ()

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
  var cancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case Up =>
      println("Setting actor up")
      originalSender = Some(sender())

      WS.url(s"https://agent.electricimp.com/$targetid/up?text=&user_name=jaapm").get()
      println("Becoming isUp")

      // Schedule a miss if no result if > 10 seconds have passed and no result is received
      val c = system.scheduler.scheduleOnce(11 seconds) {
        self ! Miss
      }

      cancellable = Some(c)
      become(isUp)
  }

  def isUp: Receive = {
    case result: Result => originalSender.foreach { s =>
      println(s"Sending result $result to orchestrationactor $s")

      // Cancel any scheduled misses because we already received a result
      cancellable.foreach(_.cancel())

      s ! result
      become(receive)
    }
  }
}

object TargetActor {
  def props(targetid: String) = Props(new TargetActor(targetid))
}
