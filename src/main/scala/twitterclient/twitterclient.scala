package twitterclient

import common._
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
import akka.pattern.ask

import akka.actor.ActorSystem
import spray.json.{JsonFormat, DefaultJsonProtocol}
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._


case class FollowerNum(var userID: Int, var numFollowers: Int)


object TweetProtocol extends DefaultJsonProtocol {
  implicit val followerNumFormat = jsonFormat2(FollowerNum)
  implicit val tweetFormat = jsonFormat4(Tweet)
}


object twitterclient extends App {
  sealed trait Message
  case object GetNumOfFollowers extends Message
  case object SendTweet extends Message
  case object ViewHomeTimeline extends Message
  case object ViewUserTimeline extends Message
  case object GetFriends extends Message
  case object GetFollowers extends Message
  case class CreateFriendship (newFriend: Double) extends Message
  case class DestroyFriendship (oldFriend: Double) extends Message
  case class DestroyTweet(deleteTweet: Double) extends Message

  var numClientWorkers: Int = 100
  var firstClientID: Int = 0
  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer
  var maxNumOfFollowers = 100001
  var T = 0.5

  var serverIP: String = "10.227.56.44:8080"

  implicit val system = ActorSystem("UserSystem")
  import system.dispatcher

  /*define various pipelines globally*/
  import SprayJsonSupport._
  import TweetProtocol._
  val pipeline = sendReceive
  val postTweetPipeline = sendReceive
  val getNumPipeline = sendReceive ~> unmarshal[String]//~> unmarshal[Int]
  val followerPipeline = sendReceive ~> unmarshal[FollowerNum]
  val tweetPipeline = sendReceive ~> unmarshal[Tweet]
  val timelinePipeline = sendReceive ~> unmarshal[List[String]]
  val arrayPipeline = sendReceive ~> unmarshal[Array[Int]]

  def getHash(s: String): String = {
    val sha = MessageDigest.getInstance("SHA-256")
    sha.digest(s.getBytes)
      .foldLeft("")((s: String, b: Byte) => s +
      Character.forDigit((b & 0xf0) >> 4, 16) +
      Character.forDigit(b & 0x0f, 16))
  }

  def dateToString(current: Date): String = {
    val formatter = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss.SSS")
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


  class clientWorkerActor( ) extends Actor{
    var reference_id = ""
    def receive = {
      case SendTweet => {
        val t = Tweet(self.path.name.substring(6).toInt, genRandTweet, dateToString(getCurrentTime), null)
        t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
        reference_id = t.ref_id
        /* client worker sends tweets to the server */
        postTweetPipeline(Post("http://" + serverIP + "/postTweet?userID=" + t.user_id + "&text=" + t.text + "&timeStamp=" + t.time_stamp + "&refID=" + t.ref_id))
        println(self.path.name + " sends tweet: " + t)
      }
      case ViewHomeTimeline => {
        val userID = self.path.name.substring(6).toInt
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewHomeTimeline/" + userID))
        userTimelineResponse.foreach { response =>
          print(self.path.name + "  hometimeline: " + response)
          println()
        }
      }
      case ViewUserTimeline => {
        val userID = self.path.name.substring(6).toInt
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/" + userID))
        userTimelineResponse.foreach { response =>
          print(self.path.name + "  userTimeline:  " + response)
          println()
        }
      }
      case GetFriends => {
        val userID = self.path.name.substring(6).toInt
        val friendsResponse = arrayPipeline(Get("http://" + serverIP + "/getFriends/" + userID))
        friendsResponse.foreach { response =>
          println(self.path.name + " friendsList: " + response.toList)
          println()
        }
      }
      case GetFollowers => {
        val userID = self.path.name.substring(6).toInt
        val followersResponse = arrayPipeline(Get("http://" + serverIP + "/getFollowers/" + userID))
        followersResponse.foreach { response =>
          println(self.path.name + " followersList: " + response.toList)
          println()
        }
      }
      case CreateFriendship(newFriend) => {
        val userID = self.path.name.substring(6).toInt
        pipeline(Post("http://" + serverIP + "/createFriendship?user_ID=" + userID + "&newFriend=" + newFriend))
      }
      case DestroyFriendship(oldFriend) => {
        val userID = self.path.name.substring(6).toInt
        pipeline(Post("http://" + serverIP + "/destroyFriendship?user_ID=" + userID + "&oldFriend=" + oldFriend))
      }
      case DestroyTweet(deleteTweet) => {
        val userID = self.path.name.substring(6).toInt
        pipeline(Post("http://" + serverIP + "/destroyTweet?user_ID=" + userID + "&del_ID=" + deleteTweet))
      }

    }
  }

  /*create client workers*/
  val twitterClientWorkers = ArrayBuffer[ActorRef]()
  for(i <-0 until numClientWorkers) {
    val twitterClientWorker = system.actorOf(Props(classOf[clientWorkerActor]), "client" + (i + firstClientID).toString)
    twitterClientWorkers.append(twitterClientWorker)
    numOfFollowers.append(0)
  }
  println("create client worker actors finishes")



  /*second interaction step: get the number of followers for each client worker actor*/
  for(i <-0 until numClientWorkers) {
    val responseFuture = followerPipeline (Get("http://" + serverIP + "/getFollowerNum/" + i))
    responseFuture.foreach { response =>
      numOfFollowers(i) = response.numFollowers
    }
  }

  var num = 0
  while(num != numClientWorkers) {
    val responseFuture2 = getNumPipeline ( Get("http://" + serverIP + "/getNum") )
    responseFuture2.foreach { response =>
      num = response.toInt
    }
    Thread.sleep(100L)
  }
  Thread.sleep(1000L)
  println("get followers count finish. " + numOfFollowers(1))


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
//      system.scheduler.scheduleOnce( tweetStartTime + ((tweetFrequency * 1.5).toInt) milliseconds, twitterClientWorkers(i), ViewUserTimeline)
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

  /*for testing DestroyTweet*/
  for(i <-0 to 5)
    twitterClientWorkers(0) ! SendTweet

  twitterClientWorkers(0) ! ViewHomeTimeline
  twitterClientWorkers(0) ! DestroyTweet(0.99)
  twitterClientWorkers(0) ! ViewHomeTimeline

//  for(i <-0 to 5)
//    twitterClientWorkers(49) ! SendTweet
//
//  twitterClientWorkers(49) ! ViewHomeTimeline
//  twitterClientWorkers(49) ! DestroyTweet(0.2)
//  twitterClientWorkers(49) ! ViewHomeTimeline


  /* simple test for SendTweet
  for(i <- 0 until numClientWorkers){
    twitterClientWorkers(i) ! SendTweet
    twitterClientWorkers(i) ! SendTweet
  }

  Thread.sleep(1000L)

  var userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/0"))
  userTimelineResponse.foreach { response =>
    print("client 0 timeline:  ")
    println(response)
    println()
  }

  userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/50"))
  userTimelineResponse.foreach { response =>
    print("client 50 timeline:  ")
    println(response)
    println()
  }

  userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/100"))
  userTimelineResponse.foreach { response =>
    print("client 100 timeline:  ")
    println(response)
    println()
  }
  */

}
