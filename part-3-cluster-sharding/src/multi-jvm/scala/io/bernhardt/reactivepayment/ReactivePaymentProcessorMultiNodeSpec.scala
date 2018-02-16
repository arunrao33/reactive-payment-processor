package io.bernhardt.reactivepayment

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.typesafe.config.ConfigFactory
import io.bernhardt.reactivepayment.PaymentProcessor._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ReactivePaymentMultiJvmNode1 extends ReactivePaymentProcessorMultiNode

class ReactivePaymentMultiJvmNode2 extends ReactivePaymentProcessorMultiNode

class ReactivePaymentMultiJvmNode3 extends ReactivePaymentProcessorMultiNode

class ReactivePaymentProcessorMultiNode extends MultiNodeSpec(ReactivePaymentMultiNodeConfig) with ScalaTestMultiNodeSpec with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(scaled(Span(15, Seconds)))

  import ReactivePaymentMultiNodeConfig._

  override def initialParticipants = 3


  "A Reactive Payment Processor" must {

    var processor: Option[PaymentProcessor] = None

    "start all nodes" in within(15.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])
      Cluster(system) join node(node1).address

      runOn(node1) {
        ReactivePaymentProcessor(system)
      }
      runOn(node2) {
        ReactivePaymentProcessor(system)
      }
      runOn(node3) {
       processor = Some(ReactivePaymentProcessor(system))

      }

      receiveN(3).collect { case MemberUp(m) => m.address }.toSet must be(
        Set(node(node1).address, node(node2).address, node(node3).address)
      )

      testConductor.enter("all-up")
    }

    "be able to process a valid order" in within(15.seconds) {
      runOn(node3) {
        val order = Order(PaymentProcessor.MerchantAccountA, CreditCardToken("token"), BigDecimal(10.00), EUR, "Test node 3")
        processor.get.processPayment(order).futureValue mustBe an[OrderSucceeded]
      }

      enterBarrier("order-processed")
    }

    "do not process an invalid order" in within(15.seconds) {
      runOn(node3) {
        val order = Order(PaymentProcessor.MerchantAccountA, CreditCardToken("token"), BigDecimal(-10.00), EUR, "Test node 3")
        processor.get.processPayment(order).futureValue mustBe an[OrderFailed]
      }

      enterBarrier("order-rejected")
    }

    "be able to process an order even when a node fails" ignore within(15.seconds) {
      // TODO why does this fail in multi-jvm?
      testConductor.blackhole(node3, node1, Direction.Both).futureValue

      runOn(node3) {
        // run this with MerchantAccountB which should work for node2
        val order = Order(PaymentProcessor.MerchantAccountB, CreditCardToken("token"), BigDecimal(10.00), EUR, "Test node 3")
        processor.get.processPayment(order).futureValue mustBe an[OrderSucceeded]
      }

      testConductor.passThrough(node1, node3, Direction.Both).futureValue

      enterBarrier("order-succeeded-with-down-node")

    }
  }
}


object ReactivePaymentMultiNodeConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  testTransport(on = true)

  nodeConfig(node1)(ConfigFactory.parseString(
    """
      |akka.cluster.roles=[bank-A]
      |akka.persistence.journal.leveldb.dir = "target/journal-A"
    """.stripMargin))

  nodeConfig(node2)(ConfigFactory.parseString(
    """
      |akka.cluster.roles=[bank-B]
      |akka.persistence.journal.leveldb.dir = "target/journal-B"
    """.stripMargin))

    nodeConfig(node3)(ConfigFactory.parseString(
    """
      |akka.cluster.roles=[bank-C]
      |akka.persistence.journal.leveldb.dir = "target/journal-C"
    """.stripMargin))


  commonConfig(ConfigFactory.parseString(
    """
      |akka.loglevel=INFO
      |akka.actor.provider = cluster
      |akka.remote.artery.enabled = on
      |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      |akka.coordinated-shutdown.terminate-actor-system = off
      |akka.cluster.run-coordinated-shutdown-when-down = off
      |akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
    """.stripMargin))
}