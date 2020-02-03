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
import play.api.libs.json._
import json.NonEmptyListOps._

sealed trait UnloadingPermission

object UnloadingPermission {

  implicit lazy val reads: Reads[UnloadingPermission] = {

    implicit class ReadsWithContravariantOr[A](a: Reads[A]) {

      def or[B >: A](b: Reads[B]): Reads[B] =
        a.map[B](identity).orElse(b)
    }

    implicit def convertToSupertype[A, B >: A](a: Reads[A]): Reads[B] =
      a.map(identity)

    PermissionToStartUnloading.format or
      PermissionToContinueUnloading.format
  }

  implicit lazy val writes: OWrites[UnloadingPermission] = OWrites {
    case up: PermissionToContinueUnloading => Json.toJsObject(up)(PermissionToContinueUnloading.format)
    case up: PermissionToStartUnloading    => Json.toJsObject(up)(PermissionToStartUnloading.format)
  }
}

final case class PermissionToStartUnloading(
  movementReferenceNumber: String,
  declarationType: DeclarationType,
  transportIdentity: Option[String],
  transportCountry: Option[String],
  acceptanceDate: LocalDate,
  numberOfItems: Int,
  numberOfPackages: Int,
  grossMass: BigDecimal,
  principal: Principal,
  traderAtDestination: TraderAtDestination,
  presentationOffice: String,
  seals: Seq[String],
  goodsItems: NonEmptyList[GoodsItem]
) extends UnloadingPermission

object PermissionToStartUnloading {

  implicit lazy val format: OFormat[PermissionToStartUnloading] =
    Json.format[PermissionToStartUnloading]
}

final case class PermissionToContinueUnloading(
  movementReferenceNumber: String,
  continueUnloading: Int,
  presentationOffice: String,
  traderAtDestination: TraderAtDestination
) extends UnloadingPermission

object PermissionToContinueUnloading {

  implicit lazy val format: OFormat[PermissionToContinueUnloading] =
    Json.format[PermissionToContinueUnloading]
}
