package achievements

import akka.actor.{ActorRef, Actor}

sealed trait Result {
  val shooter: String
}
case class Hit(shooter: String, time: Long) extends Result
case class Miss(shooter: String) extends Result
case class Print(shooter: String)
case class Boom(shooter: String)

class AchievementDispatcher extends Actor {

  var actors = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case res: Result => {
      getActor(res.shooter) ! res
    }
    case Print(s: String) => {
      getActor(s) ! "print"
    }
    case Boom(s: String) => {
      getActor(s) ! "boom"
    }
  }

  def getActor(shooter: String): ActorRef = {
    if(actors.contains(shooter))
      actors(shooter)
    else {
      val actor = context.actorOf(PersonalAchievementActor.props(shooter))
      actors = actors + (shooter -> actor)
      actor
    }
  }
}
