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
import DefaultJsonProtocol._
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
  case object ViewTweet extends Message

  var numClientWorkers: Int = 1000
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
  val postTweetPipeline = sendReceive
  val getNumPipeline = sendReceive ~> unmarshal[String]//~> unmarshal[Int]
  val followerPipeline = sendReceive ~> unmarshal[FollowerNum]
  val tweetPipeline = sendReceive ~> unmarshal[Tweet]
  val timelinePipeline = sendReceive ~> unmarshal[List[String]]

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
        println("client " + self.path.name + " sends tweet: " + t)
      }
      case ViewHomeTimeline => {
        val i = self.path.name.substring(6).toInt
        postTweetPipeline(Get("http://10.227.56.44:8080/viewUserTimeline" + i))
      }
      case ViewUserTimeline => {
        val i = self.path.name.substring(6).toInt
        val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/" + i))
        userTimelineResponse.foreach { response =>
          print(self.path.name + "  timeline:  ")
          println(response)
        }
      }
      /*
      case ViewTweet => {
        val responseFuture3 = tweetPipeline(Get("http://10.227.56.44:8080/getTweet/" + reference_id))
        responseFuture3.foreach { response =>
          println(response.user_id)
          println(response.text)
          println(response.time_stamp)
          println(response.ref_id)
        }
      }*/
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
    val responseFuture = followerPipeline (Get("http://10.227.56.44:8080/getFollowerNum/" + i))
    responseFuture.foreach { response =>
      numOfFollowers(i) = response.numFollowers
    }
  }

  var num = 0
  while(num != numClientWorkers) {
    val responseFuture2 = getNumPipeline ( Get("http://10.227.56.44:8080/getNum") )
    responseFuture2.foreach { response =>
      num = response.toInt
//      num = response.entity.asString.toInt
//      num = response
    }
    Thread.sleep(100L)
  }
  Thread.sleep(1000L)
  println("finish." + numOfFollowers(1))

//  twitterClientWorkers(0) ! SendTweet
//  Thread.sleep(1000)
//  twitterClientWorkers(0) ! ViewTweet

  /*
  for(i <- 0 until 2) {
    if(numOfFollowers(i) != 0) {
      println("send tweets. ")
//      val tweetFrequency = maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble
//      val tweetStartTime = Random.nextInt(600 * 1000)
      system.scheduler.schedule(0 milliseconds, 1000 milliseconds, twitterClientWorkers(0), SendTweet)
      system.scheduler.scheduleOnce(4000 milliseconds, twitterClientWorkers(0), ViewUserTimeline)
//      system.scheduler.schedule(0 milliseconds, tweetFrequency.toInt milliseconds, twitterClientWorkers(0), SendTweet)
//      system.scheduler.scheduleOnce(((tweetFrequency * 1.5).toInt) milliseconds, twitterClientWorkers(0), ViewUserTimeline)
    }
  }
  */



  for(i <- 0 until 2){
    val t = Tweet(0, genRandTweet, dateToString(getCurrentTime), null)
    t.ref_id = getHash(t.user_id.toString + t.text + t.time_stamp)
//  val str = "http://" + serverIP + "/postTweet?userID=" + t.user_id + "&text=" + t.text + "&timeStamp=" + t.time_stamp + "&refID=" + t.ref_id
    postTweetPipeline(Post("http://" + serverIP + "/postTweet?userID=" + t.user_id + "&text=" + t.text + "&timeStamp=" + t.time_stamp + "&refID=" + t.ref_id))
    println("client 0 sends tweet: " + t)
  }

  Thread.sleep(100L)

  val userTimelineResponse = timelinePipeline(Get("http://" + serverIP + "/viewUserTimeline/0"))
  userTimelineResponse.foreach { response =>
    print("client 0 timeline:  ")
    println(response)
    println(response(0))
    println(response(1))
  }


//  for(i <- 0 until 2) {
//    twitterClientWorkers(0) ! SendTweet
//  }
//  Thread.sleep(1000L)
//  twitterClientWorkers(0) ! ViewUserTimeline

}
