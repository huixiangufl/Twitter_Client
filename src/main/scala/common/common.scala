package common
import java.util.Date
import scala.collection.mutable.ArrayBuffer


sealed trait Message
case object GetNumofServerWorkers extends Message
case class numOfServerWorkers(num: Int)
case object ClientBossReady extends Message
case object ServerActorWantYouWork extends Message

case object IsServerBossReady extends Message
case object ServerReady extends Message

case class numFollowers(user_id: Int)
case class followers_num(num: Int)
case class viewHomeTimeline(user_id: Int)
case class viewUserTimeline(user_id: Int)
case class getTweet(t: Tweet)
case class displayUserTimeLine(userTimeLine: ArrayBuffer[String])
case class displayHomeTimeLine(HomeTimeLine: ArrayBuffer[String])
case class Tweet(user_id: Int, text: String, time_stamp: String, var ref_id: String)
case class DirectMessage(sender_id: Int, receiver_id: Double, text: String, time_stamp: String, var ref_id: String)

//Advanced API Implementation
//case class getMentionTimeline(user_id: Int)
//
////Tweet API
//case class getMyRetweet(user_id: Int)
//case class postRetweet(user_id: Int, ref_id: String)
//case class getOthersRetweet(user_id: Int)
//
////Direct Message API
//case class getDirectMessage(user_id: Int)
//case class getDirectMessageSent(user_id: Int)
//case class getDirectMessageShow()
//case class postDirectMessage()
//
////Friends and Followers API
//case class getFriends()
//case class getFollowers()
//case class getIncomingFriends() ? not implemented?
//case class gerOutgoingFriends() ? not implemented?
//case class getFriendShow() ? not implemented
//case class postCreateFriend()
//case class postDestroyFriend()
//case class postUpdateFriend() ? not implemented?
