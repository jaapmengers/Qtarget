package achievements

import akka.actor.{Props, ActorRef, Actor}
import org.joda.time.DateTime

sealed trait Result {
  val shooter: String
  val timestamp: DateTime
}
case class Hit(shooter: String, time: Long, timestamp: DateTime) extends Result
case class Miss(shooter: String, timestamp: DateTime) extends Result
case class Print(shooter: String)
case class Boom(shooter: String)

case class StatsRequest(shooter: String)
case object RankingRequest
case class ForwardedRankingRequest(sender: ActorRef)
case class ForwardedStatsRequest(sender: ActorRef)
case class StatsResponse(bestTime: Long, hits: Int, misses: Int)

class AchievementDispatcher extends Actor {

  val achievementCommunicator = context.actorOf(Props[AchievementCommunicator])
  val overallAchievementActor = context.actorOf(Props[OverallAchievementActor])

  var actors = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case res: Result => getActor(res.shooter) ! res
    case sr: StatsRequest => getActor(sr.shooter) ! ForwardedStatsRequest(sender())
    case RankingRequest => overallAchievementActor ! ForwardedRankingRequest(sender())
  }

  def getActor(shooter: String): ActorRef = {
    if(actors.contains(shooter))
      actors(shooter)
    else {
      val actor = context.actorOf(PersonalAchievementActor.props(shooter, achievementCommunicator, overallAchievementActor))
      actors = actors + (shooter -> actor)
      actor
    }
  }
}
