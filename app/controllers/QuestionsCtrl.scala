package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current

import play.api.libs.json._

// Akka imports
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.{Props, ActorSystem}
import scala.language.postfixOps

import models._
import models.QuestionsActor.{QuestionRetrieve, QuestionAdd, QuestionUpdate, QuestionDelete}
import models.QuestionsActor.{QuestionAttrsAppend, QuestionAttrsRemove, QuestionAttrsClean}
import models.QuestionResultStatus._

import globals._
     
object QuestionsCtrl extends Controller with QuestionsJSONTrait {
    implicit val timeout = Timeout(5 seconds)
    implicit lazy val system = ActorSystem()
    implicit lazy val qActor = system.actorOf(Props[QuestionsActor])

    def retrieve(id: Long) = Action {
        var retOpt = None: Option[QuestionResult]

        val future = qActor ? QuestionRetrieve(id)
        retOpt = Await.result(future, timeout.duration)
                    .asInstanceOf[Option[QuestionResult]]

        if (retOpt.isDefined) {
            val questionResult = retOpt.get
            questionResult.status match {
                case Some(200) => {
                    Status(QUESTION_OK.get)(Json.toJson(retOpt.get))
                }
                case _ => {
                    Status(questionResult.status.get)
                }
            }
        } else {
            InternalServerError
        }
    }

    def action = Action { request =>
        var retOpt = None: Option[QuestionResult]

        val reqJSON = request.body.asJson.get
        // parse the handler
        val handler = (reqJSON \ "handler").asOpt[String]
        val questionJSON = (reqJSON \ "question").asOpt[JsValue]
        if (handler.isDefined && questionJSON.isDefined) {
            var questionOptParseFromJSON = None: Option[Question]
            // validate the json data to scala class
            questionJSON.get.validate[Question] match {
                case s: JsSuccess[Question] => {
                    questionOptParseFromJSON = Option(s.get)
                }
                case e: JsError => {
                    // error handling flow
                }
            }

            // match the handler with the different actor methods
            if (questionOptParseFromJSON.isDefined) {
                val question = questionOptParseFromJSON.get

                handler match {
                    case Some("whipper.questions.add") => {
                        val future = qActor ? QuestionAdd(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case Some("whipper.questions.update") => {
                        val future = qActor ? QuestionUpdate(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case Some("whipper.questions.delete") => {
                        val future = qActor ? QuestionDelete(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case Some("whipper.questions.attrs.append") => {
                        val future = qActor ? QuestionAttrsAppend(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case Some("whipper.questions.attrs.remove") => {
                        val future = qActor ? QuestionAttrsRemove(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case Some("whipper.questions.attrs.clean") => {
                        val future = qActor ? QuestionAttrsClean(
                                    question)
                        retOpt = Await.result(future, timeout.duration)
                                .asInstanceOf[Option[QuestionResult]]
                    }
                    case _ => {}
                }
            }
        }

        if (retOpt.isDefined) {
            val questionResult = retOpt.get
            questionResult.status match {
                case Some(200) => {
                    Status(QUESTION_OK.get)(Json.toJson(retOpt.get))
                }
                case _ => {
                    Status(questionResult.status.get)
                }
            }
        } else {
            InternalServerError
        }
    }
}