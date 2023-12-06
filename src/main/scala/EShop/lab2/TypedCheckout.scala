package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.Payment


object TypedCheckout {
  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match {
        case StartCheckout => 
          selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self,ExpireCheckout))
      }
    }
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match {
        case CancelCheckout => 
          timer.cancel()
          cancelled
        case SelectDeliveryMethod(method) => 
          selectingPaymentMethod(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
        case ExpireCheckout =>
          cancelled
      }
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match {
        case CancelCheckout => 
          timer.cancel()
          cancelled
        case SelectPayment(payment, orderManager) =>
          val paymentActor = context.spawn(new Payment(payment, orderManager, context.self).start, "payment")
          orderManager ! OrderManager.ConfirmPaymentStarted(paymentActor)
          processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
        case ExpireCheckout =>
          cancelled
      }
    }
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match { 
        case CancelCheckout => 
          timer.cancel()
          cancelled
        case ConfirmPaymentReceived => 
          closed
        case ExpireCheckout => 
          cancelled
      }
    }
  }

  
  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match { 
        case _ =>  Behaviors.stopped
      }
    }
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) => {
      message match { 
        case _ => Behaviors.stopped
      }
    }
  }
}