package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.{
  BehaviorTestKit,
  ScalaTestWithActorTestKit,
  TestInbox
}
import akka.actor.testkit.typed.Effect
import EShop.lab2.TypedCheckout

class TypedCartTest
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly [sync]" in {
    //Given
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    //When
    testKit.run(TypedCartActor.AddItem("apple"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))

    //Then
    inbox.expectMessage(Cart.empty.addItem("apple"))
  }

  it should "add item properly [async]" in {
    //Given
    val probe = testKit.createTestProbe[Any]()
    val cart = testKit.spawn(new TypedCartActor().start)

    //When
    cart ! AddItem("apple")
    cart ! GetItems(probe.ref)

    //Then
    probe.expectMessage(Cart.empty.addItem("apple"))
  }

  it should "be empty after adding and removing the same item [sync]" in {
    //Given
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    //When
    testKit.run(TypedCartActor.AddItem("apple"))
    testKit.run(TypedCartActor.RemoveItem("apple"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))

    //Then
    inbox.expectMessage(Cart.empty)
  }

  it should "be empty after adding and removing the same item [async]" in {
    //Given
    val probe = testKit.createTestProbe[Any]()
    val cart = testKit.spawn(new TypedCartActor().start)

    //When
    cart ! AddItem("apple")
    cart ! RemoveItem("apple")
    cart ! GetItems(probe.ref)

    //Then
    probe.expectMessage(Cart.empty)
  }

  it should "start checkout [sync]" in {
    //Given
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[OrderManager.Command]()

    //When
    testKit.run(AddItem("apple"))
    testKit.run(StartCheckout(inbox.ref))

    //Then
    testKit.expectEffectType[Effect.Scheduled[TypedCartActor]]
    testKit.expectEffectType[Effect.Spawned[TypedCheckout]]
    inbox.expectMessage(_: OrderManager.ConfirmCheckoutStarted)
  }

  it should "start checkout [async]" in {
    //Given
    val probe = testKit.createTestProbe[Any]()
    val cart = testKit.spawn(new TypedCartActor().start)

    //When
    cart ! AddItem("apple")
    cart ! StartCheckout(probe.ref)

    //Then
    probe.expectMessageType[OrderManager.ConfirmCheckoutStarted]
  }
}
