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

package utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateFormatter {
  val dateFormatter: DateTimeFormatter         = DateTimeFormatter.ofPattern("yyyyMMdd")
  val readableDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  def dateFormatted(date: LocalDate): String   = date.format(dateFormatter)

  val dateTimeFormatter: DateTimeFormatter               = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
  def dateTimeFormatted(dateTime: LocalDateTime): String = dateTime.format(dateTimeFormatter)

  val arrivalDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

  def arrivalDateTimeFormatted(dateTime: LocalDateTime): String = dateTime.format(arrivalDateTimeFormatter)

  def dateFormatted(date: LocalDate, pattern: String): String = date.format(DateTimeFormatter.ofPattern(pattern))

  def dateFormatted(dateOption: Option[LocalDate], pattern: String): Option[String] = dateOption.map {
    date =>
      try {
        date.format(DateTimeFormatter.ofPattern(pattern))
      } catch {
        case _ => date.toString
      }
  }
}
