package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.util.control

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AddItem(id, sender) =>
          println("added item")
          val cartActor = context.spawn(new TypedCartActor().start, "cart").ref
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cartActor)
        case _ => Behaviors.unhandled
      }
    }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      {
        message match {
          case AddItem(id, sender) =>
            cartActor ! TypedCartActor.AddItem(id)
            sender ! Done
            open(cartActor)
          case RemoveItem(id, sender) =>
            cartActor ! TypedCartActor.RemoveItem(id)
            sender ! Done
            open(cartActor)
          case Buy(sender) =>
            cartActor ! TypedCartActor.StartCheckout(context.self)
            sender ! Done
            inCheckout(cartActor, sender)
          case _ =>
            Behaviors.unhandled
        }
      }
    }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      {
        message match {
          case ConfirmCheckoutStarted(checkoutRef) =>
            senderRef ! Done
            inCheckout(checkoutRef)
        }
      }
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
          sender ! Done
          inPayment(sender)

      }
    }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      {
        message match {
          case ConfirmPaymentStarted(paymentRef) =>
            senderRef ! Done
            inPayment(paymentRef, senderRef)

        }
      }
    }

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case ConfirmPaymentReceived =>
          senderRef ! Done
          finished
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          sender ! Done
          inPayment(paymentActorRef, senderRef)

      }
    }

  def finished: Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case _ => Behaviors.stopped
      }
    }
}
