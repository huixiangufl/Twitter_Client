package common
import java.util.Date
import scala.collection.mutable.ArrayBuffer

sealed trait Message
case object GetNumofServerWorkers extends Message
case class numOfServerWorkers(num: Int)

case class numFollowers(user_id: Int)
case class followers_num(num: Int)
case class viewHomeTimeline(user_id: Int)
case class viewUserTimeline(user_id: Int)
case class getTweet(t: Tweet)
case class displayUserTimeLine(userTimeLine: ArrayBuffer[String])
case class displayHomeTimeLine(HomeTimeLine: ArrayBuffer[String])
case class Tweet(user_id: Long, text: String, time_stamp: Date, var ref_id: String)