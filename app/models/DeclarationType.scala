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

import com.lucidchart.open.xtract.ParseError
import com.lucidchart.open.xtract.ParseFailure
import com.lucidchart.open.xtract.ParseResult
import com.lucidchart.open.xtract.ParseSuccess
import com.lucidchart.open.xtract.XmlReader

import scala.xml.NodeSeq

sealed trait DeclarationType

object DeclarationType extends Enumerable.Implicits {

  case object TMinus extends WithName("T-") with DeclarationType
  case object T1     extends WithName("T1") with DeclarationType
  case object T2     extends WithName("T2") with DeclarationType
  case object T2F    extends WithName("T2F") with DeclarationType
  case object T2SM   extends WithName("T2SM") with DeclarationType
  case object TIR    extends WithName("TIR") with DeclarationType

  val values: Set[DeclarationType] = Set(TMinus, T1, T2, T2F, T2SM, TIR)

  implicit val enumerable: Enumerable[DeclarationType] =
    Enumerable(values.toSeq.map(v => v.toString -> v): _*)

  implicit lazy val xmlReader: XmlReader[DeclarationType] =
    (xml: NodeSeq) => {

      case class DeclarationTypeParseFailure(message: String) extends ParseError

      values
        .find(_.toString == xml.text)
        .map(ParseSuccess(_))
        .getOrElse(ParseFailure(DeclarationTypeParseFailure(s"Could not parse the following value to DeclarationType: ${xml.text}")))
    }
}
