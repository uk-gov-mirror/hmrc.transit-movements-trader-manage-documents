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

package services.conversion

import cats.data.Validated.Invalid

import javax.inject.Inject
import services.ReferenceDataService
import services.ValidationResult
import uk.gov.hmrc.http.HeaderCarrier
import cats.implicits._
import viewmodels.CustomsOfficeWithOptionalDate

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class TransitAccompanyingDocumentConversionService @Inject()(referenceData: ReferenceDataService) {

  //TODO: Rename PermissionToStartUnloading view model
  /*
   * The TAD/UL xsd files are identical, both documents share same structure
   * There's no point having separate templates under views
   * One view model can hold all the required data which can be used to build a document
   * Let each Converter handle what goes in the view model
   */
  def toViewModel(transitAccompanyingDocument: models.TransitAccompanyingDocument,
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[ValidationResult[viewmodels.TransitAccompanyingDocumentPDF]] = {

    val countriesFuture      = referenceData.countries()
    val additionalInfoFuture = referenceData.additionalInformation()
    val kindsOfPackageFuture = referenceData.kindsOfPackage()
    val documentTypesFuture  = referenceData.documentTypes()
    val departureOfficeFuture = referenceData
      .customsOfficeSearch(transitAccompanyingDocument.departureOffice)
      .map(
        office => CustomsOfficeWithOptionalDate(office, None)
      )
    val destinationOfficeFuture = referenceData
      .customsOfficeSearch(transitAccompanyingDocument.destinationOffice)
      .map(
        office => CustomsOfficeWithOptionalDate(office, None)
      )
    val transitOfficeFuture = Future.sequence(
      transitAccompanyingDocument.customsOfficeOfTransit.map(
        office =>
          referenceData
            .customsOfficeSearch(office.reference)
            .map(customsOffice => CustomsOfficeWithOptionalDate(customsOffice, Some(office.arrivalTime), maxLength = 18))
      ))

    //TODO: ref data will be needed below when we're building out the view model
    //    val transportMode  = referenceData.transportMode()
    //    val controlResultCode = referenceData.controlResult()
    //    val previousDocumentTypesFuture  = referenceData.previousDocumentTypes()
    //    val sensitiveGoodsCodeFuture = referenceData.sensitiveGoodsCode()
    //    val specialMentionsCodeFuture = referenceData.specialMentions()

    for {
      countriesResult      <- countriesFuture
      additionalInfoResult <- additionalInfoFuture
      kindsOfPackageResult <- kindsOfPackageFuture
      documentTypesResult  <- documentTypesFuture
      departureOffice      <- departureOfficeFuture
      destinationOffice    <- destinationOfficeFuture
      transitOffice        <- transitOfficeFuture
    } yield {
      (
        countriesResult,
        additionalInfoResult,
        kindsOfPackageResult,
        documentTypesResult
      ).mapN(
          (countries, additionalInfo, kindsOfPackage, documentTypes) =>
            TransitAccompanyingDocumentConverter.toViewModel(
              transitAccompanyingDocument,
              countries,
              additionalInfo,
              kindsOfPackage,
              documentTypes,
              departureOffice,
              destinationOffice,
              transitOffice
          )
        )
        .fold(
          errors => Invalid(errors),
          result => result
        )
    }
  }
}
