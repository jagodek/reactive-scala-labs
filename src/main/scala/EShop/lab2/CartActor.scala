package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {
  
  import CartActor._
  

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive{
    case AddItem(item) => 
      log.debug(s"[CART EMPTY] Added to cart $item")
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

   def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug(s"[CART NONEMPTY] Added to cart $item")
      context become nonEmpty(cart.addItem(item),scheduleTimer)   
    case RemoveItem(item) if cart.contains(item) && cart.size ==1 =>
      timer.cancel()
      log.debug(s"[CART NONEMPTY] Removed from cart $item")
      context become empty
    case RemoveItem(item) if cart.contains(item) =>
      scheduleTimer
      context become nonEmpty(cart.removeItem(item),scheduleTimer) 
    case StartCheckout => 
      timer.cancel()
      log.debug("[CART NONEMPTY] Checkout started")
      context become inCheckout(cart)  
    case ExpireCart => 
      log.debug("[CART NONEMPTY] Expiredate")
      context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive{
    case ConfirmCheckoutCancelled =>
      log.debug("[CART INCHECKOUT] Checkout cancelled")
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      log.debug("[CART INCHECKOUT] Checkout closed")
      context become empty 
  }

}