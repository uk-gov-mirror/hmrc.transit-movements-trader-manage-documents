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

import cats.data.Validated.Valid
import cats.implicits._
import models.reference.AdditionalInformation
import models.reference.Country
import models.reference.DocumentType
import models.reference.KindOfPackage

object GoodsItemConverter extends Converter {

  def toViewModel(goodsItem: models.GoodsItem,
                  path: String,
                  countries: Seq[Country],
                  additionalInfo: Seq[AdditionalInformation],
                  kindsOfPackage: Seq[KindOfPackage],
                  documentTypes: Seq[DocumentType]): ValidationResult[viewmodels.GoodsItem] = {

    def convertDocuments(docs: Seq[models.ProducedDocument]): ValidationResult[List[viewmodels.ProducedDocument]] =
      docs.zipWithIndex
        .map {
          case (doc, index) =>
            ProducedDocumentConverter.toViewModel(doc, s"$path.producedDocuments[$index]", documentTypes)
        }
        .toList
        .sequence

    def convertSpecialMentions(mentions: Seq[models.SpecialMention]): ValidationResult[List[viewmodels.SpecialMention]] =
      mentions.zipWithIndex
        .map {
          case (sm, index) =>
            SpecialMentionConverter.toViewModel(sm, s"$path.specialMentions[$index]", additionalInfo, countries)
        }
        .toList
        .sequence

    def convertPackages(packages: Seq[models.Package]): ValidationResult[List[viewmodels.Package]] =
      packages.zipWithIndex
        .map {
          case (pkg, index) =>
            PackageConverter.toViewModel(pkg, s"$path.packages[$index]", kindsOfPackage)
        }
        .toList
        .sequence

    def convertConsignor(maybeConsignor: Option[models.Consignor]): ValidationResult[Option[viewmodels.Consignor]] =
      maybeConsignor match {
        case Some(consignor) => ConsignorConverter.toViewModel(consignor, s"$path.consignor", countries).map(x => Some(x))
        case None            => Valid(None)
      }

    def convertConsignee(maybeConsignee: Option[models.Consignee]): ValidationResult[Option[viewmodels.Consignee]] =
      maybeConsignee match {
        case Some(consignee) => ConsigneeConverter.toViewModel(consignee, s"$path.consignee", countries).map(x => Some(x))
        case None            => Valid(None)
      }

    (
      findReferenceData(goodsItem.countryOfDispatch, countries, s"$path.countryOfDispatch"),
      findReferenceData(goodsItem.countryOfDestination, countries, s"$path.countryOfDestination"),
      convertDocuments(goodsItem.producedDocuments),
      convertSpecialMentions(goodsItem.specialMentions),
      convertPackages(goodsItem.packages),
      convertConsignor(goodsItem.consignor),
      convertConsignee(goodsItem.consignee)
    ).mapN(
      (dispatch, destination, docs, mentions, packages, consignor, consignee) =>
        viewmodels.GoodsItem(
          goodsItem.itemNumber,
          goodsItem.commodityCode,
          goodsItem.declarationType,
          goodsItem.description,
          goodsItem.grossMass,
          goodsItem.netMass,
          dispatch,
          destination,
          docs,
          mentions,
          consignor,
          consignee,
          goodsItem.containers,
          packages
      )
    )
  }
}
