package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {
  import context._
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive{
    case StartCheckout => 
      log.debug("[CHECKOUT RECEIVE] checkout started")
      context become selectingDelivery(system.scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive{
    case CancelCheckout => 
      log.debug("[CHECKOUT SELECTINGDELIVERY] checkout started")
      timer.cancel()
      context become cancelled

    case SelectDeliveryMethod(method) =>
      log.debug(s"[CHECKOUT SELECTINGDELIVERY] choosen delivery method: $method")
      context.become(selectingPaymentMethod(system.scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)))
    case ExpireCheckout =>
      log.debug("[CHECKOUT SELECTINGDELIVERY] checkout expired")
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive{
    case CancelCheckout =>
      timer.cancel()
      log.debug("[CHECKOUT SELECTINGPAYMENTMETHOD] checkout cancelled")
      context become cancelled

    case SelectPayment(payment) =>
      timer.cancel()
      log.debug(s"[CHECKOUT SELECTINGPAYMENTMETHOD] choose payment mehtod: $payment")
      context become processingPayment(system.scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))

    case ExpireCheckout =>
      log.debug("[CHECKOUT SELECTINGPAYMENTMETHOD] checkout expired")
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive{
    case CancelCheckout =>
      timer.cancel()
      log.debug("[CHECKOUT PROCESSINGPAYMENT] checkout cancelled")
      context.become(cancelled)
    case ConfirmPaymentReceived =>
      context.become(closed)
    case ExpirePayment =>
      log.debug("[CHECKOUT PROCESSINGPAYMENT] checkout expired")
      context become cancelled
  }

  def cancelled: Receive = {
    case _ => context.parent ! CheckOutClosed
  }

  def closed: Receive = {
    case _ => context.parent ! PaymentStarted(context.self)    
  }

}
