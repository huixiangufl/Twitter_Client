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

  val numUsers: Int = 100000
  //number of server actors
  //actually server need to pass this parameter to the client
  var numPerWorker: Int = 5000
  var numWorkers: Int = 100
    
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

  class clientActor(twitterServer: ActorSelection) extends Actor {
    var numOfFollowers: Int = 0
    var receiveFollower: Boolean = false
    
    def receive = {
      case GetNumFollowers => {
        var i = self.path.name.substring(6).toInt
        var twitterWorker = context.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/"+(i/numPerWorker).toString)
        //println("twitterWorker: "+twitterWorker+ " i: "+i+" i/numWorker: " + i/numPerWorker)
        twitterWorker ! numFollowers(i%numPerWorker)
      }
      
      case followers_num(num) => {
        numOfFollowers = num
        println(self.path.name + " has " + numOfFollowers + " of followers")
        receiveFollower = true
      }
      
      case IsReady => {
        sender ! receiveFollower
      }
      
      case SendTweet => {
        val t = Tweet(self.path.name.substring(6).toInt, "what are you doing?", getCurrentTime, null)
        t.ref_id = getHash(t.user_id.toString + t.text + dateToString(t.time_stamp))
        twitterServer ! getTweet(t)
      }

      case ViewTweet => {
        var i = self.path.name.substring(6).toInt
        var twitterWorker = context.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/"+(i/numPerWorker).toString)
//        println("twitterWorker: "+twitterWorker+ " i: "+i+" i/numWorker: " + i/numPerWorker)
        twitterWorker ! viewUserTimeline(i%numPerWorker)
      }
      

      case displayUserTimeLine(userHomeTimeLine) => {
        println(self.path.name + " usertimeline: at " + getCurrentTime)
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

  def main(args: Array[String]) {
    implicit val system = ActorSystem("UserSystem")
    val twitterServer = system.actorSelection("akka.tcp://TwitterSystem@10.227.56.128:9002/user/boss")
    var clientArray = ArrayBuffer[ActorRef]()
    var counter = 0
    while (counter < numUsers) {
      val client = system.actorOf(Props(classOf[clientActor], twitterServer), "client" + counter.toString)
      clientArray.append(client)
      counter += 1
      if(counter % 10000 == 0)
        println("create " + counter)
    }
    println("create finished")
    
        
    for(userClient <- clientArray)
      userClient ! GetNumFollowers
    
    //First get number of followers
    //and then check out if all actor have recieved the numFollowers
    for(userClient <- clientArray){
//      userClient ! GetNumFollowers
      
      implicit val timeout = Timeout(20 seconds)
      var ready: Boolean = false
      while (!ready) {
        val future = userClient ? IsReady
        ready = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }

    
    var prob: ArrayBuffer[Int] = new ArrayBuffer
    var sender: ArrayBuffer[Int] = new ArrayBuffer
    for(i <- 0 until numUsers){
      var randProb = Random.nextDouble()
      if(randProb <= 0.1){
        sender.append(i)
        system.scheduler.schedule(0 seconds, 1 seconds, clientArray(i), SendTweet)
      }
    }
    println("sender size is: " + sender.size)

    system.scheduler.scheduleOnce(20 seconds) {
      println("current time: " + getCurrentTime)
      for(i <- 0 to 9)
        clientArray(sender(i)) ! ViewTweet
    }
    
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