package twitterclient

import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ ActorSystem, Actor, Props, ActorRef }
import akka.actor._
import scala.collection.mutable.ArrayBuffer
import common._
import java.security.MessageDigest
import java.util.Formatter
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import scala.util.Random
import scala.util.control.Breaks._
import scala.concurrent.Await
import akka.pattern.ask

object twitterclient {

  sealed trait Message
  case object SendTweet extends Message
  case object ViewTweet extends Message
  case object GetNumFollowers extends Message
  case object IsReady extends Message
  case object GetServerWorkers extends Message
  case object NumOfWorkerIsReady extends Message
  case object SendReadyToServerActor extends Message
  case object ClientBossReadyToWork extends Message
  case object CheckServerBossReady extends Message

  val numUsers: Int = 100000
  //number of server actors
  //actually server need to pass this parameter to the client
  var numWorkers: Int = 0
  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer()
  var maxNumOfFollowers = 100001
  var T = 0.0
  var firstClientID = 0

  var serverBossReadyToWork: Boolean = false

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

  def genRandTweet(): String = {
    "a" * (1 + Random.nextInt(140))
  }

  class clientActor(twitterServer: ActorSelection, twitterWorker: ActorSelection) extends Actor {
    var receiveFollower: Boolean = false

    def receive = {
      case GetNumFollowers => {
        var i = self.path.name.substring(6).toInt
        //        var twitterWorker = context.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/"+(i/numWorkers).toString)
        //        println("twitterWorker: "+twitterWorker+ " i: "+i+" i/numWorker: " + i/numWorkers)
        twitterWorker ! numFollowers(i / numWorkers)
      }

      case followers_num(num) => {
        var index = self.path.name.substring(6).toInt - firstClientID
        numOfFollowers(index) = num
        receiveFollower = true
        //        println(self.path.name + " has " + numOfFollowers(index) + " of followers")
      }

      case IsReady => {
        sender ! receiveFollower
      }

      case SendTweet => {
        val t = Tweet(self.path.name.substring(6).toInt, genRandTweet, getCurrentTime, null)
        t.ref_id = getHash(t.user_id.toString + t.text + dateToString(t.time_stamp))
        twitterServer ! getTweet(t)
      }

      case ViewTweet => {
        var i = self.path.name.substring(6).toInt
        //        var twitterWorker = context.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/"+(i/numWorkers).toString)
        //        println("twitterWorker: "+twitterWorker+ " i: "+i+" i/numWorker: " + i/numWorkers)
        twitterWorker ! viewUserTimeline(i / numWorkers)
        twitterWorker ! viewHomeTimeline(i / numWorkers)
      }

      case displayUserTimeLine(userHomeTimeLine) => {
        println(self.path.name + " received timeline: at " + getCurrentTime)
        for (tweet <- userHomeTimeLine)
          println("receive time: " + getCurrentTime + "   " + tweet)
        println("")
      }

      case displayHomeTimeLine(userHomeTimeLine) => {
        println(self.path.name + " received Homeline: at " + getCurrentTime)
        for (tweet <- userHomeTimeLine)
          println("receive time: " + getCurrentTime + "   " + tweet)
        println("")
      }

    }

  }

  def randBehavior(prob: ArrayBuffer[Int], N: Int): Int = {
    var randProb = Random.nextInt(N)
    var behavior = prob.size
    breakable {
      for (i <- 0 until prob.size) {
        if (prob(i) >= randProb) {
          behavior = i
          break
        }
      }
    }

    if (0 == behavior) {
      return 1
    } else if (1 == behavior) {
      return 5
    } else if (2 == behavior) {
      return 10
    } else {
      return -1
    }
  }

  class bossActor(twitterServer: ActorSelection) extends Actor {
    var IsReady: Boolean = false
    var cBossReadyToWork: Boolean = false
    var ServerBossReady: Boolean = false

    def receive = {
      case GetServerWorkers => {
        twitterServer ! GetNumofServerWorkers
      }

      case numOfServerWorkers(num) => {
        println("sender: " + sender + " number: " + num)
        numWorkers = num
        IsReady = true
      }

      case NumOfWorkerIsReady => {
        sender ! IsReady
      }

      case CheckServerBossReady => {
//        twitterServer ! IsServerBossReady
        sender ! ServerBossReady
      }

      case ServerReady => {
        println("receive server ready from " + sender + " serverBossReadyToWork " + serverBossReadyToWork)
        serverBossReadyToWork = true
        ServerBossReady = true
        println("receive server ready from " + sender + " serverBossReadyToWork " + serverBossReadyToWork)
      }

      case SendReadyToServerActor => {
        twitterServer ! ClientBossReady
      }

      case ServerActorWantYouWork => {
        cBossReadyToWork = true
        println("boss ready receive from the server: time: " + getCurrentTime)
      }

      case ClientBossReadyToWork => {
        sender ! cBossReadyToWork
      }

    }
  }

  def main(args: Array[String]) {
    var serverIP: String = if (args.length > 0) args(0) toString else "10.227.56.128:9001" // "10.227.56.128:9000""10.244.33.142:9000"
    var simulateOption = if (args.length > 0) args(1) toInt else 0 //0 for simulating the real behavior, 1 for simulating ideal behavior
    T = if (args.length > 0) args(2) toDouble else 0.1 // throughput = sum of all client(followers / (MaxFollowers * T)), if T=1, throughput = 100
    var percentageOfActiveClients = if (args.length > 0) args(3) toDouble else 0.05 //percentage of active clients for ideal behavior
    firstClientID = if (args.length > 0) args(4) toInt else 100000 // the first user client ID assigned to this client node 

    implicit val system = ActorSystem("UserSystem")
    val twitterServer = system.actorSelection("akka.tcp://TwitterSystem@" + serverIP + "/user/boss")

    val clientBoss = system.actorOf(Props(classOf[bossActor], twitterServer), "clientBoss")
    clientBoss ! GetServerWorkers
    implicit val timeout = Timeout(20 seconds)
    var OfWorkerIsReady: Boolean = false
    while (!OfWorkerIsReady) {
      val future = clientBoss ? NumOfWorkerIsReady
      OfWorkerIsReady = Await.result(future.mapTo[Boolean], timeout.duration)
    }
    println("the number of server workers: " + numWorkers)

    val twitterWorkers = ArrayBuffer[ActorSelection]()
    for (i <- 0 until numWorkers) {
      val twitterWorker = system.actorSelection("akka.tcp://TwitterSystem@" + serverIP + "/user/" + i.toString)
      twitterWorkers.append(twitterWorker)
    }

    var clientArray = ArrayBuffer[ActorRef]()
    var counter = 0
    while (counter < numUsers) {
      val client = system.actorOf(Props(classOf[clientActor], twitterServer, twitterWorkers((counter + firstClientID) % numWorkers)), "client" + (counter + firstClientID).toString)
      clientArray.append(client)
      if (counter % 10000 == 0)
        println("create " + clientArray(counter).path.name)
      counter += 1
      numOfFollowers.append(0)
    }
    println("create client actors finished") 
    
    {
      clientBoss ! CheckServerBossReady

      implicit val timeout = Timeout(600 seconds)
      var ready: Boolean = false
      while (!ready) {
        val future = clientBoss ? CheckServerBossReady
        ready = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }



    println("serverBossReadyToWork finished.")

    //First get number of followers
    for (userClient <- clientArray)
      userClient ! GetNumFollowers

    //and then check out if all actor have recieved the numFollowers
    for (userClient <- clientArray) {
      implicit val timeout = Timeout(20 seconds)
      var ready: Boolean = false
      while (!ready) {
        val future = userClient ? IsReady
        ready = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }
    println("get followers count finished.")

    //send boss actor, let boss actor to send ready message to server boss actor
    {
      clientBoss ! SendReadyToServerActor

      implicit val timeout = Timeout(600 seconds)
      var BossWorkerIsReady: Boolean = false
      while (!BossWorkerIsReady) {
        val future = clientBoss ? ClientBossReadyToWork
        BossWorkerIsReady = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }

    if (simulateOption == 0) {

      var tweetFrenquecy: ArrayBuffer[Double] = new ArrayBuffer
      var tweetStartTime: ArrayBuffer[Int] = new ArrayBuffer
      var throughput = 0.0
      for (i <- 0 until numUsers) {
        if (numOfFollowers(i) != 0) {
          // all in milliseconds
          tweetFrenquecy.append(maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble)
          tweetStartTime.append(Random.nextInt(600 * 1000))
          //          println("client: " + i + " tweetFrequency seconds: " + tweetFrenquecy(i) / 1000.0 + " tweetStartTime: " + tweetStartTime(i) / 1000.0)
          system.scheduler.schedule(tweetStartTime(i) milliseconds, tweetFrenquecy(i).toInt milliseconds, clientArray(i), SendTweet)
          system.scheduler.scheduleOnce((tweetStartTime(i)+(tweetFrenquecy(i) * 1.5).toInt) milliseconds, clientArray(i), ViewTweet)
          throughput += 1000.0 / tweetFrenquecy(i)
        } else {
          tweetFrenquecy.append(0)
          tweetStartTime.append(0)
        }
      }

      println("the tweet throughput is: " + throughput)

      var index = numOfFollowers.indexOf(numOfFollowers.max, 0)
      println("max followers: " + numOfFollowers.max + " that client is: " + index)
      println("start time: " + tweetStartTime(index) + " frequency: " + tweetFrenquecy(index) / 1000.0)

//      //only view the home timeline of the client who has the maximum number of followers
//      //after 40 seconds and 80 seconds respectively
//      system.scheduler.scheduleOnce(40 seconds) {
//        println("View time: " + getCurrentTime)
//        clientArray(index) ! ViewTweet
//      }
//
//      system.scheduler.scheduleOnce(80 seconds) {
//        println("View time: " + getCurrentTime)
//        clientArray(index) ! ViewTweet
//      }

    } else {

      var sender: ArrayBuffer[Int] = new ArrayBuffer
      for (i <- 0 until numUsers) {
        if (Random.nextDouble() <= percentageOfActiveClients) {
          sender.append(i)
          system.scheduler.schedule(0 seconds, 1 seconds, clientArray(i), SendTweet)
          system.scheduler.scheduleOnce(1.5 seconds, clientArray(i), ViewTweet)
        }
      }

      println("sender size is: " + sender.size)

//      //view the first 5 active clients' home timeline, after 40 seconds and 80 seconds respectively
//      system.scheduler.scheduleOnce(40 seconds) {
//        println("current time: " + getCurrentTime)
//        for (i <- 0 until 5)
//          clientArray(sender(i)) ! ViewTweet
//      }
//
//      system.scheduler.scheduleOnce(80 seconds) {
//        println("current time: " + getCurrentTime)
//        for (i <- 0 until 5)
//          clientArray(sender(i)) ! ViewTweet
//      }

    }

    //after 600 seconds shutdown the client
    system.scheduler.scheduleOnce(600 seconds) {
      system.shutdown()
    }

  }

}