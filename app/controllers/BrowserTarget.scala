package controllers

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props, ActorRef}
import play.api.libs.concurrent.Akka
import play.api.libs.json.{Json, JsValue}
import play.api.mvc._
import play.api.mvc.Results._
import play.mvc.Controller
import play.api.Play.current

object BrowserTarget extends Controller {

  val oa = Akka.system.actorOf(Props[OrchestrationActor])

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def socket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    BrowserTargetActor.props(out, oa)
  }
}

object BrowserTargetActor {
  def props(out: ActorRef, orchestrationActor: ActorRef) = Props(new BrowserTargetActor(out, orchestrationActor))
}

case object Register

class BrowserTargetActor(out: ActorRef, orchestrationActor: ActorRef) extends Actor {

  override def preStart() = {
    orchestrationActor ! Register
  }

  def receive = {
    case msg: JsValue => {
      out !  Json.obj("text" -> s"I received your message: ${(msg \ "text").as[String]}")
    }
  }
}

class OrchestrationActor extends Actor {
  import context._

  var targets = Map.empty[String, ActorRef]

  def instantiate(socket: ActorRef) = {

    val targetNames = Set("een", "twee", "drie")
    targets = targetNames.map(x => x -> context.actorOf(TargetActor.props(x))).toMap

    socket ! Json.obj("targets" -> targetNames)

    become(connected)
  }

  override def receive: Receive = {
    case Register =>
      instantiate(sender())
  }

  def connected: Receive = {
    case _ => //
  }
}

class TargetActor(targetid: String) extends Actor {
  override def receive: Receive = {
    case _ => //
  }
}

object TargetActor {
  def props(targetid: String) = Props(new TargetActor(targetid))
}
