package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  RetentionCriteria
}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        Empty,
        commandHandler(context),
        eventHandler(context)
      )
    // .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 3, keepNSnapshots = 2))
    }

  def commandHandler(context: ActorContext[Command])
    : (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case Empty =>
          command match {
            case AddItem(item) =>
              Effect.persist(ItemAdded(item))
            case GetItems(sender) =>
              sender ! Cart.empty
              Effect.none
            case _ =>
              Effect.unhandled
          }

        case NonEmpty(cart, _) =>
          command match {
            case AddItem(item) =>
              Effect.persist(ItemAdded(item))
            case RemoveItem(item) if !cart.contains(item) =>
              Effect.none
            case RemoveItem(item) =>
              Effect.persist(ItemRemoved(item))
            case ExpireCart =>
              Effect.persist(CartExpired)
            case StartCheckout(orderManagerRef) =>
              val checkout =
                context.spawn(new TypedCheckout(context.self).start, "checkout")
              Effect
                .persist(CheckoutStarted(checkout))
                .thenRun { _ =>
                  orderManagerRef ! OrderManager.ConfirmCheckoutStarted(
                    checkout)
                  checkout ! TypedCheckout.StartCheckout
                }
            case GetItems(sender) =>
              sender ! cart
              Effect.none
            case _ =>
              Effect.unhandled
          }

        case InCheckout(_) =>
          command match {
            case ConfirmCheckoutCancelled =>
              Effect.persist(CheckoutCancelled)
            case ConfirmCheckoutClosed =>
              Effect.persist(CheckoutClosed)
            case _ => Effect.none
          }
      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      //???
      event match {
        case CheckoutStarted(_) =>
          state match {
            case NonEmpty(cart, timer) => InCheckout(cart)
            case _                     => state
          }
        case ItemAdded(item) =>
          state match {
            case Empty =>
              NonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
            case NonEmpty(cart, timer) =>
              NonEmpty(cart.addItem(item), scheduleTimer(context))
            case _ => state
          }
        case ItemRemoved(item) =>
          state match {
            case NonEmpty(cart, timer)
                if (cart.size == 1 && cart.contains(item)) =>
              Empty
            case NonEmpty(cart, timer)
                if (cart.size > 1 && cart.contains(item)) =>
              NonEmpty(cart.removeItem(item), scheduleTimer(context))
            case _ => state
          }
        case CartEmptied | CartExpired =>
          state match {
            case NonEmpty(cart, timer) => Empty
            case _                     => state
          }
        case CheckoutClosed =>
          state match {
            case InCheckout(cart) => Empty
            case _                => state
          }
        case CheckoutCancelled =>
          state match {
            case InCheckout(cart) => NonEmpty(cart, scheduleTimer(context))
            case _                => state
          }
      }
    }

}
