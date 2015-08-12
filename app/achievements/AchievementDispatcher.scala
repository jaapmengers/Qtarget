package achievements

import akka.actor.{Props, ActorRef}
import akka.persistence.PersistentActor
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
case class RankingResponse(ranking: Map[String, Long])
case class ForwardedStatsRequest(sender: ActorRef)
case class StatsResponse(bestTime: Long, hits: Int, misses: Int)

class AchievementDispatcher extends PersistentActor {

  val achievementCommunicator = context.actorOf(Props[AchievementCommunicator])

  var actors = Map.empty[String, ActorRef]
  var allResults: List[Result] = Nil

  def getRanking: RankingResponse = {

    def calculateRanking(l: List[Hit]) = {
      val results = for {
        (_, resultsPerShooter) <- l.groupBy(_.shooter).toList
      } yield resultsPerShooter.minBy(_.time)

      RankingResponse(results.sortBy(_.time).map(r => r.shooter -> r.time).toMap)
    }

    allResults.collect {case h: Hit => h} match {
      case Nil => RankingResponse(Map.empty)
      case l => calculateRanking(l)
    }
  }

  def getActor(shooter: String): ActorRef = {

    if(actors.contains(shooter))
      actors(shooter)
    else {
      val actor = context.actorOf(PersonalAchievementActor.props(shooter, achievementCommunicator))
      actors = actors + (shooter -> actor)
      actor ! ResultsSnapshot(allResults.filter(_.shooter == shooter))
      actor
    }
  }

  override def receiveRecover: Receive = {
    case res: Result => {
      allResults = allResults :+ res
    }
  }
  override def receiveCommand: Receive = {
    case res: Result => persist(res) { x =>
      getActor(res.shooter) ! x
      allResults = allResults :+ x
    }
    case sr: StatsRequest => getActor(sr.shooter) ! ForwardedStatsRequest(sender())
    case RankingRequest => sender() ! getRanking
  }

  def persistenceId: String = "achievement-dispatcher"
}
