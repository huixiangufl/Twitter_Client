package twitterclient

import spray.http._
import spray.client.pipelining._
import akka.actor.{ActorSystem, Actor, Props, ActorRef}
import akka.actor._
import java.security.MessageDigest
import java.util.Formatter
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import spray.json._

import akka.actor.ActorSystem
import spray.json.{JsonFormat, DefaultJsonProtocol}
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import common._
import MyJsonProtocol._



object utility {
   def getHash(s: String): String = {
    MessageDigest.getInstance("SHA-256").digest(s.getBytes)
      .foldLeft("")((s: String, b: Byte) => s +
      Character.forDigit((b & 0xf0) >> 4, 16) +
      Character.forDigit(b & 0x0f, 16))
  }

  def dateToString(current: Date): String = {
    val formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS")
    val s: String = formatter.format(current)
    return s
  }

  def getCurrentTime(): Date = {
    Calendar.getInstance().getTime()
  }

  def genRandCharater(): Char = {
    ('a' to 'z')(util.Random.nextInt(26))
  }

  def genRandTweet(): String = {
    genRandCharater() + "a" * (0 + Random.nextInt(139))
  }
}


object twitterclient extends App {
  sealed trait Message
  case object GetNumOfFollowers extends Message
  case object SendTweet extends Message
  case class MentionTweet (mentionID: Int) extends Message
  case object ViewHomeTimeline extends Message
  case object ViewUserTimeline extends Message
  case object GetMentionTimeline extends Message
  case object GetFriends extends Message
  case object GetFollowers extends Message
  case class CreateFriendship (newFriend: Double) extends Message
  case class DestroyFriendship (oldFriend: Double) extends Message
  case class DestroyTweet(deleteTweet: Double) extends Message
  case class PostDirectMessage(receiverID: Double) extends Message
  case class PostDestroyMessage(delID: Double) extends Message
  case object ViewReceiveMessage extends Message
  case object ViewSendMessage extends Message

  var numClientWorkers: Int = 10000
  var firstClientID: Int = 0
  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer
  var maxNumOfFollowers = 100001
  var T = 0.5

  var serverIP: String = "10.227.56.44:8080"

  implicit val system = ActorSystem("UserSystem")
  import system.dispatcher

  import utility._
  /*define various pipelines globally*/
  import SprayJsonSupport._
//  import TweetProtocol._
  val pipeline = sendReceive
  val postTweetPipeline = sendReceive
  val getNumPipeline = sendReceive ~> unmarshal[String]
  val followerPipeline = sendReceive ~> unmarshal[FollowerNum]
  val directMessagesPipeline = sendReceive ~> unmarshal[List[DirectMessage]]
  val timelinePipeline = sendReceive ~> unmarshal[List[Tweet]]
  val arrayPipeline = sendReceive ~> unmarshal[Array[Int]]


  //mentionID: -1 for default tweet message, other wise the user the tweet message mentions
  def postTweet(t: Tweet, mentionID: Int) {
    postTweetPipeline(Post("http://" + serverIP + "/postTweet?userID=" + t.user_id + "&mentionID=" + mentionID + "&text=" + t.text + "&timeStamp=" + t.time_stamp + "&refID=" + t.ref_id))
  }


  def postDirectMessage(d: DirectMessage) {
    postTweetPipeline(Post("http://" + serverIP + "/postMessage?userID=" + d.sender_id + "&sendID=" + d.receiver_id + "&text=" + d.text + "&timeStamp=" + d.time_stamp + "&refID=" + d.ref_id))
  }


  def forwardTweet(userID: Int, tweet: Tweet) {
    val t = Tweet(userID, "@" + tweet.user_id.toString + ":" + tweet.text, dateToString(getCurrentTime), null)
    t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
    postTweet(t, -1)
    println("@@@@@@@@@@@@@" + t.user_id + " forwards the tweet: " + t)
  }

  class clientWorkerActor( ) extends Actor{
    val userID = self.path.name.toInt
    def receive = {
      case SendTweet => {
        val t = Tweet(userID, genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
        postTweet(t, -1)
        println(userID + " sends tweet: " + t)
      }
      case MentionTweet(mentionID) => {
        val t = Tweet(userID, "@" + mentionID.toString + genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
        postTweet(t, mentionID)
        println(userID + " mentions tweet: " + t)
      }
      case PostDirectMessage(receiverID) => {
        val t = DirectMessage(userID, receiverID, genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.sender_id.toString + t.receiver_id.toString + t.text + t.time_stamp)
        postDirectMessage(t)
      }
      case PostDestroyMessage(delID) => {
        postTweetPipeline(Post("http://" + serverIP + "/destroyMessage?user_ID=" + userID + "&del_ID=" + delID))
      }
      case ViewHomeTimeline => {
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewHomeTimeline/" + userID))
        userTimelineResponse.foreach { response =>
          println(userID + "  hometimeline: \n" + response.toJson.prettyPrint + "\n")

          //forwards the tweet which first character is 'a'
          for(tweet <- response) {
            if('a' == tweet.text(0)) {
              forwardTweet(userID, tweet)
            }
          }
        }
      }
      case ViewUserTimeline => {
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/" + userID))
        userTimelineResponse.foreach { response =>
          println(userID + "  userTimeline: \n" + response.toJson.prettyPrint + "\n")
        }
      }
      case GetMentionTimeline => {
        val mentionTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewMentionTimeline/" + userID))
        mentionTimelineResponse.foreach { response =>
          println(userID + " mentionTimeline: \n" + response.toJson.prettyPrint + "\n")
        }
      }
      case ViewReceiveMessage => {
        val receiveMessageResponse = directMessagesPipeline(Get("http://" + serverIP + "/viewReceiveMessage/" + userID))
        receiveMessageResponse.foreach { response =>
          println(userID + "  receivedMessages: \n" + response.toJson.prettyPrint + "\n")
        }
      }
      case ViewSendMessage => {
        val sendMessageResponse = directMessagesPipeline(Get("http://" + serverIP + "/viewSendMessage/" + userID))
        sendMessageResponse.foreach { response =>
          println(userID + "  sentMessages: \n" + response.toJson.prettyPrint + "\n")
        }
      }
      case GetFriends => {
        val friendsResponse = arrayPipeline(Get("http://" + serverIP + "/getFriends/" + userID))
        friendsResponse.foreach { response =>
          println(userID + " friendsList: " + response.toList + "\n")
        }
      }
      case GetFollowers => {
        val followersResponse = arrayPipeline(Get("http://" + serverIP + "/getFollowers/" + userID))
        followersResponse.foreach { response =>
          println(userID + " followersList: " + response.toList + "\n")
        }
      }
      case CreateFriendship(newFriend) => {
        pipeline(Post("http://" + serverIP + "/createFriendship?user_ID=" + userID + "&newFriend=" + newFriend))
      }
      case DestroyFriendship(oldFriend) => {
        pipeline(Post("http://" + serverIP + "/destroyFriendship?user_ID=" + userID + "&oldFriend=" + oldFriend))
      }
      case DestroyTweet(deleteTweet) => {
        pipeline(Post("http://" + serverIP + "/destroyTweet?user_ID=" + userID + "&del_ID=" + deleteTweet))
      }

    }
  }

  /*create client workers*/
  val twitterClientWorkers = ArrayBuffer[ActorRef]()
  for(i <-0 until numClientWorkers) {
    val twitterClientWorker = system.actorOf(Props(classOf[clientWorkerActor]), (i + firstClientID).toString)
    twitterClientWorkers.append(twitterClientWorker)
    numOfFollowers.append(0)
  }
  println("create client worker actors finishes")



  /*second interaction step: get the number of followers for each client worker actor*/
  for(i <-0 until numClientWorkers) {
    val responseFuture = followerPipeline (Get("http://" + serverIP + "/getFollowerNum/" + i))
    responseFuture.foreach { response =>
      numOfFollowers(i) = response.numFollowers
      println("client " + i + " followers: " + numOfFollowers(i))
    }
  }
  println("finish.")

  /*check if all actors get their followers count*/
  var num = 0
  while(num < numClientWorkers) {
    val responseFuture2 = getNumPipeline ( Get("http://" + serverIP + "/getNum") )
    responseFuture2.foreach { response =>
      num = response.toInt
    }
    Thread.sleep(1000L)
  }
  Thread.sleep(1000L)
  println("get followers num finish. " + numOfFollowers(1))


  /* simulate the behavior of sending tweets
  val tweetFrequencys: ArrayBuffer[Double] = new ArrayBuffer
  val tweetStartTimes: ArrayBuffer[Int] = new ArrayBuffer
  for(i <- 0 until numClientWorkers) {
    if(numOfFollowers(i) != 0) {
      val tweetFrequency = maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble
      val tweetStartTime = Random.nextInt(60 * 1000)
      tweetFrequencys.append(tweetFrequency)
      tweetStartTimes.append(tweetStartTime)
      println("client " + i + " sends tweets, frequency: " + tweetFrequency / 1000.0 + " start time: " + tweetStartTime / 1000.0)
      system.scheduler.schedule(tweetStartTime milliseconds, tweetFrequency milliseconds, twitterClientWorkers(i), SendTweet)
      system.scheduler.scheduleOnce( tweetStartTime + ((tweetFrequency * 1.5).toInt) milliseconds, twitterClientWorkers(i), ViewUserTimeline)
    }else {
      tweetFrequencys.append(0)
      tweetStartTimes.append(0)
    }
  }

  var maxIndex = numOfFollowers.indexOf(numOfFollowers.max, 0)
  println("max index: " + maxIndex)
  println("max start time: " + tweetStartTimes(maxIndex) + " tweet frequency: " + tweetFrequencys(maxIndex))

  Thread.sleep(tweetStartTimes(maxIndex) + tweetFrequencys(maxIndex).toInt * 5)

  twitterClientWorkers(maxIndex) ! ViewUserTimeline
  */

  /* for testing GetFriends, GetFollowers
  twitterClientWorkers(0) ! GetFriends
  twitterClientWorkers(0) ! GetFollowers

  twitterClientWorkers(49) ! GetFriends
  twitterClientWorkers(49) ! GetFollowers

  twitterClientWorkers(99) ! GetFriends
  twitterClientWorkers(99) ! GetFollowers
  */

  /* for testing CreateFriendship, DestroyFriendship
  twitterClientWorkers(0) ! GetFriends
  twitterClientWorkers(49) ! GetFriends
  twitterClientWorkers(99) ! GetFriends

  twitterClientWorkers(0) ! CreateFriendship(0.1)
  twitterClientWorkers(0) ! DestroyFriendship(0.1)

  twitterClientWorkers(49) ! CreateFriendship(0.2)
  twitterClientWorkers(49) ! DestroyFriendship(0.2)

  twitterClientWorkers(99) ! CreateFriendship(0.3)
  twitterClientWorkers(99) ! DestroyFriendship(0.3)


  twitterClientWorkers(0) ! GetFriends
  twitterClientWorkers(49) ! GetFriends
  twitterClientWorkers(99) ! GetFriends
  */

  /*for testing DestroyTweet
  for(i <-0 to 5)
    twitterClientWorkers(0) ! SendTweet

  twitterClientWorkers(0) ! ViewHomeTimeline
  twitterClientWorkers(0) ! DestroyTweet(0.99)
  twitterClientWorkers(0) ! ViewHomeTimeline

  for(i <-0 to 5)
    twitterClientWorkers(49) ! SendTweet

  twitterClientWorkers(49) ! ViewHomeTimeline
  twitterClientWorkers(49) ! DestroyTweet(0.2)
  twitterClientWorkers(49) ! ViewHomeTimeline
  */


  /* simple test for SendTweet
  for(i <- 0 until numClientWorkers){
    twitterClientWorkers(i) ! SendTweet
    twitterClientWorkers(i) ! SendTweet
  }
  */

  /* the most simple test for SendTweets and ViewTweets
  for(i <- 0 until 5){
    twitterClientWorkers(0) ! SendTweet
    twitterClientWorkers(49) ! SendTweet
    twitterClientWorkers(99) ! SendTweet
  }

  Thread.sleep(1000L)

  twitterClientWorkers(0) ! ViewHomeTimeline
  twitterClientWorkers(49) ! ViewHomeTimeline
  twitterClientWorkers(99) ! ViewHomeTimeline
  */

  /* simple test for MentionTweet, GetMentionTimeline
  twitterClientWorkers(0) ! MentionTweet(1)
  twitterClientWorkers(0) ! MentionTweet(504)

  Thread.sleep(2000L)

  twitterClientWorkers(1) ! GetMentionTimeline
  twitterClientWorkers(504) ! GetMentionTimeline
  twitterClientWorkers(0) ! ViewHomeTimeline
  */

  /* simple test for
  case class PostDirectMessage(receiverID: Double) extends Message
  case class PostDestroyMessage(delID: Double) extends Message
  case object ViewReceiveMessage extends Message
  case object ViewSendMessage extends Message
  */
  twitterClientWorkers(1) ! PostDirectMessage(0.1)
  twitterClientWorkers(1) ! PostDirectMessage(0.1)

  Thread.sleep(2000L)

  twitterClientWorkers(1) ! ViewSendMessage
  twitterClientWorkers(7235) ! ViewReceiveMessage
  Thread.sleep(2000L)
  twitterClientWorkers(7235) ! PostDestroyMessage(0.2)
  Thread.sleep(2000L)
  twitterClientWorkers(7235) ! ViewReceiveMessage

  twitterClientWorkers(0) ! ViewReceiveMessage
  Thread.sleep(2000L)
  twitterClientWorkers(0) ! PostDestroyMessage(0.2)
  Thread.sleep(2000L)
  twitterClientWorkers(0) ! ViewReceiveMessage

}
