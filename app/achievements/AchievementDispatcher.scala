package achievements

import akka.actor.{Props, ActorRef, Actor}

sealed trait Result {
  val shooter: String
}
case class Hit(shooter: String, time: Long) extends Result
case class Miss(shooter: String) extends Result
case class Print(shooter: String)
case class Boom(shooter: String)

case class StatsRequest(shooter: String)
case class ForwardedStatsRequest(sender: ActorRef)
case class StatsResponse(bestTime: Long, hits: Int, misses: Int)

class AchievementDispatcher extends Actor {

  val achievementCommunicator = context.actorOf(Props[AchievementCommunicator])

  var actors = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case res: Result => getActor(res.shooter) ! res
    case sr: StatsRequest => getActor(sr.shooter) ! ForwardedStatsRequest(sender())
  }

  def getActor(shooter: String): ActorRef = {
    if(actors.contains(shooter))
    {
      println("Actor found")
      actors(shooter)
    }

    else {
      println(actors.map(_._1).toString)
      println(s"$shooter")
      val actor = context.actorOf(PersonalAchievementActor.props(shooter, achievementCommunicator))
      actors = actors + (shooter -> actor)
      actor
    }
  }
}
