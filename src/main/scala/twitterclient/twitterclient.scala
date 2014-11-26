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

  val numUsers: Int = 100000
  //number of server actors
  //actually server need to pass this parameter to the client
  var numWorkers: Int = 0
  var numOfFollowers: ArrayBuffer[Int] = new ArrayBuffer()
  var maxNumOfFollowers = 100001
  var T = 1
  
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
    "a" * ( 1 + Random.nextInt(20) )
  }

  class clientActor(twitterServer: ActorSelection, twitterWorker: ActorSelection) extends Actor {
//    var numOfFollowers: Int = 0
    var receiveFollower: Boolean = false
    
    def receive = {
      case GetNumFollowers => {
        var i = self.path.name.substring(6).toInt
//        var twitterWorker = context.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/"+(i/numWorkers).toString)
//        println("twitterWorker: "+twitterWorker+ " i: "+i+" i/numWorker: " + i/numWorkers)
        twitterWorker ! numFollowers(i/numWorkers)
      }
      
      case followers_num(num) => {
        numOfFollowers(self.path.name.substring(6).toInt) = num
//        println(self.path.name + " has " + numOfFollowers(self.path.name.substring(6).toInt) + " of followers")
//        println(self.path.name)
        receiveFollower = true
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
        twitterWorker ! viewUserTimeline(i/numWorkers)
      }
      

      case displayUserTimeLine(userHomeTimeLine) => {
        println(self.path.name + "received time usertimeline: at " + getCurrentTime)
        for (tweet <- userHomeTimeLine)
          println("receive time: "+getCurrentTime+ "   " +tweet)
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
   
  class bossActor(twitterServer: ActorSelection) extends Actor{
    var IsReady: Boolean = false
    
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
      
    }
  }

  def main(args: Array[String]) {
    implicit val system = ActorSystem("UserSystem")
    val twitterServer = system.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9000/user/boss")
    
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
    for(i <- 0 until numWorkers){
      val twitterWorker = system.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9000/user/"+i.toString)
      twitterWorkers.append(twitterWorker)
    }
    
    var clientArray = ArrayBuffer[ActorRef]()
    var counter = 0
    while (counter < numUsers) {
      val client = system.actorOf(Props(classOf[clientActor], twitterServer, twitterWorkers(counter%numWorkers)), "client" + counter.toString)
      clientArray.append(client)
      counter += 1
      if(counter % 10000 == 0)
        println("create " + counter)
      numOfFollowers.append(0)
    }
    println("create finished")


    //First get number of followers
    for (userClient <- clientArray)
      userClient ! GetNumFollowers
    
    //and then check out if all actor have recieved the numFollowers
    for(userClient <- clientArray){
      
      implicit val timeout = Timeout(20 seconds)
      var ready: Boolean = false
      while (!ready) {
        val future = userClient ? IsReady
        ready = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }
    
    println("get followers count finished.")


    var tweetFrenquecy: ArrayBuffer[Double] = new ArrayBuffer
    var tweetStartTime: ArrayBuffer[Int] = new ArrayBuffer
    var throughput = 0.0
    for(i <- 0 until numUsers){
      if (numOfFollowers(i) != 0) {
        // all in milliseconds
//        tweetFrenquecy = maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble
//        tweetStartTime = Random.nextInt(600 * 1000)
        tweetFrenquecy.append(maxNumOfFollowers.toDouble * T * 1000.0 / numOfFollowers(i).toDouble)
//        tweetStartTime.append(Random.nextInt(600 * 10))
        tweetStartTime.append(0)
        println("client: " + i + " tweetFrequency seconds: " + tweetFrenquecy(i) / 1000.0 + " tweetStartTime: " + tweetStartTime(i) / 1000.0)
        system.scheduler.schedule(tweetStartTime(i) milliseconds, tweetFrenquecy(i).toInt milliseconds, clientArray(i), SendTweet)
        throughput += 1000.0 / tweetFrenquecy(i)
      }else{
        tweetFrenquecy.append(0)
        tweetStartTime.append(0)
      }
    }
    println("the tweet throughput is: " + throughput)
    
    var index = numOfFollowers.indexOf(numOfFollowers.max, 0)
    println("max followers: " + numOfFollowers.max + " max " + numOfFollowers(index) + " index: " + index)
    println("start time: " + tweetStartTime(index) + " frequency: " + tweetFrenquecy(index) / 1000.0)
    
    system.scheduler.scheduleOnce(40 seconds) {
      println("View time: " + getCurrentTime)
      clientArray(index) ! ViewTweet
    }

    system.scheduler.scheduleOnce(80 seconds) {
      println("View time: " + getCurrentTime)
      clientArray(index) ! ViewTweet
    }
//    var prob: ArrayBuffer[Int] = new ArrayBuffer
//    var sender: ArrayBuffer[Int] = new ArrayBuffer
//    for(i <- 0 until numUsers){
//      var randProb = Random.nextDouble()
//      if(randProb <= 0.1){
//        sender.append(i)
//        system.scheduler.schedule(0 seconds, 1 seconds, clientArray(i), SendTweet)
//      }
//    }
//    println("sender size is: " + sender.size)

//    system.scheduler.scheduleOnce(30 seconds) {
//      println("current time: " + getCurrentTime)
//      for(i <- 0 to 9)
//        clientArray(sender(i)) ! ViewTweet
//    }
    
    /*
    for debugingg
    system.scheduler.schedule(0 seconds, 1 seconds, clientArray(1), SendTweet)
    system.scheduler.schedule(0 seconds, 1 seconds, clientArray(10), SendTweet)
    system.scheduler.schedule(0 seconds, 1 seconds, clientArray(4002), SendTweet)
    
    system.scheduler.scheduleOnce(20 seconds){
      clientArray(1) ! ViewTweet
      clientArray(10) ! ViewTweet
      clientArray(4002) ! ViewTweet
    }
    * 
    */
    
    
    system.scheduler.scheduleOnce(600 seconds) {
      system.shutdown()
    }
    

//    var number = numUsers.toDouble * 0.01
//    prob.append(number.toInt)
//    number = numUsers.toDouble * 0.11
//    prob.append(number.toInt)
//    number = numUsers.toDouble * 0.56
//    prob.append(number.toInt)
//    number = numUsers.toDouble * 1.0
//    prob.append(number.toInt)
    
//    for(i <- 0 until numUsers){
//      var randSeconds = randBehavior(prob, numUsers)
//      if(1 == i)
//        println("user " + i + " randSeconds: " + randSeconds)
//      if(randSeconds != -1)
//        system.scheduler.schedule(0 seconds, randSeconds seconds, clientArray(i), SendTweet)
//    }
//    
//    system.scheduler.scheduleOnce(30 seconds){
//      clientArray(1) ! ViewTweet
//    }

  }

}