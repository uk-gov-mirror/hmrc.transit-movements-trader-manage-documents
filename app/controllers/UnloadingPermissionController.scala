/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import cats.data.Validated
import com.lucidchart.open.xtract.ParseFailure
import com.lucidchart.open.xtract.ParseSuccess
import javax.inject.Inject
import logging.Logging
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import services._
import services.conversion.UnloadingPermissionConversionService
import services.pdf.UnloadingPermissionPdfGenerator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class UnloadingPermissionController @Inject()(
  conversionService: UnloadingPermissionConversionService,
  pdf: UnloadingPermissionPdfGenerator,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def get(): Action[NodeSeq] = Action.async(parse.xml) {
    implicit request =>
      XMLToPermissionToStartUnloading.convert(request.body) match {
        case ParseSuccess(unloadingPermission) =>
          conversionService.toViewModel(unloadingPermission).map {
            case Validated.Valid(viewModel) => Ok(pdf.generate(viewModel))
            case Validated.Invalid(errors) =>
              logger.error(s"Failed to convert to UnloadingPermissionViewModel with following errors: $errors")

              InternalServerError
          }
        case ParseFailure(errors) =>
          logger.error(s"Failed to parse xml to UnloadingPermission with the following errors: $errors")

          Future.successful(BadRequest)
      }
  }
}
