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
    genRandCharater() + "a" * (0 + Random.nextInt(69))
  }
}


object twitterclient extends App {
  sealed trait Message
  case object GetNumOfFollowers extends Message
  case object SendTweet extends Message
  case class MentionTweet (mentionID: Int) extends Message
  case class ShowTweet (whichTweet: Double) extends Message
  case object ViewHomeTimeline extends Message
  case object ViewUserTimeline extends Message
  case object GetMentionTimeline extends Message
  case object GetFriends extends Message
  case object GetFollowers extends Message
  case class CreateFriendship (newFriend: Double) extends Message
  case class DestroyFriendship (oldFriend: Double) extends Message
  case class DestroyTweet(deleteTweet: Double) extends Message
  case class PostDirectMessage(receiverID: Int) extends Message
  case class PostDestroyMessage(delID: Double) extends Message
  case object ViewReceiveMessage extends Message
  case object ViewSendMessage extends Message

  //just for testting!!!
  case object SendTest extends Message
  case object PrintType1 extends Message
  case object PrintType2 extends Message
  case object PrintType3 extends Message



  var numTweets = 0
  var numDirectMessages = 0
  var numTests = 0

  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer
  val maxNumOfFollowers = 5000

  val serverIP: String = if(args.length > 0) args(0) toString else "10.227.56.44:8080" //"192.168.1.3:9056" //"10.244.33.189:8080" //"10.227.56.44:8080"
  val sendMode: Int = if(args.length > 1) args(1) toInt else 1 // option 0: our own send mode; option 1: homogeneous mode
  val T = if(args.length > 2) args(2) toDouble else 12.5
  val numClientWorkers: Int = if(args.length > 3) args(3) toInt else 100000
  val firstClientID: Int = if(args.length > 4) args(4) toInt else 0
  val numTotalUsers: Int = if(args.length > 5) args(5) toInt else 100000


  implicit val system = ActorSystem("UserSystem")
  import system.dispatcher

  import utility._
  /*define various pipelines globally*/
  import SprayJsonSupport._
  val pipeline = sendReceive
  val postTweetPipeline = sendReceive
  val postTweetPipeline2 = sendReceive
  val getNumPipeline = sendReceive ~> unmarshal[String]
  val followerPipeline = sendReceive ~> unmarshal[followerNum]
  val directMessagesPipeline = sendReceive ~> unmarshal[List[DirectMessage]]
  val timelinePipeline = sendReceive ~> unmarshal[List[Tweet]]
  val arrayPipeline = sendReceive ~> unmarshal[Array[Int]]
  val tweetPipeline = sendReceive ~> unmarshal[Tweet]


  //mentionID: -1 for default tweet message, other wise the user the tweet message mentions
  def postTweet(t: Tweet, mentionID: Int) {
    postTweetPipeline(Post("http://" + serverIP + "/postTweet?userID=" + t.user_id + "&mentionID=" + mentionID + "&text=" + t.text + "&timeStamp=" + t.time_stamp + "&refID=" + t.ref_id))
  }


  def postDirectMessage(d: DirectMessage) {
    postTweetPipeline(Post("http://" + serverIP + "/postMessage?userID=" + d.sender_id + "&receiveID=" + d.receiver_id + "&text=" + d.text + "&timeStamp=" + d.time_stamp + "&refID=" + d.ref_id))
  }


  def forwardTweet(userID: Int, tweet: Tweet) {
    val t = Tweet(userID, "@" + tweet.user_id.toString + ":" + tweet.text, dateToString(getCurrentTime), null)
    t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
    postTweet(t, -1)
//    println("@@@@@@@@@@@@@" + t.user_id + " forwards the tweet: " + t)
  }

  class clientWorkerActor( ) extends Actor{
    val userID = self.path.name.toInt
    def receive = {
      case SendTest => {
        numTests += 1
//        println("numtests: " + numTests)
        postTweetPipeline(Post("http://" + serverIP + "/test?userID=" + userID))
//        pipeline(Post("http://" + serverIP + "/test1?userID=" + userID))
//        postTweetPipeline2(Post("http://" + serverIP + "/test2?userID=" + userID))
      }
      case SendTweet => {
        numTweets += 1
//        println("tweets: " + numTweets)
        val t = Tweet(userID, genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
        postTweet(t, -1)
//        println(userID + " sends tweet: " + t)
      }
      case ShowTweet(whichTweet) => {
        val tweetResponse = tweetPipeline(Get("http://" + serverIP + "/showTweet/" + userID + "/" + whichTweet))
        tweetResponse.foreach { response =>
          println("\n" + userID + " see this tweet: \n" + response.toJson.prettyPrint + "\n")
        }
      }
      case MentionTweet(mentionID) => {
        val t = Tweet(userID, "@" + mentionID.toString + genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
        postTweet(t, mentionID)
//        println(userID + " mentions(@) user " + mentionID + " with tweet: \n" + t)
      }
      case PostDirectMessage(receiverID) => {
        if(numOfFollowers(userID-firstClientID) > 0) {
          numDirectMessages += 1
//          println("directMessages: " + numDirectMessages)
          val t = DirectMessage(userID, receiverID, genRandTweet, dateToString(getCurrentTime), null)
          t.ref_id = getHash(t.sender_id.toString + t.receiver_id.toString + t.text + t.time_stamp)
          postDirectMessage(t)
        }
      }
      case PostDestroyMessage(delID) => {
        postTweetPipeline(Post("http://" + serverIP + "/destroyMessage?user_ID=" + userID + "&del_ID=" + delID))
      }
      case ViewHomeTimeline => {
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewHomeTimeline/" + userID))
        println(userID + " viewHomeTimelineTime: " + getCurrentTime)
        userTimelineResponse.foreach { response =>
          println(userID + "  homeTimeline: \n" + response.toJson.prettyPrint + "\n")

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
        //for testing, chx
        println(userID + " viewUserTimelineTime: " + getCurrentTime)
        //
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
        println(userID + " adds (follows) a new friend. \n")
        pipeline(Post("http://" + serverIP + "/createFriendship?user_ID=" + userID + "&newFriend=" + newFriend))
      }
      case DestroyFriendship(oldFriend) => {
        println(userID + " destroys (unfollows) an old friend. \n")
        pipeline(Post("http://" + serverIP + "/destroyFriendship?user_ID=" + userID + "&oldFriend=" + oldFriend))
      }
      case DestroyTweet(deleteTweet) => {
        pipeline(Post("http://" + serverIP + "/destroyTweet?user_ID=" + userID + "&del_ID=" + deleteTweet))
      }


      case PrintType1 => {
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("------------Testing API: getHomeTimeline, retweet, getUserTimeline, showTweet, destroyTweet-------------------")
        println("------------Testing API: getFollowers, mention(@), getMentionTimeline-----------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
      }

      case PrintType2 => {
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("------------Testing API: getFriends, postCreateFriendship, postDestroyFridnship-------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
      }

      case PrintType3 => {
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("-----Testing API: postDirectMessage, getDirectMessageSent, getDirectMessageReceive, postDestroyMessage--------")
        println("--------------------------------------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------------------------------------")
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
  println("\ncreate client worker actors finishes\n")




  if(sendMode == 0) { // mode 0 for testing all APIs
    /*second interaction step: get the number of followers for each client worker actor*/
    for(i <-0 until numClientWorkers) {
      val responseFuture = followerPipeline (Get("http://" + serverIP + "/getFollowerNum/" + (i + firstClientID).toString))
      responseFuture.foreach { response =>
        numOfFollowers(i) = response.numFollowers
        println("user " + (i + firstClientID).toString + " followers: " + numOfFollowers(i))
      }
    }

    println("\ngetting serverIP finished.\n")


    /*check if all actors get their followers count*/
    var num = 0
    while(num < numTotalUsers) {
      val responseFuture2 = getNumPipeline ( Get("http://" + serverIP + "/getNum") )
      responseFuture2.foreach { response =>
        num = response.toInt
      }
      Thread.sleep(1000L)
    }
    Thread.sleep(1000L)
    println("get followers num finish. \n")


    /* the behavior of sending tweets
   */
    val tweetFrequencys: ArrayBuffer[Double] = new ArrayBuffer
    val tweetStartTimes: ArrayBuffer[Int] = new ArrayBuffer
    val maxIndex = numOfFollowers.indexOf(numOfFollowers.max, 0)
    for(i <- 0 until numClientWorkers) {
      if(numOfFollowers(i) != 0) {
        var tweetStartTime = 0
        if(i == maxIndex) {
          tweetStartTime = 1 * 1000

          //test type1
          system.scheduler.scheduleOnce( (tweetStartTime + 18 * 1000) milliseconds, twitterClientWorkers(i), PrintType1)
          system.scheduler.scheduleOnce( (tweetStartTime + 21 * 1000) milliseconds, twitterClientWorkers(i), ViewHomeTimeline)
          system.scheduler.scheduleOnce( (tweetStartTime + 22 * 1000) milliseconds, twitterClientWorkers(i), ShowTweet(0.1))

          system.scheduler.scheduleOnce( (tweetStartTime + 23 * 1000) milliseconds, twitterClientWorkers(i), ViewUserTimeline)
          system.scheduler.scheduleOnce( (tweetStartTime + 24 * 1000) milliseconds, twitterClientWorkers(i), DestroyTweet(0.1))
          system.scheduler.scheduleOnce( (tweetStartTime + 25 * 1000) milliseconds, twitterClientWorkers(i), ViewUserTimeline)

          //test mention, maxIndex mentions his first follower every 10 seconds
          //and after 26 seconds let his first follower get its mention timeline, should have 3
          val followersResponse = arrayPipeline(Get("http://" + serverIP + "/getFollowers/" + maxIndex))
          var firstFollower = -1
          followersResponse.foreach { response =>
            firstFollower = response(0)
            println( (i + firstClientID).toString + " followersList: " + response.toList + "\n")
          }
          Thread.sleep(1 * 1000L)
          system.scheduler.scheduleOnce( (tweetStartTime + 26 * 1000) milliseconds, twitterClientWorkers(firstFollower), MentionTweet(firstFollower))
          system.scheduler.scheduleOnce( (tweetStartTime + 27 * 1000) milliseconds, twitterClientWorkers(firstFollower), GetMentionTimeline)


          //test type2
          system.scheduler.scheduleOnce( (tweetStartTime + 28 * 1000) milliseconds, twitterClientWorkers(i), PrintType2)
          system.scheduler.scheduleOnce( (tweetStartTime + 31 * 1000) milliseconds, twitterClientWorkers(i), GetFriends)
          system.scheduler.scheduleOnce( (tweetStartTime + 32 * 1000) milliseconds, twitterClientWorkers(i), CreateFriendship(0.0))
          system.scheduler.scheduleOnce( (tweetStartTime + 33 * 1000) milliseconds, twitterClientWorkers(i), GetFriends)
          system.scheduler.scheduleOnce( (tweetStartTime + 34 * 1000) milliseconds, twitterClientWorkers(i), DestroyFriendship(0.0))
          system.scheduler.scheduleOnce( (tweetStartTime + 35 * 1000) milliseconds, twitterClientWorkers(i), GetFriends)


          //test type3
          system.scheduler.scheduleOnce( (tweetStartTime + 38 * 1000) milliseconds, twitterClientWorkers(i), PrintType3)
          system.scheduler.scheduleOnce( (tweetStartTime + 41 * 1000) milliseconds, twitterClientWorkers(i), PostDirectMessage(firstFollower))
          system.scheduler.scheduleOnce( (tweetStartTime + 42 * 1000) milliseconds, twitterClientWorkers(i), ViewSendMessage)
          system.scheduler.scheduleOnce( (tweetStartTime + 43 * 1000) milliseconds, twitterClientWorkers(firstFollower), ViewReceiveMessage)
          system.scheduler.scheduleOnce( (tweetStartTime + 44 * 1000) milliseconds, twitterClientWorkers(firstFollower), PostDestroyMessage(0.0))
          system.scheduler.scheduleOnce( (tweetStartTime + 45 * 1000) milliseconds, twitterClientWorkers(firstFollower), ViewReceiveMessage)
        }else {
          tweetStartTime = Random.nextInt(600 * 1000)
        }
        val tweetFrequency = maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble
        tweetFrequencys.append(tweetFrequency)
        tweetStartTimes.append(tweetStartTime)
        // println("user " + (i + firstClientID).toString + " sends tweets, frequency: " + tweetFrequency / 1000.0 + " start time: " + tweetStartTime / 1000.0)
        system.scheduler.schedule(tweetStartTime milliseconds, tweetFrequency milliseconds, twitterClientWorkers(i), SendTweet)
        // system.scheduler.schedule(tweetStartTime + 10 milliseconds, tweetFrequency milliseconds, twitterClientWorkers(i), PostDirectMessage(1))
        // system.scheduler.scheduleOnce( tweetStartTime + ((tweetFrequency * 1.5).toInt) milliseconds, twitterClientWorkers(i), ViewUserTimeline)
      }else {
        tweetFrequencys.append(0)
        tweetStartTimes.append(0)
      }
    }

    println("Setting tweeting frequency finished")
    println("userID which has maximum followers is: " + maxIndex + " its start time: " + tweetStartTimes(maxIndex) / 1000.0 + " tweet frequency: " + tweetFrequencys(maxIndex) / 1000.0 + " num of followers: " + numOfFollowers(maxIndex))
    println()
    Thread.sleep(tweetStartTimes(maxIndex) + tweetFrequencys(maxIndex).toInt * 5)

  } else if(sendMode == 1) { // mode 1 for testing throughput
    for (i <- 0 until numClientWorkers) {
      if (Random.nextDouble() <= 1.0) {
        val tweetStartTime = Random.nextInt(10 * 1000)
        system.scheduler.schedule(tweetStartTime milliseconds, T seconds, twitterClientWorkers(i), SendTweet)
      }
    }
  }



//  twitterClientWorkers(maxIndex) ! ViewUserTimeline

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
  */

}
