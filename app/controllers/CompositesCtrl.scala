package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current
import scala.language.postfixOps

import play.api.libs.json._

// Akka imports
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}

import net.liftweb.json._
import net.liftweb.json.Serialization.write

import models._
import models.CompositesActor.{CompositeRetrieve}
import models.CompositeResultStatus._

import globals._
import utils._

object CompositesCtrl extends Controller 
    with CompositesJSONTrait with QuestionsJSONTrait {

    implicit val timeout = Timeout(5 seconds)
    implicit lazy val system = ActorSystem()
    implicit lazy val compositeActor = system.actorOf(Props[CompositesActor])

    def retrieve(id: Long) = Action {
        var retOpt = None: Option[CompositeResult]

        val future = compositeActor ? CompositeRetrieve(id)
        retOpt = Await.result(future, timeout.duration)
                    .asInstanceOf[Option[CompositeResult]]

        if (retOpt.isDefined) {
            val compositeResult = retOpt.get
            compositeResult.status match {
                case Some(200) => {
                    Status(COMPOSITE_OK.get)(Json.toJson(retOpt.get))
                }
                case _ => {
                    Status(compositeResult.status.get)
                }
            }
        } else {
            InternalServerError
        }
    }

    def struct(id: Long) = Action {
        var retStructUnfoldComposite = None: Option[List[StructElemEx]]
        implicit val formats = DefaultFormats

        val compositeFuture = compositeActor ? CompositeRetrieve(id)
        val compositeResult = Await.result(compositeFuture, timeout.duration)
                                .asInstanceOf[Option[CompositeResult]]
        if (compositeResult.isDefined) {
            compositeResult.get.status match {
                case Some(200) => {
                    val composite = compositeResult.get.composite.get
                    if (composite.struct.isDefined) {
                        val structListComposite = composite.struct.get
                        retStructUnfoldComposite = StructElem.unfoldListIterate(structListComposite)
                    }
                }
                case _ => {}
            }
        }

        if (retStructUnfoldComposite.isDefined) {
            val retJSON = write(retStructUnfoldComposite.get)
            Status(200)(Json.parse(retJSON.toString))
        } else {
            InternalServerError
        }
    }

    def actionHandler(
        handlerOpt: Option[String],
        jsonOpt: Option[JsValue]): Option[CompositeResult] = {
        var retOpt = None: Option[CompositeResult]

        var compositeOptParseFromJSON = None: Option[Composite]
        jsonOpt.foreach { json =>
            // validate the json to scala object
            json.validate[Composite] match {
                case s: JsSuccess[Composite] => {
                    compositeOptParseFromJSON = Option(s.get)
                }
                case e: JsError => {
                    compositeOptParseFromJSON = None
                }
            }
        }

        compositeOptParseFromJSON.foreach { composite =>
            val request = CompositeRequest(handlerOpt)
            retOpt = request.send(composite)
        }

        retOpt
    }

    def actionAsync = Action { request =>
        val reqJSON = request.body.asJson.get

        val handlerOpt = (reqJSON \ "handler").asOpt[String]
        val compositeJSONOpt = (reqJSON \ "composite").asOpt[JsValue]

        compositeJSONOpt.foreach { compositeJSON =>
            val msgPack = new MsgWrapper(
                handlerOpt, Option(compositeJSONOpt.toString))
            val msgPackBytes = msgPack.toMsgPack

            val mqRequestHandler = HandlerMQFactory(
                Option("whipper.composites.mq"))
            mqRequestHandler.send(Option(msgPackBytes))
        }

        Status(200)
    }

    def action = Action { request =>
        var retOpt = None: Option[CompositeResult]

        val reqJSON = request.body.asJson.get
        val handlerOpt = (reqJSON \ "handler").asOpt[String]
        val compositeJSONOpt = (reqJSON \ "composite").asOpt[JsValue]

        retOpt = actionHandler(handlerOpt, compositeJSONOpt)

        if (retOpt.isDefined) {
            val compositeResult = retOpt.get
            compositeResult.status match {
                case Some(200) => {
                    Status(COMPOSITE_OK.get)(Json.toJson(retOpt.get))
                }
                case _ => {
                    Status(compositeResult.status.get)
                }
            }
        } else {
            InternalServerError
        }
    }
}