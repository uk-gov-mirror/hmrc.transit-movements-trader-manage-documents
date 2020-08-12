/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.implicits._
import models.reference.AdditionalInformation
import models.reference.Country
import models.reference.DocumentType
import models.reference.KindOfPackage
import utils.DateFormatter
import utils.StringTransformer._

object UnloadingPermissionConverter extends Converter {

  def toViewModel(permission: models.PermissionToStartUnloading,
                  countries: Seq[Country],
                  additionalInfo: Seq[AdditionalInformation],
                  kindsOfPackage: Seq[KindOfPackage],
                  documentTypes: Seq[DocumentType]): ValidationResult[viewmodels.PermissionToStartUnloading] = {

    def convertTransportCountry(maybeCountry: Option[String]): ValidationResult[Option[Country]] =
      maybeCountry match {
        case Some(country) => findReferenceData[Country](country, countries, s"transportCountry").map(x => Some(x))
        case None          => Valid(None)
      }

    def convertConsignor(maybeConsignor: Option[models.Consignor]): ValidationResult[Option[viewmodels.Consignor]] =
      maybeConsignor match {
        case Some(consignor) => ConsignorConverter.toViewModel(consignor, s"consignor", countries).map(x => Some(x))
        case None            => Valid(None)
      }

    def convertConsignee(maybeConsignee: Option[models.Consignee]): ValidationResult[Option[viewmodels.Consignee]] =
      maybeConsignee match {
        case Some(consignee) => ConsigneeConverter.toViewModel(consignee, s"consignee", countries).map(x => Some(x))
        case None            => Valid(None)
      }

    def convertGoodsItems(items: NonEmptyList[models.GoodsItem]): ValidationResult[NonEmptyList[viewmodels.GoodsItem]] = {

      val head = GoodsItemConverter.toViewModel(items.head, "goodsItems[0]", countries, additionalInfo, kindsOfPackage, documentTypes)

      val tail = items.tail.zipWithIndex.map {
        case (item, index) =>
          GoodsItemConverter.toViewModel(item, s"goodsItems[${index + 1}", countries, additionalInfo, kindsOfPackage, documentTypes)
      }.sequence

      (
        head,
        tail
      ).mapN(
        (head, tail) => NonEmptyList(head, tail)
      )
    }

    (
      PrincipalConverter.toViewModel(permission.principal, "principal", countries),
      TraderConverter.toViewModel(permission.traderAtDestination, "traderAtDestination", countries),
      convertTransportCountry(permission.transportCountry),
      convertGoodsItems(permission.goodsItems),
      convertConsignor(permission.consignor),
      convertConsignee(permission.consignee)
    ).mapN(
      (principal, trader, transportCountry, goodsItems, consignor, consignee) =>
        viewmodels.PermissionToStartUnloading(
          permission.movementReferenceNumber,
          permission.declarationType,
          permission.transportIdentity,
          transportCountry,
          permission.acceptanceDate,
          DateFormatter.dateFormatted(permission.acceptanceDate, "dd/MM/yyyy"),
          permission.numberOfItems,
          permission.numberOfPackages,
          permission.grossMass,
          principal,
          consignor,
          consignee,
          trader,
          permission.departureOffice,
          permission.departureOffice.shorten(45)("***"),
          permission.presentationOffice,
          permission.seals,
          goodsItems
      )
    )
  }
}
