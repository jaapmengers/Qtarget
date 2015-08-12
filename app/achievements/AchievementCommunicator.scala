package achievements

import akka.persistence.PersistentActor
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

class AchievementCommunicator extends PersistentActor {

  var state = Set.empty[(String, String)]

  override def receiveRecover: Receive = {
    case x: Achievement => {
      println(s"receiveRecover from communicator ${x.toString}}")
      state = state + ((x.shooter, x.achievement))
    }
  }

  override def receiveCommand: Receive = {
    case x: Achievement if !state.contains((x.shooter, x.achievement)) => persist(x){ y =>
      println(s"Sending achievement: $y")

      val data = Json.obj(
        "text" -> s"${y.shooter} unlocked an achievement! ${y.achievement}"
      )

      for {
        _ <- WS.url("https://hooks.slack.com/services/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").post(data)
      } yield {
        state = state + ((y.shooter, y.achievement))
      }
    }
  }

  override def persistenceId: String = "achievementCommunicator"
}
