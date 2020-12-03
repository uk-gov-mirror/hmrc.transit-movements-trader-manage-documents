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

package models

import java.time.LocalDate

import cats.data.NonEmptyList
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.XmlReader
import com.lucidchart.open.xtract.__
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import cats.syntax.all._
import utils.NonEmptyListXMLReader.xmlNonEmptyListReads
import utils.BigDecimalXMLReader._
import utils.LocalDateXMLReader._
import json.NonEmptyListOps._

final case class TransitAccompanyingDocument(
  localReferenceNumber: String,
  declarationType: DeclarationType,
  countryOfDispatch: Option[String],
  countryOfDestination: Option[String],
  transportIdentity: Option[String],
  transportCountry: Option[String],
  acceptanceDate: LocalDate,
  numberOfItems: Int,
  numberOfPackages: Int,
  grossMass: BigDecimal,
  authorisationId: Option[String],
  principal: Principal,
  consignor: Option[Consignor],
  consignee: Option[Consignee],
  departureOffice: String,
  customsOfficeTransit: Seq[CustomsOfficeTransit],
  controlResult: Option[ControlResult],
  seals: Seq[String],
  goodsItems: NonEmptyList[GoodsItem]
)

object TransitAccompanyingDocument {

  implicit lazy val format: OFormat[TransitAccompanyingDocument] =
    Json.format[TransitAccompanyingDocument]

  implicit val xmlReader: XmlReader[TransitAccompanyingDocument] = {
    ((__ \ "HEAHEA" \ "RefNumHEA4").read[String],
     (__ \ "HEAHEA" \ "TypOfDecHEA24").read[DeclarationType],
     (__ \ "HEAHEA" \ "CouOfDisCodHEA55").read[String].optional,
     (__ \ "HEAHEA" \ "CouOfDesCodHEA30").read[String].optional,
     (__ \ "HEAHEA" \ "IdeOfMeaOfTraAtDHEA78").read[String].optional,
     (__ \ "HEAHEA" \ "NatOfMeaOfTraAtDHEA80").read[String].optional,
     (__ \ "HEAHEA" \ "AccDatHEA158").read[LocalDate],
     (__ \ "HEAHEA" \ "TotNumOfIteHEA305").read[Int],
     (__ \ "HEAHEA" \ "TotNumOfPacHEA306").read[Int],
     (__ \ "HEAHEA" \ "TotGroMasHEA307").read[BigDecimal],
     (__ \ "HEAHEA" \ "AutIdHEA380").read[String].optional,
     (__ \ "TRAPRIPC1").read[Principal],
     (__ \ "TRACONCO1").read[Consignor](Consignor.xmlReaderRootLevel).optional,
     (__ \ "TRACONCE1").read[Consignee](Consignee.xmlReaderRootLevel).optional,
     (__ \ "CUSOFFDEPEPT" \ "RefNumEPT1").read[String],
     (__ \ "CUSOFFTRARNS").read(strictReadSeq[CustomsOfficeTransit]),
     (__ \ "CONRESERS").read[ControlResult].optional,
     (__ \ "SEAINFSLI" \ "SEAIDSID" \ "SeaIdeSID1").read(strictReadSeq[String]),
     (__ \ "GOOITEGDS").read(xmlNonEmptyListReads[GoodsItem])).mapN(apply)
  }
}
