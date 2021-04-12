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

package models

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader
import com.lucidchart.open.xtract.__

final case class SafetyAndSecurityCarrier(
  name: String,
  streetAndNumber: String,
  postCode: String,
  city: String,
  countryCode: String,
  nadLanguageCode: Option[String],
  eori: Option[String]
)

object SafetyAndSecurityCarrier {

  implicit val xmlReader: XmlReader[SafetyAndSecurityCarrier] = (
    (__ \ "NamCARTRA121").read[String],
    (__ \ "StrAndNumCARTRA254").read[String],
    (__ \ "PosCodCARTRA121").read[String],
    (__ \ "CitCARTRA789").read[String],
    (__ \ "CouCodCARTRA587").read[String],
    (__ \ "NADCARTRA121").read[String].optional,
    (__ \ "TINCARTRA254").read[String].optional
  ).mapN(apply)

}
