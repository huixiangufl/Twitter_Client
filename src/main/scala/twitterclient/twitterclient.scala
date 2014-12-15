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


//case class FollowerNum(numFollowers: String)
case class FollowerNum(var userID: Int, var numFollowers: Int)

object FollowerNumProtocol extends DefaultJsonProtocol {
  implicit val followerNumFormat = jsonFormat2(FollowerNum)
}

//case class Tweet(user_id: Long, text: String, time_stamp: Date, var ref_id: String)

//object TweetProtocol extends DefaultJsonProtocol {
//  implicit val tweetFormat = jsonFormat4(Tweet)
//}


object twitterclient extends App {
  sealed trait Message
  case object GetNumOfFollowers extends Message
  case object SendTweet extends Message
  case object ViewTweet extends Message

  var numClientWorkers: Int = 100000
  var firstClientID: Int = 0
  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer
  var maxNumOfFollowers = 100001
  var T = 0.5

  var serverIP: String = "10.227.56.44:8080"

  implicit val system = ActorSystem("UserSystem")
  import system.dispatcher

  /*define various pipelines globally*/
  import FollowerNumProtocol._
  import SprayJsonSupport._
  val followerPipeline = sendReceive ~> unmarshal[FollowerNum]

  val numPipeline = sendReceive

//  import TweetProtocol._
//  val tweetPipeline = sendReceive ~> unmarshal[Tweet]

  def getHash(s: String): String = {
    val sha = MessageDigest.getInstance("SHA-256")
    sha.digest(s.getBytes)
      .foldLeft("")((s: String, b: Byte) => s +
      Character.forDigit((b & 0xf0) >> 4, 16) +
      Character.forDigit(b & 0x0f, 16))
  }

  def dateToString(current: Date): String = {
    val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
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
    def receive = {
      case SendTweet => {
        val t = Tweet(self.path.name.substring(6).toInt, genRandTweet, getCurrentTime, null)
        t.ref_id = getHash(t.user_id.toString + t.text + dateToString(t.time_stamp))
        /* client worker sends tweets to the server */
//        tweetPipeline(Post("http://" + serverIP + "/postTweet/tweet?=" + t))
      }

      case ViewTweet => {
        val i = self.path.name.substring(6).toInt
        /*
        pipeline(Post("http://10.227.56.44:8080/viewUserTimeLine"))
        pipeline(Post("http://10.227.56.44:8080/viewUserTimeLine"))
        */
      }

    }
  }


  val twitterClientWorkers = ArrayBuffer[ActorRef]()
  for(i <-0 until numClientWorkers) {
    val twitterClientWorker = system.actorOf(Props(classOf[clientWorkerActor]), "client" + (i + firstClientID).toString)
    twitterClientWorkers.append(twitterClientWorker)
    numOfFollowers.append(0)
  }
  println("create client worker actors finishes")



  /*second interaction step: get the number of followers for each client worker actor*/
  var count = 0
  for(i <-0 until numClientWorkers) {
    val responseFuture = followerPipeline (Get("http://10.227.56.44:8080/getFollowerNum/" + i))
    responseFuture.foreach { response =>
      numOfFollowers(i) = response.numFollowers
      count += 1
//      println("client " + i + " " + response.numFollowers + " " + numOfFollowers(i) + " count: " + count)
    }
  }

  var num = 0

  while(num != numClientWorkers) {
    val responseFuture2 = numPipeline ( Get("http://10.227.56.44:8080/getNum") )
    responseFuture2.foreach { response =>
      num = response.entity.asString.toInt
//      println("num: " + num)
    }
    Thread.sleep(10L)
  }
  println("finish." + numOfFollowers(99999))


  for(i <- 0 until numClientWorkers) {
    if(numOfFollowers(i) != 0) {
      val tweetFrequency = maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble
      val tweetStartTime = Random.nextInt(600 * 1000)

      /*third interaction step: client worker sends tweets*/
      system.scheduler.schedule(tweetStartTime milliseconds, tweetFrequency.toInt milliseconds, twitterClientWorkers(i), SendTweet)
      /*fourth interaction step: client worker views tweets*/
      system.scheduler.scheduleOnce((tweetStartTime+(tweetFrequency * 1.5).toInt) milliseconds, twitterClientWorkers(i), ViewTweet)
    }
  }

}
