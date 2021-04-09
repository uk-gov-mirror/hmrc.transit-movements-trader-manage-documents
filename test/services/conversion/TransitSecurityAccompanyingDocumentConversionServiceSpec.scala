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

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.implicits._
import cats.scalatest.ValidatedMatchers
import cats.scalatest.ValidatedValues
import connectors.ReferenceDataConnector
import generators.ModelGenerators
import models._
import models.reference._
import org.mockito.Matchers.any
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.FreeSpec
import org.scalatest.MustMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import services.ReferenceDataNotFound
import services.ReferenceDataRetrievalError
import services.ValidationResult
import uk.gov.hmrc.http.HeaderCarrier
import utils.FormattedDate
import utils.StringTransformer._
import viewmodels.CustomsOfficeWithOptionalDate
import viewmodels.PreviousDocumentType

import java.time.LocalDate
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransitSecurityAccompanyingDocumentConversionServiceSpec
    extends FreeSpec
    with MustMatchers
    with MockitoSugar
    with ValidatedMatchers
    with ValidatedValues
    with ScalaFutures
    with IntegrationPatience
    with ModelGenerators
    with ScalaCheckPropertyChecks {

  private val countries                 = Seq(Country("valid", "AA", "Country A"), Country("valid", "BB", "Country B"))
  private val kindsOfPackage            = Seq(KindOfPackage("P1", "Package 1"), KindOfPackage("P2", "Package 2"))
  private val documentTypes             = Seq(DocumentType("T1", "Document 1", transportDocument = true), DocumentType("T2", "Document 2", transportDocument = false))
  private val additionalInfo            = Seq(AdditionalInformation("I1", "Info 1"), AdditionalInformation("I2", "info 2"))
  private val sensitiveGoodsInformation = Nil
  private val departureOffice           = CustomsOffice("AB124", Some("Departure Office"), "AB")
  private val destinationOffice         = CustomsOffice("AB125", Some("Destination Office"), "AB")
  private val transitOffices            = CustomsOffice("AB123", Some("Transit Office"), "AB")
  private val previousDocumentTypes     = Seq(PreviousDocumentTypes("123", "Some Description"), PreviousDocumentTypes("124", "Some Description2"))
  private val circumstanceIndicators    = Seq(CircumstanceIndicator("E", "indicator 1"), CircumstanceIndicator("D", "indicator 2"))

  private val controlResult = Some(viewmodels.ControlResult(ControlResultData("code", "description a2"), ControlResult("code", LocalDate.of(1990, 2, 3))))

  implicit private val hc: HeaderCarrier = HeaderCarrier()

  private val specialMentionEc                 = models.SpecialMention(None, additionalInfo.head.code, Some(true), None)
  private val specialMentionEcViewModel        = viewmodels.SpecialMention(additionalInfo.head, specialMentionEc)
  private val specialMentionNonEc              = models.SpecialMention(None, additionalInfo.head.code, None, countries.headOption.map(_.code))
  private val specialMentionNonEcViewModel     = viewmodels.SpecialMention(additionalInfo.head, specialMentionNonEc)
  private val specialMentionNoCountry          = models.SpecialMention(Some("Description"), additionalInfo.head.code, None, None)
  private val specialMentionNoCountryViewModel = viewmodels.SpecialMention(additionalInfo.head, specialMentionNoCountry)

  val validModel = models.ReleaseForTransit(
    movementReferenceNumber = "mrn",
    declarationType = DeclarationType.T1,
    countryOfDispatch = Some(countries.head.code),
    countryOfDestination = Some(countries.head.code),
    transportIdentity = Some("identity"),
    transportCountry = Some(countries.head.code),
    acceptanceDate = LocalDate.of(2020, 1, 1),
    numberOfItems = 1,
    numberOfPackages = Some(3),
    grossMass = 1.0,
    printBindingItinerary = true,
    authId = Some("AuthId"),
    returnCopy = false,
    circumstanceIndicator = None,
    security = None,
    commercialReferenceNumber = None,
    methodOfPayment = None,
    principal =
      models.Principal("Principal name", "Principal street", "Principal postCode", "Principal city", countries.head.code, Some("Principal EORI"), Some("tir")),
    consignor = Some(models.Consignor("consignor name", "consignor street", "consignor postCode", "consignor city", countries.head.code, None, None)),
    consignee = Some(models.Consignee("consignee name", "consignee street", "consignee postCode", "consignee city", countries.head.code, None, None)),
    customsOfficeOfTransit = Seq(
      CustomsOfficeOfTransit("AB123", Some(LocalDateTime.of(2020, 1, 1, 1, 1)))
    ),
    guaranteeDetails = NonEmptyList.one(
      GuaranteeDetails("A", Seq(GuaranteeReference(Some("RefNum"), None, None, Nil)))
    ),
    departureOffice = "AB124",
    destinationOffice = "AB125",
    returnCopiesCustomsOffice = None,
    controlResult = Some(ControlResult("code", LocalDate.of(1990, 2, 3))),
    seals = Seq("seal 1"),
    goodsItems = NonEmptyList.one(
      models.GoodsItem(
        itemNumber = 1,
        commodityCode = None,
        declarationType = None,
        description = "Description",
        grossMass = Some(1.0),
        netMass = Some(0.9),
        countryOfDispatch = Some(countries.head.code),
        countryOfDestination = Some(countries.head.code),
        previousAdminRef = Seq(
          PreviousAdministrativeReference("123", "ABABA", None)
        ),
        producedDocuments = Seq(models.ProducedDocument(documentTypes.head.code, None, None)),
        specialMentions = Seq(
          specialMentionEc,
          specialMentionNonEc,
          specialMentionNoCountry
        ),
        consignor = Some(models.Consignor("consignor name", "consignor street", "consignor postCode", "consignor city", countries.head.code, None, None)),
        consignee = Some(models.Consignee("consignee name", "consignee street", "consignee postCode", "consignee city", countries.head.code, None, None)),
        containers = Seq("container 1"),
        packages = NonEmptyList(
          models.BulkPackage(kindsOfPackage.head.code, Some("numbers")),
          List(
            models.UnpackedPackage(kindsOfPackage.head.code, 1, Some("marks")),
            models.RegularPackage(kindsOfPackage.head.code, 1, "marks and numbers")
          )
        ),
        sensitiveGoodsInformation = sensitiveGoodsInformation
      )
    )
  )

  "toViewModel" - {

    "must return a view model" - {

      "all data exists" in {

        forAll(arbitrary[Country], arbitrary[ReleaseForTransit], arbitrary[Consignor], arbitrary[Consignee], stringWithMaxLength(17)) {
          (countriesGen, releaseForTransitGen, consignorGen, consigneeGen, mrn) =>
            val referenceDataService = mock[ReferenceDataConnector]
            when(referenceDataService.countries()(any(), any())) thenReturn Future.successful(Valid(Seq(countriesGen, Country("valid", "AA", "Country A"))))
            when(referenceDataService.kindsOfPackage()(any(), any())) thenReturn Future.successful(Valid(kindsOfPackage))
            when(referenceDataService.documentTypes()(any(), any())) thenReturn Future.successful(Valid(documentTypes))
            when(referenceDataService.additionalInformation()(any(), any())) thenReturn Future.successful(Valid(additionalInfo))
            when(referenceDataService.previousDocumentTypes()(any(), any())) thenReturn Future.successful(Valid(previousDocumentTypes))
            when(referenceDataService.controlResultByCode(any())(any(), any())) thenReturn Future.successful(controlResult.get.controlResultData)
            when(referenceDataService.circumstanceIndicators()(any(), any())) thenReturn Future.successful(Valid(circumstanceIndicators))

            when(referenceDataService.customsOfficeSearch(eqTo("AB123"))(any(), any())) thenReturn Future.successful(
              CustomsOffice("AB123", Some("Transit Office"), "AB"))
            when(referenceDataService.customsOfficeSearch(eqTo("AB124"))(any(), any())) thenReturn Future.successful(
              CustomsOffice("AB124", Some("Departure Office"), "AB"))
            when(referenceDataService.customsOfficeSearch(eqTo("AB125"))(any(), any())) thenReturn Future.successful(
              CustomsOffice("AB125", Some("Destination Office"), "AB"))

            val consignorGenUpdated = Some(consignorGen.copy(countryCode = "AA"))
            val consigneeGenUpdated = Some(consigneeGen.copy(countryCode = "AA"))

            val releaseForTransit =
              releaseForTransitGen.copy(
                principal = releaseForTransitGen.principal.copy(countryCode = countriesGen.code),
                consignor = consignorGenUpdated,
                consignee = consigneeGenUpdated,
                circumstanceIndicator = Some(circumstanceIndicators.head.code),
                security = releaseForTransitGen.security,
                returnCopiesCustomsOffice = releaseForTransitGen.returnCopiesCustomsOffice.map(_.copy(countryCode = countriesGen.code))
              )

            val validModelUpdated = validModel.copy(
              declarationType = releaseForTransit.declarationType,
              countryOfDispatch = Some(countriesGen.code),
              countryOfDestination = Some(countriesGen.code),
              transportIdentity = releaseForTransit.transportIdentity,
              transportCountry = Some(countriesGen.code),
              numberOfItems = releaseForTransit.numberOfItems,
              numberOfPackages = releaseForTransit.numberOfPackages,
              grossMass = releaseForTransit.grossMass,
              principal = releaseForTransit.principal,
              consignor = releaseForTransit.consignor,
              consignee = releaseForTransit.consignee,
              controlResult = releaseForTransit.controlResult,
              returnCopiesCustomsOffice = releaseForTransit.returnCopiesCustomsOffice,
              seals = releaseForTransit.seals,
              circumstanceIndicator = releaseForTransit.circumstanceIndicator,
              security = releaseForTransit.security,
              commercialReferenceNumber = releaseForTransit.commercialReferenceNumber,
              methodOfPayment = releaseForTransit.methodOfPayment
            )

            val expectedResult = viewmodels.TransitSecurityAccompanyingDocumentPDF(
              movementReferenceNumber = validModelUpdated.movementReferenceNumber,
              declarationType = releaseForTransit.declarationType,
              singleCountryOfDispatch = Some(countriesGen),
              singleCountryOfDestination = Some(countriesGen),
              transportIdentity = releaseForTransit.transportIdentity,
              transportCountry = Some(countriesGen),
              acceptanceDate = Some(FormattedDate(validModel.acceptanceDate)),
              numberOfItems = releaseForTransit.numberOfItems,
              numberOfPackages = releaseForTransit.numberOfPackages,
              grossMass = releaseForTransit.grossMass,
              printBindingItinerary = validModel.printBindingItinerary,
              authId = validModel.authId,
              copyType = validModel.returnCopy,
              circumstanceIndicator = validModelUpdated.circumstanceIndicator,
              security = validModelUpdated.security,
              commercialReferenceNumber = validModelUpdated.commercialReferenceNumber,
              methodOfPayment = validModelUpdated.methodOfPayment,
              principal = viewmodels.Principal(
                releaseForTransit.principal.name,
                releaseForTransit.principal.streetAndNumber,
                releaseForTransit.principal.streetAndNumber.shorten(32)("***"),
                releaseForTransit.principal.postCode,
                releaseForTransit.principal.city,
                countriesGen,
                releaseForTransit.principal.eori,
                releaseForTransit.principal.tir
              ),
              consignor = Some(
                viewmodels.Consignor(
                  releaseForTransit.consignor.get.name,
                  releaseForTransit.consignor.get.streetAndNumber,
                  releaseForTransit.consignor.get.streetAndNumber.shorten(32)("***"),
                  releaseForTransit.consignor.get.postCode,
                  releaseForTransit.consignor.get.city,
                  countries.head,
                  releaseForTransit.consignor.get.eori
                )),
              consignee = Some(
                viewmodels.Consignee(
                  releaseForTransit.consignee.get.name,
                  releaseForTransit.consignee.get.streetAndNumber,
                  releaseForTransit.consignee.get.streetAndNumber.shorten(32)("***"),
                  releaseForTransit.consignee.get.postCode,
                  releaseForTransit.consignee.get.city,
                  countries.head,
                  releaseForTransit.consignee.get.eori
                )),
              departureOffice = CustomsOfficeWithOptionalDate(departureOffice, None),
              destinationOffice = CustomsOfficeWithOptionalDate(destinationOffice, None),
              customsOfficeOfTransit = validModel.customsOfficeOfTransit.map(transit => CustomsOfficeWithOptionalDate(transitOffices, transit.arrivalTime, 18)),
              returnCopiesCustomsOffice = releaseForTransit.returnCopiesCustomsOffice.map(office =>
                viewmodels
                  .ReturnCopiesCustomsOffice(office.customsOfficeName, office.streetAndNumber.shorten(32)("***"), office.postCode, office.city, countriesGen)),
              controlResult = releaseForTransit.controlResult.flatMap(cr => controlResult.map(_.copy(controlResult = cr))),
              guaranteeDetails = validModel.guaranteeDetails.toList,
              seals = releaseForTransit.seals,
              goodsItems = NonEmptyList.one(
                viewmodels.GoodsItem(
                  itemNumber = 1,
                  commodityCode = None,
                  declarationType = None,
                  description = "Description",
                  grossMass = Some(1.0),
                  netMass = Some(0.9),
                  countryOfDispatch = Some(countries.head),
                  countryOfDestination = Some(countries.head),
                  producedDocuments = Seq(viewmodels.ProducedDocument(documentTypes.head, None, None)),
                  previousDocumentTypes =
                    validModel.goodsItems.head.previousAdminRef.map(ref => PreviousDocumentType(PreviousDocumentTypes("123", "Some Description"), ref)),
                  specialMentions = Seq(
                    specialMentionEcViewModel,
                    specialMentionNonEcViewModel,
                    specialMentionNoCountryViewModel
                  ),
                  consignor = Some(viewmodels
                    .Consignor("consignor name", "consignor street", "consignor street", "consignor postCode", "consignor city", countries.head, None)),
                  consignee = Some(viewmodels
                    .Consignee("consignee name", "consignee street", "consignee street", "consignee postCode", "consignee city", countries.head, None)),
                  containers = Seq("container 1"),
                  packages = NonEmptyList(
                    viewmodels.BulkPackage(kindsOfPackage.head, Some("numbers")),
                    List(
                      viewmodels.UnpackedPackage(kindsOfPackage.head, 1, Some("marks")),
                      viewmodels.RegularPackage(kindsOfPackage.head, 1, "marks and numbers")
                    )
                  ),
                  sensitiveGoodsInformation = sensitiveGoodsInformation
                )
              )
            )

            val service = new TransitSecurityAccompanyingDocumentConversionService(referenceDataService)

            val result: ValidationResult[viewmodels.TransitSecurityAccompanyingDocumentPDF] = service.toViewModel(validModelUpdated).futureValue
            result.valid.value mustEqual expectedResult
        }
      }

      "mandatory data exists" in {

        forAll(arbitrary[Country], arbitrary[ReleaseForTransit], stringWithMaxLength(17)) {
          (countriesGen, releaseForTransit, mrn) =>
            val referenceDataConnector = mock[ReferenceDataConnector]
            when(referenceDataConnector.countries()(any(), any())) thenReturn Future.successful(Valid(Seq(countriesGen, Country("valid", "AA", "Country A"))))
            when(referenceDataConnector.kindsOfPackage()(any(), any())) thenReturn Future.successful(Valid(kindsOfPackage))
            when(referenceDataConnector.documentTypes()(any(), any())) thenReturn Future.successful(Valid(documentTypes))
            when(referenceDataConnector.additionalInformation()(any(), any())) thenReturn Future.successful(Valid(additionalInfo))
            when(referenceDataConnector.previousDocumentTypes()(any(), any())) thenReturn Future.successful(Valid(previousDocumentTypes))
            when(referenceDataConnector.circumstanceIndicators()(any(), any())) thenReturn Future.successful(Valid(circumstanceIndicators))

            when(referenceDataConnector.customsOfficeSearch(eqTo("AB124"))(any(), any())) thenReturn Future.successful(
              CustomsOffice("AB124", Some("Departure Office"), "AB"))
            when(referenceDataConnector.customsOfficeSearch(eqTo("AB125"))(any(), any())) thenReturn Future.successful(
              CustomsOffice("AB125", Some("Destination Office"), "AB"))

            val service = new TransitSecurityAccompanyingDocumentConversionService(referenceDataConnector)

            val transitSecurityAccompanyingDocument =
              releaseForTransit.copy(principal = releaseForTransit.principal.copy(countryCode = countriesGen.code))

            val validModelUpdated = validModel.copy(
              declarationType = transitSecurityAccompanyingDocument.declarationType,
              countryOfDispatch = None,
              countryOfDestination = None,
              transportIdentity = None,
              transportCountry = None,
              numberOfItems = transitSecurityAccompanyingDocument.numberOfItems,
              numberOfPackages = None,
              grossMass = transitSecurityAccompanyingDocument.grossMass,
              printBindingItinerary = transitSecurityAccompanyingDocument.printBindingItinerary,
              authId = None,
              returnCopy = transitSecurityAccompanyingDocument.returnCopy,
              circumstanceIndicator = None,
              principal = transitSecurityAccompanyingDocument.principal,
              consignor = None,
              consignee = None,
              customsOfficeOfTransit = Nil,
              departureOffice = validModel.departureOffice,
              destinationOffice = validModel.destinationOffice,
              controlResult = None,
              goodsItems = validModel.goodsItems
                .map(_.copy(previousAdminRef = Nil)),
              seals = Nil
            )

            val expectedResult = viewmodels.TransitSecurityAccompanyingDocumentPDF(
              movementReferenceNumber = validModel.movementReferenceNumber,
              declarationType = transitSecurityAccompanyingDocument.declarationType,
              singleCountryOfDispatch = None,
              singleCountryOfDestination = None,
              transportIdentity = None,
              transportCountry = None,
              acceptanceDate = Some(FormattedDate(validModel.acceptanceDate)),
              numberOfItems = transitSecurityAccompanyingDocument.numberOfItems,
              numberOfPackages = None,
              grossMass = transitSecurityAccompanyingDocument.grossMass,
              authId = None,
              copyType = transitSecurityAccompanyingDocument.returnCopy,
              circumstanceIndicator = None,
              security = None,
              commercialReferenceNumber = None,
              methodOfPayment = None,
              printBindingItinerary = transitSecurityAccompanyingDocument.printBindingItinerary,
              principal = viewmodels.Principal(
                transitSecurityAccompanyingDocument.principal.name,
                transitSecurityAccompanyingDocument.principal.streetAndNumber,
                transitSecurityAccompanyingDocument.principal.streetAndNumber.shorten(32)("***"),
                transitSecurityAccompanyingDocument.principal.postCode,
                transitSecurityAccompanyingDocument.principal.city,
                countriesGen,
                transitSecurityAccompanyingDocument.principal.eori,
                transitSecurityAccompanyingDocument.principal.tir
              ),
              consignor = None,
              consignee = None,
              departureOffice = CustomsOfficeWithOptionalDate(departureOffice, None),
              destinationOffice = CustomsOfficeWithOptionalDate(destinationOffice, None),
              customsOfficeOfTransit = Nil,
              seals = Nil,
              guaranteeDetails = validModel.guaranteeDetails.toList,
              returnCopiesCustomsOffice = None,
              controlResult = None,
              goodsItems = NonEmptyList.one(
                viewmodels.GoodsItem(
                  itemNumber = 1,
                  commodityCode = None,
                  declarationType = None,
                  description = "Description",
                  grossMass = Some(1.0),
                  netMass = Some(0.9),
                  countryOfDispatch = Some(countries.head),
                  countryOfDestination = Some(countries.head),
                  producedDocuments = Seq(viewmodels.ProducedDocument(documentTypes.head, None, None)),
                  previousDocumentTypes = Nil,
                  specialMentions = Seq(
                    specialMentionEcViewModel,
                    specialMentionNonEcViewModel,
                    specialMentionNoCountryViewModel
                  ),
                  consignor = Some(viewmodels
                    .Consignor("consignor name", "consignor street", "consignor street", "consignor postCode", "consignor city", countries.head, None)),
                  consignee = Some(viewmodels
                    .Consignee("consignee name", "consignee street", "consignee street", "consignee postCode", "consignee city", countries.head, None)),
                  containers = Seq("container 1"),
                  packages = NonEmptyList(
                    viewmodels.BulkPackage(kindsOfPackage.head, Some("numbers")),
                    List(
                      viewmodels.UnpackedPackage(kindsOfPackage.head, 1, Some("marks")),
                      viewmodels.RegularPackage(kindsOfPackage.head, 1, "marks and numbers")
                    )
                  ),
                  sensitiveGoodsInformation = sensitiveGoodsInformation
                )
              )
            )

            val result: ValidationResult[viewmodels.TransitSecurityAccompanyingDocumentPDF] = service.toViewModel(validModelUpdated).futureValue

            result.valid.value mustEqual expectedResult
        }
      }
    }

    "must return errors when reference data cannot be retrieved" in {

      val referenceDataConnector = mock[ReferenceDataConnector]

      when(referenceDataConnector.countries()(any(), any()))
        .thenReturn(Future.successful(ReferenceDataRetrievalError("countries", 500, "body").invalidNec))

      when(referenceDataConnector.kindsOfPackage()(any(), any()))
        .thenReturn(Future.successful(ReferenceDataRetrievalError("kindsOfPackage", 501, "body").invalidNec))

      when(referenceDataConnector.documentTypes()(any(), any()))
        .thenReturn(Future.successful(ReferenceDataRetrievalError("documentTypes", 502, "body").invalidNec))

      when(referenceDataConnector.additionalInformation()(any(), any()))
        .thenReturn(Future.successful(ReferenceDataRetrievalError("additionalInformation", 503, "body").invalidNec))

      when(referenceDataConnector.previousDocumentTypes()(any(), any())) thenReturn Future.successful(Valid(previousDocumentTypes))
      when(referenceDataConnector.circumstanceIndicators()(any(), any())) thenReturn Future.successful(Valid(circumstanceIndicators))

      when(referenceDataConnector.controlResultByCode(any())(any(), any())) thenReturn Future.successful(controlResult.get.controlResultData)

      when(referenceDataConnector.customsOfficeSearch(eqTo("AB123"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB123", Some("Transit Office"), "AB"))
      when(referenceDataConnector.customsOfficeSearch(eqTo("AB124"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB124", Some("Departure Office"), "AB"))
      when(referenceDataConnector.customsOfficeSearch(eqTo("AB125"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB125", Some("Destination Office"), "AB"))

      val service = new TransitSecurityAccompanyingDocumentConversionService(referenceDataConnector)

      val result = service.toViewModel(validModel).futureValue

      val expectedErrors = Seq(
        ReferenceDataRetrievalError("countries", 500, "body"),
        ReferenceDataRetrievalError("kindsOfPackage", 501, "body"),
        ReferenceDataRetrievalError("documentTypes", 502, "body"),
        ReferenceDataRetrievalError("additionalInformation", 503, "body")
      )

      result.invalidValue.toChain.toList must contain theSameElementsAs expectedErrors
    }

    "must return errors when reference data can be retrieved but the conversion fails" in {

      val referenceDataConnector = mock[ReferenceDataConnector]
      when(referenceDataConnector.countries()(any(), any())) thenReturn Future.successful(Valid(countries))
      when(referenceDataConnector.kindsOfPackage()(any(), any())) thenReturn Future.successful(Valid(kindsOfPackage))
      when(referenceDataConnector.documentTypes()(any(), any())) thenReturn Future.successful(Valid(documentTypes))
      when(referenceDataConnector.additionalInformation()(any(), any())) thenReturn Future.successful(Valid(additionalInfo))
      when(referenceDataConnector.previousDocumentTypes()(any(), any())) thenReturn Future.successful(Valid(previousDocumentTypes))
      when(referenceDataConnector.circumstanceIndicators()(any(), any())) thenReturn Future.successful(Valid(circumstanceIndicators))

      when(referenceDataConnector.controlResultByCode(any())(any(), any())) thenReturn Future.successful(controlResult.get.controlResultData)

      when(referenceDataConnector.customsOfficeSearch(eqTo("AB123"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB123", Some("Transit Office"), "AB"))
      when(referenceDataConnector.customsOfficeSearch(eqTo("AB124"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB124", Some("Departure Office"), "AB"))
      when(referenceDataConnector.customsOfficeSearch(eqTo("AB125"))(any(), any())) thenReturn Future.successful(
        CustomsOffice("AB125", Some("Destination Office"), "AB"))

      val service = new TransitSecurityAccompanyingDocumentConversionService(referenceDataConnector)

      val invalidRefData = validModel copy (countryOfDispatch = Some("non-existent code"))

      val result = service.toViewModel(invalidRefData).futureValue

      result.invalidValue.toChain.toList must contain theSameElementsAs Seq(ReferenceDataNotFound("countryOfDispatch", "non-existent code"))
    }
  }
}
