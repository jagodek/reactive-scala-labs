package EShop.lab5

import EShop.lab2.TypedCheckout
import EShop.lab3.OrderManager
import EShop.lab5.Payment.{PaymentRejected, WrappedPaymentServiceResponse}
import EShop.lab5.PaymentService.{
  PaymentClientError,
  PaymentServerError,
  PaymentSucceeded,
  Response
}
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.StreamTcpException

import scala.concurrent.duration._
import akka.actor.typed.Terminated

object Payment {
  sealed trait Message
  case object DoPayment extends Message
  case class WrappedPaymentServiceResponse(response: PaymentService.Response)
      extends Message

  sealed trait Response
  case object PaymentRejected extends Response

  val restartStrategy = SupervisorStrategy.restart
    .withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  def apply(
      method: String,
      orderManager: ActorRef[OrderManager.Command],
      checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Message] =
    Behaviors
      .receive[Message](
        (context, msg) => {
          val messageAdapter = context.messageAdapter[PaymentService.Response](
            WrappedPaymentServiceResponse.apply)
          msg match {
            case DoPayment =>
              val supervisedPaymentService = Behaviors
                .supervise(PaymentService(method, messageAdapter))
                .onFailure[Exception](restartStrategy)
              val paymentServiceActor =
                context.spawn(supervisedPaymentService, "paymentServiceActor")
              context.watch(paymentServiceActor)
              Behaviors.same

            case WrappedPaymentServiceResponse(response) =>
              response match {
                case PaymentSucceeded =>
                  orderManager ! OrderManager.ConfirmPaymentReceived
                  checkout ! TypedCheckout.ConfirmPaymentReceived
                  Behaviors.stopped
                case _ =>
                  throw PaymentServerError()
              }

          }
        }
      )
      .receiveSignal {
        case (context, Terminated(t)) =>
          notifyAboutRejection(orderManager, checkout)
          Behaviors.stopped
      }

  // please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(
      orderManager: ActorRef[OrderManager.Command],
      checkout: ActorRef[TypedCheckout.Command]
  ): Unit = {
    orderManager ! OrderManager.PaymentRejected
    checkout ! TypedCheckout.PaymentRejected
  }

}
