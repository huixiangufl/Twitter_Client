package common

import spray.json.DefaultJsonProtocol

case class Tweet(user_id: Int, text: String, time_stamp: String, var ref_id: String)
case class DirectMessage(sender_id: Int, receiver_id: Double, text: String, time_stamp: String, var ref_id: String)
case class FollowerNum(var userID: Int, var numFollowers: Int)

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val followerNumFormat = jsonFormat2(FollowerNum)
  implicit val tweetFormat = jsonFormat4(Tweet)
  implicit val messageFormat = jsonFormat5(DirectMessage)
}


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
